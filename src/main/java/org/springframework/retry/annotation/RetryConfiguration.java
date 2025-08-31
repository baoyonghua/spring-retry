/*
 * Copyright 2006-2024 the original author or authors.
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

import org.aopalliance.aop.Advice;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.OrderComparator;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.MethodArgumentsKeyGenerator;
import org.springframework.retry.interceptor.NewMethodArgumentsIdentifier;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>@Retryable</code> 处理的基本配置。
 * <p>
 * 对于有状态重试，如果在上下文中存在唯一类型为 {@link RetryContextCache}、{@link MethodArgumentsKeyGenerator}
 * 或 {@link NewMethodArgumentsIdentifier} 的 bean，则对应的重试拦截器会使用它（否则将采用合理的默认值）。
 * </p>
 *
 * @author Dave Syer
 * @since 1.1
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class RetryConfiguration extends AbstractPointcutAdvisor
        implements IntroductionAdvisor, BeanFactoryAware, InitializingBean, SmartInitializingSingleton, ImportAware {

    /**
     * EnableRetry 注解的属性集，如果没有启用则为 null
     */
    @Nullable
    protected AnnotationAttributes enableRetry;

    /**
     * Advice, 用于解析方法上的重试元数据，并委托给合适的RetryOperationsInterceptor
     */
    private AnnotationAwareRetryOperationsInterceptor advice;

    /**
     * Pointcut, 用于识别标注了 @Retryable 的方法
     */
    private Pointcut pointcut;

    /**
     * RetryContext 缓存实例（可选，如果有状态重试需要）
     */
    private RetryContextCache retryContextCache;

    /**
     * 重试监听器列表（可选）
     */
    private List<RetryListener> retryListeners;

    /**
     * 方法参数键生成器实例（可选，如果有状态重试需要）
     */
    private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

    /**
     * 新方法参数标识符实例（可选，如果有状态重试需要）
     */
    private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

    /**
     * Sleeper 实例（可选，如果使用了退避策略）
     */
    private Sleeper sleeper;

    /**
     * BeanFactory
     */
    private BeanFactory beanFactory;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        Map<String, Object> annotationAttributes = importMetadata.getAnnotationAttributes(EnableRetry.class.getName());
        this.enableRetry = AnnotationAttributes.fromMap(annotationAttributes);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // 在 BeanFactory 中查找相关的 bean 实例
        this.retryContextCache = findBean(RetryContextCache.class);
        this.methodArgumentsKeyGenerator = findBean(MethodArgumentsKeyGenerator.class);
        this.newMethodArgumentsIdentifier = findBean(NewMethodArgumentsIdentifier.class);
        this.sleeper = findBean(Sleeper.class);

        // 构建 Pointcut 和 Advice
        Set<Class<? extends Annotation>> retryableAnnotationTypes = new LinkedHashSet<>(1);
        retryableAnnotationTypes.add(Retryable.class);
        this.pointcut = buildPointcut(retryableAnnotationTypes);  // Pointcut: 在类或方法上标注了 @Retryable 的地方都匹配
        this.advice = buildAdvice();
        this.advice.setBeanFactory(this.beanFactory);

        // 设置 Advisor 的执行顺序
        if (this.enableRetry != null) {
            setOrder(enableRetry.getNumber("order"));
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 在 BeanFactory 中查找所有的 RetryListener 实例，并按顺序设置到 Advice 中
        this.retryListeners = findBeans(RetryListener.class);
        if (this.retryListeners != null) {
            this.advice.setListeners(this.retryListeners);
        }
    }


    /**
     * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public ClassFilter getClassFilter() {
        return this.pointcut.getClassFilter();
    }

    @Override
    public Class<?>[] getInterfaces() {
        return new Class[]{org.springframework.retry.interceptor.Retryable.class};
    }

    @Override
    public void validateInterfaces() throws IllegalArgumentException {
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }

    protected AnnotationAwareRetryOperationsInterceptor buildAdvice() {
        AnnotationAwareRetryOperationsInterceptor interceptor = new AnnotationAwareRetryOperationsInterceptor();
        if (this.retryContextCache != null) {
            interceptor.setRetryContextCache(this.retryContextCache);
        }
        if (this.methodArgumentsKeyGenerator != null) {
            interceptor.setKeyGenerator(this.methodArgumentsKeyGenerator);
        }
        if (this.newMethodArgumentsIdentifier != null) {
            interceptor.setNewItemIdentifier(this.newMethodArgumentsIdentifier);
        }
        if (this.sleeper != null) {
            interceptor.setSleeper(this.sleeper);
        }
        return interceptor;
    }

    /**
     * 为给定的重试注解类型计算一个 Pointcut（切点）。
     *
     * @param retryAnnotationTypes 需要检测的重试注解类型集合
     * @return 匹配的 Pointcut 对象，如果没有则为 {@code null}
     */
    protected Pointcut buildPointcut(Set<Class<? extends Annotation>> retryAnnotationTypes) {
        ComposablePointcut result = null;
        // 为每个重试注解类型创建一个 AnnotationClassOrMethodPointcut，并将它们组合成一个 ComposablePointcut
        // 这样，任何标注了任意注解的类或方法都会被匹配
        for (Class<? extends Annotation> retryAnnotationType : retryAnnotationTypes) {
            Pointcut filter = new AnnotationClassOrMethodPointcut(retryAnnotationType);
            if (result == null) {
                result = new ComposablePointcut(filter);
            } else {
                result.union(filter);
            }
        }
        return result;
    }

    private <T> List<T> findBeans(Class<? extends T> type) {
        if (this.beanFactory instanceof ListableBeanFactory listable) {
            if (listable.getBeanNamesForType(type).length > 0) {
                ArrayList<T> list = new ArrayList<>(listable.getBeansOfType(type, false, false).values());
                OrderComparator.sort(list);
                return list;
            }
        }
        return null;
    }

    private <T> T findBean(Class<? extends T> type) {
        if (this.beanFactory instanceof ListableBeanFactory listable) {
            if (listable.getBeanNamesForType(type, false, false).length == 1) {
                return listable.getBean(type);
            }
        }
        return null;
    }

    /**
     * 基于注解的切入点，用于匹配类或其方法上标注了指定注解的类或方法。
     * <li>如果类上标注了指定注解，或者类中存在至少一个方法标注了该注解，则类中的所有方法都匹配成功。</li>
     * <li>如果类上没有标注指定注解，但类中存在标注了该注解的方法，则只有这些方法匹配成功。</li>
     */
    private static final class AnnotationClassOrMethodPointcut extends StaticMethodMatcherPointcut {

        /**
         * 方法匹配器，用于检查方法上是否标注了指定注解
         */
        private final MethodMatcher methodResolver;

        AnnotationClassOrMethodPointcut(Class<? extends Annotation> annotationType) {
            this.methodResolver = new AnnotationMethodMatcher(annotationType);

            // 为Pointcut设置类过滤器
            setClassFilter(new AnnotationClassOrMethodFilter(annotationType));
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            // 如果类上标注了指定注解，则类中的所有方法都匹配成功
            return getClassFilter().matches(targetClass) ||
                    // 否则检查方法上是否标注了指定注解
                    this.methodResolver.matches(method, targetClass);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AnnotationClassOrMethodPointcut otherAdvisor)) {
                return false;
            }
            return ObjectUtils.nullSafeEquals(this.methodResolver, otherAdvisor.methodResolver);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.methodResolver);
        }
    }

    /**
     * 基于注解的类过滤器，用于匹配类或其方法上标注了指定注解的类。
     * <p>
     * 如果类上标注了指定注解，或者类中存在至少一个方法标注了该注解，则匹配成功。
     * </p>
     */
    private static final class AnnotationClassOrMethodFilter extends AnnotationClassFilter {

        private final AnnotationMethodsResolver methodResolver;

        AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
            super(annotationType, true);
            this.methodResolver = new AnnotationMethodsResolver(annotationType);
        }

        @Override
        public boolean matches(Class<?> clazz) {
            return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
        }
    }

    /**
     * 用于检查类中是否存在标注了指定注解的方法的解析器
     */
    private record AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {

        public boolean hasAnnotatedMethods(Class<?> clazz) {
            final AtomicBoolean found = new AtomicBoolean(false);
            ReflectionUtils.doWithMethods(clazz, method -> {
                if (found.get()) {
                    return;
                }
                Annotation annotation = AnnotationUtils.findAnnotation(method,
                        AnnotationMethodsResolver.this.annotationType);
                if (annotation != null) {
                    found.set(true);
                }
            });
            return found.get();
        }

    }

}
