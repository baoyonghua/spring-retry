/*
 * Copyright 2006-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.annotation;

import org.springframework.classify.SubclassClassifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 <code>@Recover</code> 注解的方法调用恢复器。
 * <p>
 * 一个合适的恢复方法是指第一个参数为 Throwable 类型，并且返回类型和参数与失败的方法相同的方法。
 * Throwable 类型的第一个参数是可选的，如果省略，则该方法被视为默认方法（当没有其他的恢复方法被匹配时才会被调用）。<br>
 * 通常会根据第一个参数的类型和所处理异常的类型选择最匹配的方法。并在类层次结构中选择最近的匹配，例如：
 * <li>如果正在处理 IllegalArgumentException，并且有一个方法的第一个参数是 RuntimeException，则它会优先于第一个参数为 Throwable 的方法</li>
 * </p>
 *
 * @param <T> 恢复器返回值的类型
 * @author Dave Syer
 */
public class RecoverAnnotationRecoveryHandler<T> implements MethodInvocationRecoverer<T> {

    /**
     * 分类器, 用于根据异常类型选择恢复方法
     * key: 恢复方法的第一个参数类型（Throwable类型的子类）
     * value: 恢复方法
     */
    private final SubclassClassifier<Throwable, Method> classifier = new SubclassClassifier<>();

    /**
     * 恢复方法映射
     * key: 恢复方法
     * value: 恢复方法的简单元数据（参数个数和第一个参数类型[Throwable类型的子类]）
     */
    private final Map<Method, SimpleMetadata> methods = new HashMap<>();

    /**
     * 恢复方法所在的目标对象
     */
    private final Object target;

    /**
     * 恢复方法的名称（如果在 @Retryable 注解中指定了 recover 属性则存在此属性）
     */
    private String recoverMethodName;

    /**
     * 构造一个新的 <code>RecoverAnnotationRecoveryHandler</code> 实例。
     *
     * @param target 恢复方法所在的目标对象
     * @param method 目标方法
     */
    public RecoverAnnotationRecoveryHandler(Object target, Method method) {
        this.target = target;

        // 初始化, 会收集该目标类中所有的恢复方法
        init(target, method);
    }

    /**
     * 尝试通过调用一个合适的恢复方法来进行恢复
     *
     * @param args  the arguments for the method invocation that failed.
     * @param cause the cause of the failure that led to this recovery.
     * @return
     */
    @Override
    public T recover(Object[] args, Throwable cause) {
        // 从类中的所有恢复方法中，找到与给定参数和异常类型最匹配的方法
        Method method = findClosestMatch(args, cause.getClass());
        if (method == null) {
            throw new ExhaustedRetryException("Cannot locate recovery method", cause);
        }

        SimpleMetadata meta = this.methods.get(method);
        Object[] argsToUse = meta.getArgs(cause, args);
        ReflectionUtils.makeAccessible(method);
        RetryContext context = RetrySynchronizationManager.getContext();
        Object proxy = null;
        if (context != null) {
            // 尝试从重试上下文中获取代理对象
            proxy = context.getAttribute("___proxy___");
            if (proxy != null) {
                Method proxyMethod = findMethodOnProxy(method, proxy);
                if (proxyMethod == null) {
                    proxy = null;
                } else {
                    method = proxyMethod;
                }
            }
        }
        if (proxy == null) {
            proxy = this.target;
        }

        // 调用恢复方法
        @SuppressWarnings("unchecked")
        T result = (T) ReflectionUtils.invokeMethod(method, proxy, argsToUse);
        return result;
    }

    private Method findMethodOnProxy(Method method, Object proxy) {
        try {
            return proxy.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    /**
     * 从类中的所有恢复方法中，找到与给定参数和异常类型最匹配的方法。
     *
     * @param args  the arguments for the method invocation that failed.
     * @param cause the cause of the failure that led to this recovery.
     * @return
     */
    private Method findClosestMatch(Object[] args, Class<? extends Throwable> cause) {
        Method result = null;

        if (!StringUtils.hasText(this.recoverMethodName)) {
            int min = Integer.MAX_VALUE;
            for (Map.Entry<Method, SimpleMetadata> entry : this.methods.entrySet()) {
                Method method = entry.getKey();
                SimpleMetadata meta = entry.getValue();
                Class<? extends Throwable> type = meta.type();
                if (type == null) {
                    type = Throwable.class;
                }
                if (type.isAssignableFrom(cause)) {
                    int distance = calculateDistance(cause, type);
                    if (distance < min) {
                        min = distance;
                        result = method;
                    } else if (distance == min) {
                        boolean parametersMatch = compareParameters(args, meta.argCount(),
                                method.getParameterTypes(), false);
                        if (parametersMatch) {
                            result = method;
                        }
                    }
                }
            }
        } else {
            for (Map.Entry<Method, SimpleMetadata> entry : this.methods.entrySet()) {
                Method method = entry.getKey();
                if (method.getName().equals(this.recoverMethodName)) {
                    SimpleMetadata meta = entry.getValue();
                    if ((meta.type == null || meta.type.isAssignableFrom(cause))
                            && compareParameters(args, meta.argCount(), method.getParameterTypes(), true)) {
                        result = method;
                        break;
                    }
                }
            }
        }
        return result;
    }

    private int calculateDistance(Class<? extends Throwable> cause, Class<? extends Throwable> type) {
        int result = 0;
        Class<?> current = cause;
        while (current != type && current != Throwable.class) {
            result++;
            current = current.getSuperclass();
        }
        return result;
    }

    private boolean compareParameters(Object[] args, int argCount, Class<?>[] parameterTypes,
                                      boolean withRecoverMethodName) {
        if ((withRecoverMethodName && argCount == args.length) || argCount == (args.length + 1)) {
            int startingIndex = 0;
            if (parameterTypes.length > 0 && Throwable.class.isAssignableFrom(parameterTypes[0])) {
                startingIndex = 1;
            }
            for (int i = startingIndex; i < parameterTypes.length; i++) {
                final Object argument = i - startingIndex < args.length ? args[i - startingIndex] : null;
                if (argument == null) {
                    continue;
                }
                Class<?> parameterType = parameterTypes[i];
                parameterType = ClassUtils.resolvePrimitiveIfNecessary(parameterType);
                if (!parameterType.isAssignableFrom(argument.getClass())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 初始化，收集目标类中的所有恢复方法
     *
     * @param target 恢复方法所在的目标对象
     * @param method 目标方法
     */
    private void init(final Object target, Method method) {
        final Map<Class<? extends Throwable>, Method> types = new HashMap<>();
        final Method failingMethod = method;
        Retryable retryable = AnnotatedElementUtils.findMergedAnnotation(method, Retryable.class);
        if (retryable != null) {
            this.recoverMethodName = retryable.recover();
        }

        ReflectionUtils.doWithMethods(target.getClass(), candidate -> {
            // 查找 @Recover 注解
            Recover recover = AnnotatedElementUtils.findMergedAnnotation(candidate, Recover.class);
            if (recover == null) {
                recover = findAnnotationOnTarget(target, candidate);
            }

            if (recover != null && failingMethod.getGenericReturnType() instanceof ParameterizedType
                    && candidate.getGenericReturnType() instanceof ParameterizedType) {
                // 如果返回类型是参数化类型，则进行更严格的检查
                if (isParameterizedTypeAssignable(
                        (ParameterizedType) candidate.getGenericReturnType(),
                        (ParameterizedType) failingMethod.getGenericReturnType())
                ) {
                    putToMethodsMap(candidate, types);
                }
            } else if (recover != null && candidate.getReturnType().isAssignableFrom(failingMethod.getReturnType())) {
                // 非参数化类型，则检查返回类型即可
                putToMethodsMap(candidate, types);
            }
        });
        this.classifier.setTypeMap(types);  // 初始化分类器

        optionallyFilterMethodsBy(failingMethod.getReturnType());
    }

    /**
     * 如果输入的 methodReturnType 与 failingMethodReturnType 直接匹配，则返回 {@code true}。
     * 在决定是否匹配时，也会考虑嵌套的泛型类型。
     *
     * @param methodReturnType        恢复方法的返回类型
     * @param failingMethodReturnType 失败方法的返回类型
     * @return true if the parameterized return types match.
     * @since 1.3.2
     */
    private static boolean isParameterizedTypeAssignable(ParameterizedType methodReturnType,
                                                         ParameterizedType failingMethodReturnType) {

        Type[] methodActualArgs = methodReturnType.getActualTypeArguments();
        Type[] failingMethodActualArgs = failingMethodReturnType.getActualTypeArguments();
        if (methodActualArgs.length != failingMethodActualArgs.length) {
            return false;
        }
        int startingIndex = 0;
        for (int i = startingIndex; i < methodActualArgs.length; i++) {
            Type methodArgType = methodActualArgs[i];
            Type failingMethodArgType = failingMethodActualArgs[i];
            if (methodArgType instanceof ParameterizedType && failingMethodArgType instanceof ParameterizedType) {
                if (!isParameterizedTypeAssignable((ParameterizedType) methodArgType,
                        (ParameterizedType) failingMethodArgType)) {

                    return false;
                }
            } else if (methodArgType instanceof Class && failingMethodArgType instanceof Class) {
                if (!failingMethodArgType.equals(methodArgType)) {
                    return false;
                }
            } else if (!methodArgType.equals(failingMethodArgType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将恢复方法添加到方法映射中
     *
     * @param method 恢复方法
     * @param types  恢复方法类型映射
     */
    private void putToMethodsMap(Method method, Map<Class<? extends Throwable>, Method> types) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length > 0 && Throwable.class.isAssignableFrom(parameterTypes[0])) {
            // 如果恢复方法的第一个参数是 Throwable 的子类，则将其作为类型进行映射
            @SuppressWarnings("unchecked")
            Class<? extends Throwable> type = (Class<? extends Throwable>) parameterTypes[0];
            RecoverAnnotationRecoveryHandler.this.methods.put(method, new SimpleMetadata(parameterTypes.length, type));
            types.put(type, method);
        } else {
            // 如果恢复方法不是 Throwable 类型的参数，则将其作为默认的恢复方法, 当没有其他恢复方法匹配时会调用它
            RecoverAnnotationRecoveryHandler.this.classifier.setDefaultValue(method);
            RecoverAnnotationRecoveryHandler.this.methods.put(method, new SimpleMetadata(parameterTypes.length, null));
        }
    }

    /**
     *
     * @param target
     * @param method
     * @return
     */
    private Recover findAnnotationOnTarget(Object target, Method method) {
        try {
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            return AnnotatedElementUtils.findMergedAnnotation(targetMethod, Recover.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     *
     * @param returnClass 失败方法的返回类型
     */
    private void optionallyFilterMethodsBy(Class<?> returnClass) {
        Map<Method, SimpleMetadata> filteredMethods = new HashMap<>();
        for (Method method : this.methods.keySet()) {
            // 过滤掉返回类型与失败方法不匹配的恢复方法
            if (method.getReturnType() == returnClass) {
                filteredMethods.put(method, this.methods.get(method));
            }
        }
        if (!filteredMethods.isEmpty()) {
            this.methods.clear();
            this.methods.putAll(filteredMethods);
        }
    }

    /**
     * 简单的元数据记录
     *
     * @param argCount 恢复方法的参数个数
     * @param type     恢复方法的第一个参数类型（如果存在则为 Throwable 的子类）
     */
    private record SimpleMetadata(int argCount, Class<? extends Throwable> type) {

        public Object[] getArgs(Throwable t, Object[] args) {
            Object[] result = new Object[argCount()];
            int startArgs = 0;
            if (this.type != null) {
                result[0] = t;
                startArgs = 1;
            }
            int length = Math.min(result.length - startArgs, args.length);
            if (length == 0) {
                return result;
            }
            System.arraycopy(args, 0, result, startArgs, length);
            return result;
        }

    }

}
