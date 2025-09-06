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

package org.springframework.retry.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.*;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.util.Assert;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用于执行带有`重试语义操作`的模板类。
 * <p>
 * 用户的可重试的操作可以被封装在 {@link org.springframework.retry.RetryCallback} 接口的实现中，然后通过 RetryTemplate 提供的 execute 方法来执行，
 * 当操作失败时会根据配置的重试策略进行重试。
 * <p>
 * 默认情况下，如果操作抛出任何 {@link java.lang.Exception} 或其子类，则会进行重试。此行为可通过
 * {@link #setRetryPolicy(RetryPolicy)} 方法进行更改。
 * <p>
 * 同样默认情况下，每个操作最多重试三次且中间没有任何退避。此行为可通过
 * {@link #setRetryPolicy(RetryPolicy)} 和 {@link #setBackOffPolicy(BackOffPolicy)}
 * 属性进行配置。
 * <li>{@link org.springframework.retry.RetryPolicy} 用于控制当前是否允许重试</li>
 * <li>{@link org.springframework.retry.backoff.BackOffPolicy} 控制每次重试之间的暂停时长。</li>
 *
 * <p>
 * 用户可以通过调用 {@link #builder} 来以流式 API 配置新的RetryTemplate实例，例如：<pre> {@code
 * RetryTemplate.builder()
 *                 .maxAttempts(10)
 *                 .fixedBackoff(1000)
 *                 .build();
 * }</pre>
 * 更多示例和细节请参见 {@link org.springframework.retry.support.RetryTemplateBuilder}
 * <p>
 * 此类是线程安全的，适用于并发访问，无论是执行操作还是进行配置更改都是线程安全的。
 * 因此用户可以动态更改重试次数以及重试操作所使用的{@link BackOffPolicy}，且不会影响正在进行的重试操作。
 */
public class RetryTemplate implements RetryOperations {

    /**
     * 指示上下文应被视为全局状态（永不关闭）的重试上下文属性名。
     * TODO: 将此转换为RetryState中的标志
     */
    private static final String GLOBAL_STATE = "state.global";

    protected Log logger = LogFactory.getLog(getClass());

    /**
     * 退避策略，默认为 {@link NoBackOffPolicy}，即没有退避。
     */
    private volatile BackOffPolicy backOffPolicy = new NoBackOffPolicy();

    /**
     * 重试策略，默认为 {@link SimpleRetryPolicy}，即最多重试3次，且对所有异常进行重试。
     */
    private volatile RetryPolicy retryPolicy = new SimpleRetryPolicy(3);

    /**
     * 重试监听器
     */
    private volatile RetryListener[] listeners = new RetryListener[0];

    /**
     * 重试上下文缓存，默认为 {@link MapRetryContextCache}。
     * <li>key: {@link RetryState#getKey()}</li>
     * <li>value: {@link RetryContext}</li>
     */
    private RetryContextCache retryContextCache = new MapRetryContextCache();

    /**
     * 是否在所有重试尝试耗尽时抛出最后一个异常或将其包装到 {@link ExhaustedRetryException} 中。默认为 {@code false}；
     * 仅在有状态重试的情况下应用，例如 {@link StatefulRetryOperationsInterceptor}。
     */
    private boolean throwLastExceptionOnExhausted;

    /**
     * Main entry point to configure RetryTemplate using fluent API. See
     * {@link RetryTemplateBuilder} for usage examples and details.
     *
     * @return a new instance of RetryTemplateBuilder with preset default behaviour, that
     * can be overwritten during manual configuration
     * @since 1.3
     */
    public static RetryTemplateBuilder builder() {
        return new RetryTemplateBuilder();
    }

    /**
     * Creates a new default instance. The properties of default instance are described in
     * {@link RetryTemplateBuilder} documentation.
     *
     * @return a new instance of RetryTemplate with default behaviour
     * @since 1.3
     */
    public static RetryTemplate defaultInstance() {
        return new RetryTemplateBuilder().build();
    }

    /**
     * 当所有重试尝试耗尽时，是否重新抛出最后一个异常或将其包装为 {@link ExhaustedRetryException}。
     * 默认为 {@code false}；仅在有状态重试的情况下应用
     * <p>
     * 如{@link org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor}
     *
     * @param throwLastExceptionOnExhausted the throwLastExceptionOnExhausted to set
     */
    public void setThrowLastExceptionOnExhausted(boolean throwLastExceptionOnExhausted) {
        this.throwLastExceptionOnExhausted = throwLastExceptionOnExhausted;
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
     * Setter for listeners. The listeners are executed before and after a retry block
     * (i.e. before and after all the attempts), and on an error (every attempt).
     *
     * @param listeners the {@link RetryListener}s
     * @see RetryListener
     */
    public void setListeners(RetryListener[] listeners) {
        Assert.notNull(listeners, "'listeners' must not be null");
        this.listeners = Arrays.copyOf(listeners, listeners.length);
    }

    /**
     * Register an additional listener at the end of the list.
     *
     * @param listener the {@link RetryListener}
     * @see #setListeners(RetryListener[])
     */
    public void registerListener(RetryListener listener) {
        registerListener(listener, this.listeners.length);
    }

    /**
     * Register an additional listener at the specified index.
     *
     * @param listener the {@link RetryListener}
     * @param index    the position in the list.
     * @see #setListeners(RetryListener[])
     * @since 1.3
     */
    public void registerListener(RetryListener listener, int index) {
        List<RetryListener> list = new ArrayList<>(Arrays.asList(this.listeners));
        if (index >= list.size()) {
            list.add(listener);
        } else {
            list.add(index, listener);
        }
        this.listeners = list.toArray(new RetryListener[0]);
    }

    /**
     * Return true if at least one listener is registered.
     *
     * @return true if listeners present.
     * @since 1.3
     */
    public boolean hasListeners() {
        return this.listeners.length > 0;
    }

    /**
     * Setter for {@link Log}. If not applied the following is used:
     * <p>
     * {@code LogFactory.getLog(getClass())}
     * </p>
     *
     * @param logger the logger the retry template uses for logging
     * @since 2.0.10
     */
    public void setLogger(Log logger) {
        this.logger = logger;
    }

    /**
     * Setter for {@link BackOffPolicy}.
     *
     * @param backOffPolicy the {@link BackOffPolicy}
     */
    public void setBackOffPolicy(BackOffPolicy backOffPolicy) {
        this.backOffPolicy = backOffPolicy;
    }

    /**
     * Setter for {@link RetryPolicy}.
     *
     * @param retryPolicy the {@link RetryPolicy}
     */
    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * 持续执行回调，直到其成功或重试策略{@link #retryPolicy}决定停止为止，此时将重新抛出回调中最后一次抛出的异常
     *
     * @param retryCallback the {@link RetryCallback}
     * @throws TerminatedRetryException 如果重试已被监听器手动终止
     * @see RetryOperations#execute(RetryCallback)
     */
    @Override
    public final <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback) throws E {
        return doExecute(retryCallback, null, null);
    }

    /**
     * 持续执行回调函数，直到成功或重试策略{@link #retryPolicy}决定停止，当重试被耗尽时将执行恢复回调函数
     *
     * @param retryCallback    the {@link RetryCallback}
     * @param recoveryCallback the {@link RecoveryCallback}
     * @throws TerminatedRetryException 如果重试已被监听器手动终止
     * @see RetryOperations#execute(RetryCallback, RecoveryCallback)
     */
    @Override
    public final <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback,
                                                    RecoveryCallback<T> recoveryCallback) throws E {
        return doExecute(retryCallback, recoveryCallback, null);
    }

    /**
     * 有状态重试
     * <p>
     * 如果重生策略{@link RetryPolicy}决定我们可以执行回调一次，则执行回调。
     * 如果回调执行异常，则会重新抛出遇到的任何异常，以便客户端可以稍后重新执行相同的任务。
     *
     * @param retryCallback the {@link RetryCallback}
     * @param retryState    the {@link RetryState}
     * @throws ExhaustedRetryException 如果重试次数已用尽
     * @see RetryOperations#execute(RetryCallback, RetryState)
     */
    @Override
    public final <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RetryState retryState)
            throws E, ExhaustedRetryException {
        return doExecute(retryCallback, null, retryState);
    }

    /**
     * 有状态重试
     * <p>
     * 如果重生策略{@link RetryPolicy}决定我们可以执行回调一次，则执行回调。
     * 如果回调执行异常，则会重新抛出遇到的任何异常，以便客户端可以稍后重新执行相同的任务。
     * 如果重试尝试耗尽，则执行恢复回调{@link RecoveryCallback}。
     *
     * @param retryCallback    the {@link RetryCallback}
     * @param recoveryCallback the {@link RecoveryCallback}
     * @param retryState       the {@link RetryState}
     * @see RetryOperations#execute(RetryCallback, RetryState)
     */
    @Override
    public final <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback,
                                                    RecoveryCallback<T> recoveryCallback, RetryState retryState) throws E, ExhaustedRetryException {
        return doExecute(retryCallback, recoveryCallback, retryState);
    }

    /**
     * 有状态重试
     * <p>
     * 如果策略{@link RetryPolicy}决定我们可以执行回调一次，则执行回调。
     * 如果回调执行异常，则会重新抛出遇到的任何异常，以便客户端可以稍后重新执行相同的任务。
     * 如果重试尝试耗尽，则执行恢复回调{@link RecoveryCallback}。
     *
     * @param recoveryCallback the {@link RecoveryCallback}
     * @param retryCallback    the {@link RetryCallback}
     * @param state            the {@link RetryState}
     * @param <T>              the type of the return value
     * @param <E>              the exception type to throw
     * @return T the retried value
     * @throws ExhaustedRetryException 如果重试次数已用尽。
     * @throws E                       an exception if the retry operation fails
     * @see RetryOperations#execute(RetryCallback, RecoveryCallback, RetryState)
     */
    protected <T, E extends Throwable> T doExecute(
            RetryCallback<T, E> retryCallback,
            RecoveryCallback<T> recoveryCallback,
            RetryState state
    ) throws E, ExhaustedRetryException {

        RetryPolicy retryPolicy = this.retryPolicy;
        BackOffPolicy backOffPolicy = this.backOffPolicy;

        // 开启重试上下文，会委托给 RetryPolicy 来创建一个新的 RetryContext
        // 如果 RetryState 非空（有状态重试），则会在缓存(RetryContextCache)中检查是否存在已存在的 RetryContext，
        // 如果存在则返回该上下文，否则创建一个新的上下文并将其存入缓存
        RetryContext context = open(retryPolicy, state);
        if (this.logger.isTraceEnabled()) {
            this.logger.trace("RetryContext retrieved: " + context);
        }

        // 将当前的 RetryContext 注册到一个ThreadLocal中, 这样在同一个线程内的其他代码可以随时获取到当前的重试上下文。
        // 这对于需要访问重试状态的组件或回调很有用
        RetrySynchronizationManager.register(context);

        Throwable lastException = null;

        boolean exhausted = false;
        try {

            // 在正式开始重试操作前，允许注册的 RetryListener 监听器对当前的 RetryContext 进行自定义处理，比如添加属性、做一些初始化操作等
            boolean running = doOpenInterceptors(retryCallback, context);

            // 监听器可以通过返回 false 来阻止重试操作的进行，届时将会抛出 TerminatedRetryException 异常
            if (!running) {
                throw new TerminatedRetryException("Retry terminated abnormally by interceptor before first attempt");
            }

            // 向上下文中设置最大重试次数属性
            if (!context.hasAttribute(RetryContext.MAX_ATTEMPTS)) {
                context.setAttribute(RetryContext.MAX_ATTEMPTS, retryPolicy.getMaxAttempts());
            }

            BackOffContext backOffContext = null;
            Object resource = context.getAttribute("backOffContext");

            if (resource instanceof BackOffContext) {
                backOffContext = (BackOffContext) resource;
            }

            if (backOffContext == null) {
                // 开启一次新的退避操作
                // 对于有状态的BackOffPolicy而言会返回一个新的BackOffContext来存储在每次退避操作中的状态
                // 对于无状态的BackOffPolicy而言则返回null，因为它不需要存储任何状态，它每次的退避操作都是一样的，都可能会暂停相同的时间
                backOffContext = backOffPolicy.start(context);
                if (backOffContext != null) {
                    // 对于有状态的BackOffPolicy而言，必须将BackOffContext存储在RetryContext中，以便在后续的退避操作中获取并进行使用
                    context.setAttribute("backOffContext", backOffContext);
                }
            }

            Object label = retryCallback.getLabel();
            String labelMessage = (label != null) ? "; for: '" + label + "'" : "";

            /*
             * We allow the whole loop to be skipped if the policy or context already
             * forbid the first try. This is used in the case of external retry to allow a
             * recovery in handleRetryExhausted without the callback processing (which
             * would throw an exception).
             */
            while (canRetry(retryPolicy, context) && !context.isExhaustedOnly()) {

                try {
                    // Reset the last exception, so if we are successful
                    // the close interceptors will not think we failed...
                    lastException = null;
                    // 执行用户的可重试操作
                    T result = retryCallback.doWithRetry(context);
                    // 如果重试成功, 则将结果传递给监听器进行处理
                    doOnSuccessInterceptors(retryCallback, context, result);
                    return result;
                }
                catch (Throwable e) {

                    lastException = e;

                    try {
                        registerThrowable(retryPolicy, state, context, e);
                    } catch (Exception ex) {
                        throw new TerminatedRetryException("Could not register throwable", ex);
                    } finally {
                        // 重试回调抛出异常后，通知所有已注册的监听器
                        doOnErrorInterceptors(retryCallback, context, e);
                    }

                    // 用于判断是否可以重试: 1、重试策略允许重试；2、根据上下文中的标志, 用户可以通过调用 context.setExhaustedOnly() 来手动终止重试
                    if (canRetry(retryPolicy, context) && !context.isExhaustedOnly()) {
                        try {
                            // 调用退避策略进行退避
                            backOffPolicy.backOff(backOffContext);
                        } catch (BackOffInterruptedException ex) {
                            // 由于另一个线程的介入，回退操作被打断 - 在这种情况下直接抛出异常以终止重试。
                            if (this.logger.isDebugEnabled()) {
                                this.logger.debug("Abort retry because interrupted: count=" + context.getRetryCount()
                                        + labelMessage);
                            }
                            throw ex;
                        }
                    }

                    // 是否需要抛出异常，对于有状态重试而言，在默认情况下需要将最后一次抛出的异常重新抛出
                    if (shouldRethrow(retryPolicy, context, state)) {
                        throw RetryTemplate.<E>wrapIfNecessary(e);
                    }
                }

                /*
                 * 一个有状态的重试，如果可以重试，可能会在当前之前重新抛出异常，
                 * 但如果在有状态的重试中走到了这一步，肯定是有原因的，比如：circuit breaker(断路器) 或 rollback classifier(回滚分类器)
                 */

                // 对circuit breaker(断路器)的情况进行处理 -> 阻断继续重试
                if (state != null && context.hasAttribute(GLOBAL_STATE)) {
                    // 对于circuit breaker(断路器)而言，走到这里是一定会进入这个分支的，
                    // 因为在创建CircuitBreakerRetryContext(断路器上下文时)会向上下文中添加GLOBAL_STATE属性，且属性值为true
                    // 那么这种情况下，我们就需要跳出重试循环，以完成断路保护
                    break;
                }

                // 对于 无状态重试 或者 有状态重试中不需要抛出异常的情况[由rollback classifier(回滚分类器)来决定]，
                // 继续进行下一次重试，直到重试次数耗尽或者重试成功
            }

            // 走到这里，就代表着重试次数已经耗尽
            if (state == null && this.logger.isDebugEnabled()) {
                this.logger.debug("Retry failed last attempt: count=" + context.getRetryCount() + labelMessage);
            }

            // 走到这里，代表着已经耗尽了重试次数，因此需要对这种场景进行处理(例如进行恢复)
            exhausted = true;
            return handleRetryExhausted(recoveryCallback, context, state);

        } catch (Throwable e) {
            throw RetryTemplate.<E>wrapIfNecessary(e);
        } finally {
            close(retryPolicy, context, state, lastException == null || exhausted);
            doCloseInterceptors(retryCallback, context, lastException);
            RetrySynchronizationManager.clear();
        }
    }

    /**
     * Decide whether to proceed with the ongoing retry attempt. This method is called
     * before the {@link RetryCallback} is executed, but after the backoff and open
     * interceptors.
     *
     * @param retryPolicy the policy to apply
     * @param context     the current retry context
     * @return true if we can continue with the attempt
     */
    protected boolean canRetry(RetryPolicy retryPolicy, RetryContext context) {
        return retryPolicy.canRetry(context);
    }

    /**
     * Clean up the cache if necessary and close the context provided (if the flag
     * indicates that processing was successful).
     *
     * @param retryPolicy the {@link RetryPolicy}
     * @param context     the {@link RetryContext}
     * @param state       the {@link RetryState}
     * @param succeeded   whether the close succeeded
     */
    protected void close(RetryPolicy retryPolicy, RetryContext context, RetryState state, boolean succeeded) {
        if (state != null) {
            if (succeeded) {
                if (!context.hasAttribute(GLOBAL_STATE)) {
                    this.retryContextCache.remove(state.getKey());
                }
                retryPolicy.close(context);
                context.setAttribute(RetryContext.CLOSED, true);
            }
        } else {
            retryPolicy.close(context);
            context.setAttribute(RetryContext.CLOSED, true);
        }
    }

    /**
     * 注册一个异常到当前的重试上下文中。
     *
     * @param retryPolicy
     * @param state
     * @param context
     * @param e
     */
    protected void registerThrowable(RetryPolicy retryPolicy, RetryState state, RetryContext context, Throwable e) {
        retryPolicy.registerThrowable(context, e);
        registerContext(context, state);
    }

    /**
     * 向缓存{@link RetryContextCache}中注册当前的{@link RetryContext}。
     *
     * @param context
     * @param state
     */
    private void registerContext(RetryContext context, RetryState state) {
        if (state != null) {
            Object key = state.getKey();
            if (key != null) {
                if (context.getRetryCount() > 1 && !this.retryContextCache.containsKey(key)) {
                    throw new RetryException("Inconsistent state for failed item key: cache key has changed. "
                            + "Consider whether equals() or hashCode() for the key might be inconsistent, "
                            + "or if you need to supply a better key");
                }
                this.retryContextCache.put(key, context);
            }
        }
    }

    /**
     * 会委派给 {@link RetryPolicy}来开启重试上下文
     * <p>
     * 如果RetryState非空(有状态重试)，则会在缓存{@link RetryContextCache}中检查是否存在已存在的{@link RetryContext}，如果存在
     * 则返回该上下文，否则创建一个新的上下文并将其存入缓存。
     *
     * @param state       a {@link RetryState}
     * @param retryPolicy a {@link RetryPolicy} to delegate the context creation
     * @return 一个重试上下文，可以是新的，也可以是上次遇到相同状态时使用的那个上下文
     */
    protected RetryContext open(RetryPolicy retryPolicy, RetryState state) {
        if (state == null) {
            // 无状态重试 -> 开启重试上下文
            return doOpenInternal(retryPolicy);
        }

        // 有状态重试
        Object key = state.getKey();
        if (state.isForceRefresh()) {
            // 在重试之前已知该key是新的，则无需不检索缓存，直接开启一个有状态的重试上下文
            return doOpenInternal(retryPolicy, state);
        }

        // 当缓存（retryContextCache）中没有对应的 key 时，就不需要去做缓存的“重建”或“恢复”操作（比如从外部存储加载、反序列化等），
        // 可以直接新建一个重试上下文对象，从而提升性能
        if (!this.retryContextCache.containsKey(key)) {
            // 只有在发生失败（如重试过程中抛出异常）时，才会使用到缓存的重试上下文
            return doOpenInternal(retryPolicy, state);
        }

        RetryContext context = this.retryContextCache.get(key);
        if (context == null) {
            if (this.retryContextCache.containsKey(key)) {
                throw new RetryException("Inconsistent state for failed item: no history found. "
                        + "Consider whether equals() or hashCode() for the item might be inconsistent, "
                        + "or if you need to supply a better ItemKeyGenerator");
            }
            // 缓存中的重试上下文，可能在在调用 `containsKey` 之后过期了，因此我们重新开启一个新的重试上下文
            return doOpenInternal(retryPolicy, state);
        }

        // 成功从缓存中获取到重试上下文，那么需要确保这个上下文在本次重试操作中是“干净”的，不会收到上一次遗漏状态的影响
        // 因此在这里需要清理上下文中的相关状态
        context.removeAttribute(RetryContext.CLOSED);
        context.removeAttribute(RetryContext.EXHAUSTED);
        context.removeAttribute(RetryContext.RECOVERED);
        return context;
    }


    /**
     * 开启重试上下文，会委派给 {@link RetryPolicy} 来创建一个新的 {@link RetryContext}。
     *
     * @param retryPolicy 重试策略
     * @param state       重试状态
     * @return
     */
    private RetryContext doOpenInternal(RetryPolicy retryPolicy, RetryState state) {
        RetryContext currentContext = RetrySynchronizationManager.getContext();
        RetryContext context = retryPolicy.open(currentContext);
        if (state != null) {
            // 向重试上下文中设置状态键
            context.setAttribute(RetryContext.STATE_KEY, state.getKey());
        }
        if (context.hasAttribute(GLOBAL_STATE)) {
            /*
             如果当前重试上下文是全局的, 那么则向 RetryContextCache 中注册该上下文 -> key为state.getKey(), value为context。
                - 对于有状态重试而言, 当一次重试操作出现异常后，会立即传播该异常，那么就需要从 RetryContextCache 中检索到之前的重试上下文，
                  以便于后续的重试操作可以继续使用该上下文。
                  因此对于有状态重试而言，它的重试上下文是全局的
                - 对于无状态重试而言, 当一次重试操作出现异常后, 并不会传播异常, 因此可以保证无状态重试可以从当前栈中获取到当前的重试上下文,
                  因此对于无状态重试而言，它的重试上下文永远不是全局的
             */
            registerContext(context, state);
        }
        return context;
    }

    /**
     * 开启重试上下文
     *
     * @param retryPolicy
     * @return
     */
    private RetryContext doOpenInternal(RetryPolicy retryPolicy) {
        return doOpenInternal(retryPolicy, null);
    }

    /**
     * 当最后一次重试失败后需要执行的操作。如果存在状态则清理缓存。如果有恢复回调，则执行并返回其结果。
     * 否则，抛出异常。
     *
     * @param recoveryCallback the callback for recovery (might be null)
     * @param context          the current retry context
     * @param state            the {@link RetryState}
     * @param <T>              the type to classify
     * @return T the payload to return
     * @throws Exception               if the callback does, and if there is no callback and the state
     *                                 is null then the last exception from the context
     * @throws ExhaustedRetryException if the state is not null and there is no recovery
     *                                 callback
     * @throws Throwable               if there is an error
     */
    protected <T> T handleRetryExhausted(RecoveryCallback<T> recoveryCallback, RetryContext context, RetryState state)
            throws Throwable {

        context.setAttribute(RetryContext.EXHAUSTED, true);

        if (state != null && !context.hasAttribute(GLOBAL_STATE)) {
            // 对于不是全局性的重试上下文, 则需要将其从缓存中移除
            // 目前全局性的重试上下文为：有状态重试中的断路器上下文(CircuitBreakerRetryContext)会是全局性的
            this.retryContextCache.remove(state.getKey());
        }

        // 出现异常是否允许恢复
        boolean doRecover = !Boolean.TRUE.equals(context.getAttribute(RetryContext.NO_RECOVERY));
        if (recoveryCallback != null) {
            if (doRecover) {
                try {
                    // 执行恢复逻辑
                    T recovered = recoveryCallback.recover(context);
                    context.setAttribute(RetryContext.RECOVERED, true);
                    return recovered;
                } catch (UndeclaredThrowableException undeclaredThrowableException) {
                    throw wrapIfNecessary(undeclaredThrowableException.getUndeclaredThrowable());
                }
            } else {
                logger.debug("Retry exhausted and recovery disabled for this throwable");
            }
        }

        if (state != null) {
            this.logger.debug("Retry exhausted after last attempt with no recovery path.");
            // 有状态重试且没有恢复回调，则抛出 ExhaustedRetryException 异常
            rethrow(context, "Retry exhausted after last attempt with no recovery path",
                    this.throwLastExceptionOnExhausted || !doRecover);
        }
        throw wrapIfNecessary(context.getLastThrowable());
    }

    protected <E extends Throwable> void rethrow(RetryContext context, String message, boolean wrap) throws E {
        if (wrap) {
            @SuppressWarnings("unchecked")
            E rethrow = (E) context.getLastThrowable();
            throw rethrow;
        } else {
            throw new ExhaustedRetryException(message, context.getLastThrowable());
        }
    }


    /**
     * 供子类扩展的切入点，用于在捕获到 {@link RetryCallback} 的执行异常后决定后续行为。
     * 普通的无状态重试通常不会重新抛出异常，而有状态重试则会重新抛出异常。
     *
     * @param retryPolicy the retry policy
     * @param context     the current context
     * @param state       the current retryState
     * @return 如果RetryState为非空(有状态重试需要抛出)，并且{@link RetryState#rollbackFor(Throwable)}为真，但子类也可以选择不同的实现方式
     */
    protected boolean shouldRethrow(RetryPolicy retryPolicy, RetryContext context, RetryState state) {
        return state != null && state.rollbackFor(context.getLastThrowable());
    }

    /**
     * 回调 {@link RetryListener#open(RetryContext, RetryCallback)}
     *
     * @param callback
     * @param context
     * @param <T>
     * @param <E>
     * @return
     */
    private <T, E extends Throwable> boolean doOpenInterceptors(RetryCallback<T, E> callback, RetryContext context) {

        boolean result = true;

        for (RetryListener listener : this.listeners) {
            result = result && listener.open(context, callback);
        }

        return result;

    }

    private <T, E extends Throwable> void doCloseInterceptors(RetryCallback<T, E> callback, RetryContext context,
                                                              Throwable lastException) {
        for (int i = this.listeners.length; i-- > 0; ) {
            this.listeners[i].close(context, callback, lastException);
        }
    }

    private <T, E extends Throwable> void doOnSuccessInterceptors(RetryCallback<T, E> callback, RetryContext context,
                                                                  T result) {
        for (int i = this.listeners.length; i-- > 0; ) {
            this.listeners[i].onSuccess(context, callback, result);
        }
    }

    private <T, E extends Throwable> void doOnErrorInterceptors(RetryCallback<T, E> callback, RetryContext context,
                                                                Throwable throwable) {
        for (int i = this.listeners.length; i-- > 0; ) {
            this.listeners[i].onError(context, callback, throwable);
        }
    }

    /**
     * Re-throws the original throwable if it is an Exception, and wraps non-exceptions
     * into {@link RetryException}.
     */
    private static <E extends Throwable> E wrapIfNecessary(Throwable throwable) throws RetryException {
        if (throwable instanceof Error) {
            throw (Error) throwable;
        } else if (throwable instanceof Exception) {
            @SuppressWarnings("unchecked")
            E rethrow = (E) throwable;
            return rethrow;
        } else {
            throw new RetryException("Exception in retry", throwable);
        }
    }

}
