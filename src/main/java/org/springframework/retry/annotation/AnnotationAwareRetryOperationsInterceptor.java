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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.*;
import org.springframework.retry.policy.*;
import org.springframework.retry.support.Args;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.naming.OperationNotSupportedException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 拦截器，解析其调用方法上的重试元数据，并委托给合适的{@link RetryOperationsInterceptor}
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @author Roman Akentev
 * @author Aftab Shaikh
 * @since 1.1
 */
public class AnnotationAwareRetryOperationsInterceptor implements IntroductionInterceptor, BeanFactoryAware {

    /**
     * SpEL模板解析上下文
     * <p>
     * 用于解析重试注解中的SpEL模板表达式 {@code #{...}}
     * </p>
     */
    private static final TemplateParserContext PARSER_CONTEXT = new TemplateParserContext();

    /**
     * SpEL表达式解析器
     * <p>
     * 用于解析重试注解中的SpEL表达式，例如{@code maxAttemptsExpression}, {@code delayExpression}等
     * </p>
     */
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 空拦截器，表示没有找到合适的重试配置
     */
    private static final MethodInterceptor NULL_INTERCEPTOR = methodInvocation -> {
        throw new OperationNotSupportedException("Not supported");
    };

    /**
     * SpEL表达式评估上下文
     * <p>
     * 评估上下文中配置了BeanFactoryResolver, 使得SpEL表达式可以通过{@code @beanName}的方式引用Spring Bean
     * </p>
     */
    private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

    /**
     * 缓存目标对象及其方法对应的重试拦截器
     * key: 目标对象
     * value: 目标对象的方法与其对应的重试拦截器的映射
     */
    private final ConcurrentReferenceHashMap<Object, ConcurrentMap<Method, MethodInterceptor>> delegates = new ConcurrentReferenceHashMap<>();

    /**
     * 重试上下文缓存, 用于有状态重试
     */
    private RetryContextCache retryContextCache = new MapRetryContextCache();

    /**
     * 方法参数键生成器, 用于有状态重试
     */
    private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

    /**
     * 新方法参数标识符, 用于有状态重试
     */
    private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

    /**
     * 睡眠器, 用于退避策略
     */
    private Sleeper sleeper;

    /**
     * Spring Bean工厂
     */
    private BeanFactory beanFactory;

    /**
     * 全局重试监听器, 应用于所有操作
     */
    private RetryListener[] globalListeners;

    /**
     * @param sleeper the sleeper to set
     */
    public void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * Public setter for the {@link RetryContextCache}.
     *
     * @param retryContextCache the {@link RetryContextCache} to set.
     */
    public void setRetryContextCache(RetryContextCache retryContextCache) {
        this.retryContextCache = retryContextCache;
    }

    /**
     * @param methodArgumentsKeyGenerator the {@link MethodArgumentsKeyGenerator}
     */
    public void setKeyGenerator(MethodArgumentsKeyGenerator methodArgumentsKeyGenerator) {
        this.methodArgumentsKeyGenerator = methodArgumentsKeyGenerator;
    }

    /**
     * @param newMethodArgumentsIdentifier the {@link NewMethodArgumentsIdentifier}
     */
    public void setNewItemIdentifier(NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
        this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
    }

    /**
     * Default retry listeners to apply to all operations.
     *
     * @param globalListeners the default listeners
     */
    public void setListeners(Collection<RetryListener> globalListeners) {
        ArrayList<RetryListener> retryListeners = new ArrayList<>(globalListeners);
        // 排序重试监听器, 按照其优先级顺序 -> @Order/@Priority注解或实现Ordered接口
        AnnotationAwareOrderComparator.sort(retryListeners);
        this.globalListeners = retryListeners.toArray(new RetryListener[0]);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        this.evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
    }

    @Override
    public boolean implementsInterface(Class<?> intf) {
        return org.springframework.retry.interceptor.Retryable.class.isAssignableFrom(intf);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 根据目标对象及其方法获取对应的重试拦截器
        MethodInterceptor delegate = getDelegate(invocation.getThis(), invocation.getMethod());
        if (delegate != null) {
            // 如果找到了合适的重试配置, 则委托给对应的重试拦截器进行处理
            return delegate.invoke(invocation);
        } else {
            // 如果没有找到合适的重试配置, 则直接调用目标方法
            return invocation.proceed();
        }
    }

    /**
     * 根据目标对象及其方法获取合适的重试拦截器
     *
     * @param target 目标对象
     * @param method 目标方法
     * @return
     */
    private MethodInterceptor getDelegate(Object target, Method method) {
        ConcurrentMap<Method, MethodInterceptor> cachedMethods = this.delegates.get(target);
        if (cachedMethods == null) {
            cachedMethods = new ConcurrentHashMap<>();
        }
        MethodInterceptor delegate = cachedMethods.get(method);
        if (delegate == null) {
            MethodInterceptor interceptor = NULL_INTERCEPTOR;
            // 尝试在方法上查找@Retryable注解
            Retryable retryable = AnnotatedElementUtils.findMergedAnnotation(method, Retryable.class);
            if (retryable == null) {
                // 如果方法上没有找到@Retryable注解, 则尝试在方法所在的类上查找@Retryable注解
                retryable = classLevelAnnotation(method, Retryable.class);
            }
            if (retryable == null) {
                // 如果方法所在的类上也没有找到@Retryable注解, 则尝试在目标对象及其方法上查找@Retryable注解
                retryable = findAnnotationOnTarget(target, method, Retryable.class);
            }

            if (retryable != null) {
                if (StringUtils.hasText(retryable.interceptor())) {
                    // 如果@Retryable注解中指定了拦截器Bean名称, 则从BeanFactory中获取该拦截器
                    interceptor = this.beanFactory.getBean(retryable.interceptor(), MethodInterceptor.class);
                }
                // 否则的话, 根据@Retryable注解的配置创建合适的重试拦截器 -> 有状态/无状态
                else if (retryable.stateful()) {
                    interceptor = getStatefulInterceptor(target, method, retryable);
                } else {
                    interceptor = getStatelessInterceptor(target, method, retryable);
                }
            }
            cachedMethods.putIfAbsent(method, interceptor);
            delegate = cachedMethods.get(method);
        }
        this.delegates.putIfAbsent(target, cachedMethods);
        return delegate == NULL_INTERCEPTOR ? null : delegate;
    }

    /**
     * 查找目标对象及其方法上的指定注解
     *
     * @param target
     * @param method
     * @param annotation
     * @param <A>
     * @return
     */
    private <A extends Annotation> A findAnnotationOnTarget(Object target, Method method, Class<A> annotation) {

        try {
            // 尝试在目标对象的方法上查找指定注解
            Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
            A retryable = AnnotatedElementUtils.findMergedAnnotation(targetMethod, annotation);
            if (retryable == null) {
                // 如果目标对象的方法上没有找到, 则尝试在目标对象的类上查找指定注解
                retryable = classLevelAnnotation(targetMethod, annotation);
            }
            return retryable;
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * With a class level annotation, exclude @Recover methods.
     */
    private <A extends Annotation> A classLevelAnnotation(Method method, Class<A> annotation) {
        A ann = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotation);
        if (ann != null && AnnotatedElementUtils.findMergedAnnotation(method, Recover.class) != null) {
            ann = null;
        }
        return ann;
    }

    /**
     * 创建无状态的重试拦截器
     *
     * @param target
     * @param method
     * @param retryable
     * @return
     */
    private MethodInterceptor getStatelessInterceptor(Object target, Method method, Retryable retryable) {
        RetryTemplate template = createTemplate(retryable.listeners());
        template.setRetryPolicy(getRetryPolicy(retryable, true));
        template.setBackOffPolicy(getBackoffPolicy(retryable.backoff(), true));
        return RetryInterceptorBuilder.stateless()
                .retryOperations(template)
                .label(retryable.label())
                .recoverer(getRecoverer(target, method))
                .build();
    }

    /**
     * 创建有状态的重试拦截器
     *
     * @param target
     * @param method
     * @param retryable
     * @return
     */
    private MethodInterceptor getStatefulInterceptor(Object target, Method method, Retryable retryable) {
        RetryTemplate template = createTemplate(retryable.listeners());
        template.setRetryContextCache(this.retryContextCache);

        // 尝试在方法上查找@CircuitBreaker注解 -> 如果找到了则创建熔断器重试拦截器
        // @CircuitBreaker注解是有状态的重试注解, 其内部包含了@Retryable注解
        CircuitBreaker circuit = AnnotatedElementUtils.findMergedAnnotation(method, CircuitBreaker.class);
        if (circuit == null) {
            circuit = findAnnotationOnTarget(target, method, CircuitBreaker.class);
        }
        if (circuit != null) {
            RetryPolicy policy = getRetryPolicy(circuit, false);
            CircuitBreakerRetryPolicy breaker = new CircuitBreakerRetryPolicy(policy);
            openTimeout(breaker, circuit);
            resetTimeout(breaker, circuit);
            template.setRetryPolicy(breaker);
            template.setBackOffPolicy(new NoBackOffPolicy());
            template.setThrowLastExceptionOnExhausted(circuit.throwLastExceptionOnExhausted());
            String label = circuit.label();
            if (!StringUtils.hasText(label)) {
                label = method.toGenericString();
            }
            return RetryInterceptorBuilder.circuitBreaker()
                    .keyGenerator(new FixedKeyGenerator("circuit"))
                    .retryOperations(template)
                    .recoverer(getRecoverer(target, method))
                    .label(label)
                    .build();
        }


        RetryPolicy policy = getRetryPolicy(retryable, false);
        template.setRetryPolicy(policy);
        template.setBackOffPolicy(getBackoffPolicy(retryable.backoff(), false));
        String label = retryable.label();
        return RetryInterceptorBuilder.stateful()
                .keyGenerator(this.methodArgumentsKeyGenerator)
                .newMethodArgumentsIdentifier(this.newMethodArgumentsIdentifier)
                .retryOperations(template)
                .label(label)
                .recoverer(getRecoverer(target, method))
                .build();
    }

    /**
     * 配置熔断器的超时时间
     * <p>
     * 当在此超时时间内达到 {@link CircuitBreaker#maxAttempts()} 次失败时，断路器会自动打开，后续的重试操作将被拒绝, 以阻止对下游组件的访问。
     * </p>
     *
     * @param breaker
     * @param circuit
     */
    private void openTimeout(CircuitBreakerRetryPolicy breaker, CircuitBreaker circuit) {
        String expression = circuit.openTimeoutExpression();
        if (StringUtils.hasText(expression)) {
            // 解析Spel表达式
            Expression parsed = parse(expression);
            if (isTemplate(expression)) {
                // 是#{...}模板表达式, 则在初始化时进行一次性求值
                Long value = parsed.getValue(this.evaluationContext, Long.class);
                if (value != null) {
                    breaker.setOpenTimeout(value);
                    return;
                }
            } else {
                // 不是#{...}模板表达式, 则在运行时进行求值
                breaker.openTimeoutSupplier(() -> evaluate(parsed, Long.class, false));
                return;
            }
        }
        breaker.setOpenTimeout(circuit.openTimeout());
    }

    /**
     * 配置熔断器的重置超时时间
     * <p>
     * 如果断路器保持打开状态超过此超时时间，则会在下一次调用时重置，以便下游组件有机会再次响应。
     * </p>
     *
     * @param breaker
     * @param circuit
     */
    private void resetTimeout(CircuitBreakerRetryPolicy breaker, CircuitBreaker circuit) {
        String expression = circuit.resetTimeoutExpression();
        if (StringUtils.hasText(expression)) {
            // 解析Spel表达式
            Expression parsed = parse(expression);
            if (isTemplate(expression)) {
                // 是#{...}模板表达式, 则在初始化时进行一次性求值
                Long value = parsed.getValue(this.evaluationContext, Long.class);
                if (value != null) {
                    breaker.setResetTimeout(value);
                    return;
                }
            } else {
                // 不是#{...}模板表达式, 则在运行时进行求值
                breaker.resetTimeoutSupplier(() -> evaluate(parsed, Long.class, false));
            }
        }
        breaker.setResetTimeout(circuit.resetTimeout());
    }

    /**
     * 创建RetryTemplate
     *
     * @param listenersBeanNames
     * @return
     */
    private RetryTemplate createTemplate(String[] listenersBeanNames) {
        RetryTemplate template = new RetryTemplate();
        if (listenersBeanNames.length > 0) {
            template.setListeners(getListenersBeans(listenersBeanNames));
        } else if (this.globalListeners != null) {
            template.setListeners(this.globalListeners);
        }
        return template;
    }

    private RetryListener[] getListenersBeans(String[] listenersBeanNames) {
        if (listenersBeanNames.length == 1 && listenersBeanNames[0].trim().isEmpty()) {
            return new RetryListener[0];
        }
        RetryListener[] listeners = new RetryListener[listenersBeanNames.length];
        for (int i = 0; i < listeners.length; i++) {
            listeners[i] = this.beanFactory.getBean(listenersBeanNames[i], RetryListener.class);
        }
        return listeners;
    }

    /**
     * 获取恢复器, 用于在重试耗尽后进行恢复操作
     *
     * @param target 目标对象
     * @param method 目标方法
     * @return
     */
    private MethodInvocationRecoverer<?> getRecoverer(Object target, Method method) {
        // 如果目标对象实现了MethodInvocationRecoverer接口, 则直接返回该对象作为恢复器
        if (target instanceof MethodInvocationRecoverer) {
            return (MethodInvocationRecoverer<?>) target;
        }

        // 尝试目标类中的方法上查找@Recover注解
        final AtomicBoolean foundRecoverable = new AtomicBoolean(false);
        ReflectionUtils.doWithMethods(target.getClass(), candidate -> {
            if (AnnotatedElementUtils.findMergedAnnotation(candidate, Recover.class) != null) {
                foundRecoverable.set(true);
            }
        });

        if (!foundRecoverable.get()) {
            return null;
        }
        return new RecoverAnnotationRecoveryHandler<>(target, method);
    }

    /**
     * 根据@Retryable注解的配置创建合适的重试策略
     *
     * @param retryable
     * @param stateless
     * @return
     */
    private RetryPolicy getRetryPolicy(Annotation retryable, boolean stateless) {
        Map<String, Object> attrs = AnnotationUtils.getAnnotationAttributes(retryable);
        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] includes = (Class<? extends Throwable>[]) attrs.get("value");
        String exceptionExpression = (String) attrs.get("exceptionExpression");
        boolean hasExceptionExpression = StringUtils.hasText(exceptionExpression);
        if (includes.length == 0) {
            @SuppressWarnings("unchecked")
            Class<? extends Throwable>[] value = (Class<? extends Throwable>[]) attrs.get("retryFor");
            includes = value;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] excludes = (Class<? extends Throwable>[]) attrs.get("noRetryFor");
        Integer maxAttempts = (Integer) attrs.get("maxAttempts");
        String maxAttemptsExpression = (String) attrs.get("maxAttemptsExpression");
        Expression parsedExpression = null;
        if (StringUtils.hasText(maxAttemptsExpression)) {
            parsedExpression = parse(maxAttemptsExpression);
            if (isTemplate(maxAttemptsExpression)) {
                maxAttempts = parsedExpression.getValue(this.evaluationContext, Integer.class);
                parsedExpression = null;
            }
        }
        final Expression maxAttExpression = parsedExpression;
        SimpleRetryPolicy simple = null;
        if (includes.length == 0 && excludes.length == 0) {
            simple = hasExceptionExpression
                    ? new ExpressionRetryPolicy(resolve(exceptionExpression)).withBeanFactory(this.beanFactory)
                    : new SimpleRetryPolicy();
            if (maxAttExpression != null) {
                simple.maxAttemptsSupplier(() -> evaluate(maxAttExpression, Integer.class, stateless));
            } else {
                simple.setMaxAttempts(maxAttempts);
            }
        }
        Map<Class<? extends Throwable>, Boolean> policyMap = new HashMap<>();
        for (Class<? extends Throwable> type : includes) {
            policyMap.put(type, true);
        }
        for (Class<? extends Throwable> type : excludes) {
            policyMap.put(type, false);
        }
        boolean retryNotExcluded = includes.length == 0;
        if (simple == null) {
            if (hasExceptionExpression) {
                simple = new ExpressionRetryPolicy(maxAttempts, policyMap, true, resolve(exceptionExpression),
                        retryNotExcluded)
                        .withBeanFactory(this.beanFactory);
            } else {
                simple = new SimpleRetryPolicy(maxAttempts, policyMap, true, retryNotExcluded);
            }
            if (maxAttExpression != null) {
                simple.maxAttemptsSupplier(() -> evaluate(maxAttExpression, Integer.class, stateless));
            }
        }
        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] noRecovery = (Class<? extends Throwable>[]) attrs.get("notRecoverable");
        if (noRecovery != null && noRecovery.length > 0) {
            simple.setNotRecoverable(noRecovery);
        }
        return simple;
    }

    /**
     * 根据@Backoff注解的配置创建合适的退避策略
     *
     * @param backoff
     * @param stateless
     * @return
     */
    private BackOffPolicy getBackoffPolicy(Backoff backoff, boolean stateless) {
        Map<String, Object> attrs = AnnotationUtils.getAnnotationAttributes(backoff);
        long min = backoff.delay() == 0 ? backoff.value() : backoff.delay();
        String delayExpression = (String) attrs.get("delayExpression");
        Expression parsedMinExp = null;
        if (StringUtils.hasText(delayExpression)) {
            parsedMinExp = parse(delayExpression);
            if (isTemplate(delayExpression)) {
                min = parsedMinExp.getValue(this.evaluationContext, Long.class);
                parsedMinExp = null;
            }
        }
        long max = backoff.maxDelay();
        String maxDelayExpression = (String) attrs.get("maxDelayExpression");
        Expression parsedMaxExp = null;
        if (StringUtils.hasText(maxDelayExpression)) {
            parsedMaxExp = parse(maxDelayExpression);
            if (isTemplate(maxDelayExpression)) {
                max = parsedMaxExp.getValue(this.evaluationContext, Long.class);
                parsedMaxExp = null;
            }
        }
        double multiplier = backoff.multiplier();
        String multiplierExpression = (String) attrs.get("multiplierExpression");
        Expression parsedMultExp = null;
        if (StringUtils.hasText(multiplierExpression)) {
            parsedMultExp = parse(multiplierExpression);
            if (isTemplate(multiplierExpression)) {
                multiplier = parsedMultExp.getValue(this.evaluationContext, Double.class);
                parsedMultExp = null;
            }
        }
        boolean isRandom = false;
        String randomExpression = (String) attrs.get("randomExpression");
        Expression parsedRandomExp = null;

        if (multiplier > 0 || parsedMultExp != null) {
            isRandom = backoff.random();
            if (StringUtils.hasText(randomExpression)) {
                parsedRandomExp = parse(randomExpression);
                if (isTemplate(randomExpression)) {
                    isRandom = parsedRandomExp.getValue(this.evaluationContext, Boolean.class);
                    parsedRandomExp = null;
                }
            }
        }
        return buildBackOff(min, parsedMinExp, max, parsedMaxExp, multiplier, parsedMultExp, isRandom, parsedRandomExp,
                stateless);
    }

    private BackOffPolicy buildBackOff(long min, Expression minExp, long max, Expression maxExp, double multiplier,
                                       Expression multExp, boolean isRandom, Expression randomExp, boolean stateless) {

        BackOffPolicyBuilder builder = BackOffPolicyBuilder.newBuilder();
        if (minExp != null) {
            builder.delaySupplier(() -> evaluate(minExp, Long.class, stateless));
        } else {
            builder.delay(min);
        }
        if (maxExp != null) {
            builder.maxDelaySupplier(() -> evaluate(maxExp, Long.class, stateless));
        } else {
            builder.maxDelay(max);
        }
        if (multExp != null) {
            builder.multiplierSupplier(() -> evaluate(multExp, Double.class, stateless));
        } else {
            builder.multiplier(multiplier);
        }
        if (randomExp != null) {
            builder.randomSupplier(() -> evaluate(randomExp, Boolean.class, stateless));
        } else {
            builder.random(isRandom);
        }
        builder.sleeper(this.sleeper);
        return builder.build();
    }

    /**
     * 解析给定的SpEL表达式
     *
     * @param expression
     * @return
     */
    private Expression parse(String expression) {
        String resolvedExpression = resolve(expression);
        if (isTemplate(expression)) {
            // 是#{...}模板表达式, 则作为模板进行解析
            return PARSER.parseExpression(resolvedExpression, PARSER_CONTEXT);
        } else {
            // 不是#{...}模板表达式, 则直接作为SpEL表达式进行解析
            return PARSER.parseExpression(resolvedExpression);
        }
    }


    private boolean isTemplate(String expression) {
        return expression.contains(PARSER_CONTEXT.getExpressionPrefix())
                && expression.contains(PARSER_CONTEXT.getExpressionSuffix());
    }

    /**
     * 评估给定的SpEL表达式, 并返回指定类型的结果
     *
     * @param expression
     * @param type
     * @param stateless
     * @param <T>
     * @return
     */
    private <T> T evaluate(Expression expression, Class<T> type, boolean stateless) {
        Args args = null;
        if (stateless) {
            RetryContext context = RetrySynchronizationManager.getContext();
            if (context != null) {
                args = (Args) context.getAttribute("ARGS");
            }
            if (args == null) {
                args = Args.NO_ARGS;
            }
        }
        return expression.getValue(this.evaluationContext, args, type);
    }

    /**
     * Resolve the specified value if possible.
     *
     * @see ConfigurableBeanFactory#resolveEmbeddedValue
     */
    private String resolve(String value) {
        if (this.beanFactory != null && this.beanFactory instanceof ConfigurableBeanFactory) {
            // 尝试解析${...}占位符表达式, 如果不是占位符表达式则会原样返回
            return ((ConfigurableBeanFactory) this.beanFactory).resolveEmbeddedValue(value);
        }
        return value;
    }

}
