/*
 * Copyright 2006-2022 the original author or authors.
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

package org.springframework.retry.policy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 断路器重试策略.
 *
 * @author Dave Syer
 */
public class CircuitBreakerRetryPolicy implements RetryPolicy {

    private static final Log logger = LogFactory.getLog(CircuitBreakerRetryPolicy.class);

    /**
     * 上下文属性，表示断路器是否打开
     */
    public static final String CIRCUIT_OPEN = "circuit.open";

    /**
     * 上下文属性，表示断路器短路的计数
     */
    public static final String CIRCUIT_SHORT_COUNT = "circuit.shortCount";

    /**
     * 实际的重试策略
     */
    private final RetryPolicy delegate;

    /**
     * 重置断路器的超时时间（毫秒）。断路器打开后，经过此时间后会重新关闭，且上下文会被重置。
     */
    private long resetTimeout = 20000;

    /**
     * 断路器打开的超时时间（毫秒）
     * <p>
     * 如果委托的重试策略{@link #delegate}不能进行重试，且自上下文启动以来经过的时间小于此时间窗口，则断路器会被打开。
     * </p>
     */
    private long openTimeout = 5000;

    /**
     * 重置断路器的超时时间（毫秒）的供应器。断路器打开后，经过此时间后会重新关闭，且上下文会被重置。
     */
    private Supplier<Long> resetTimeoutSupplier;

    /**
     * 触发断路器打开的超时时间（毫秒）的供应器
     * <p>
     * 如果委托的重试策略{@link #delegate}不能进行重试，且自上下文启动以来经过的时间小于此窗口，则断路器会被打开。
     * </p>
     */
    private Supplier<Long> openTimeoutSupplier;

    public CircuitBreakerRetryPolicy() {
        this(new SimpleRetryPolicy());
    }

    public CircuitBreakerRetryPolicy(RetryPolicy delegate) {
        this.delegate = delegate;
    }

    /**
     * 重置断路器的超时时间（毫秒）。断路器打开后，经过此时间后会重新关闭，且上下文会被重置。
     * <p>
     * 假设我们设置的 resetTimeout 为 20000 毫秒（20 秒），当断路器因为连续失败被打开后, 20S后断路器会自动关闭，允许新的重试操作，并重置上下文
     * </p>
     *
     * @param timeout 要设置的超时时间（毫秒）
     */
    public void setResetTimeout(long timeout) {
        this.resetTimeout = timeout;
    }

    /**
     * A supplier for the timeout for resetting circuit in milliseconds. After the circuit
     * opens it will re-close after this time has elapsed and the context will be
     * restarted.
     *
     * @param timeoutSupplier a supplier for the timeout to set in milliseconds
     * @since 2.0
     */
    public void resetTimeoutSupplier(Supplier<Long> timeoutSupplier) {
        this.resetTimeoutSupplier = timeoutSupplier;
    }

    /**
     * 触发断路器打开的超时时间。如果委托策略无法重试，并且自上下文启动以来经过的时间小于该窗口，则断路器会被打开。
     * <p>
     * 假设我们设置的 openTimeout 为 5000 毫秒（5 秒），并且重试策略{@link #delegate}最多允许重试3次,
     * 那么如果在5秒内达到了3次失败，断路器会被打开，后续的重试请求会被直接拒绝, 直到断路器的重置时间到达{@link #resetTimeout}，
     * 以阻止对下游组件的访问。
     * </p>
     *
     * @param timeout 要设置的超时时间（毫秒）
     */
    public void setOpenTimeout(long timeout) {
        this.openTimeout = timeout;
    }

    /**
     * A supplier for the Timeout for tripping the open circuit. If the delegate policy
     * cannot retry and the time elapsed since the context was started is less than this
     * window, then the circuit is opened.
     *
     * @param timeoutSupplier a supplier for the timeout to set in milliseconds
     * @since 2.0
     */
    public void openTimeoutSupplier(Supplier<Long> timeoutSupplier) {
        this.openTimeoutSupplier = timeoutSupplier;
    }

    @Override
    public boolean canRetry(RetryContext context) {
        CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;

        if (circuit.isOpen()) {
            // 断路器打开时，拒绝重试请求
            circuit.incrementShortCircuitCount();
            return false;
        } else {
            // 断路器关闭时，重置短路的计数为0
            circuit.reset();
        }

        // 交由委托的重试策略决定是否可以重试
        return this.delegate.canRetry(circuit.context);
    }

    @Override
    public RetryContext open(RetryContext parent) {
        long resetTimeout = this.resetTimeout;
        if (this.resetTimeoutSupplier != null) {
            resetTimeout = this.resetTimeoutSupplier.get();
        }
        long openTimeout = this.openTimeout;
        if (this.openTimeoutSupplier != null) {
            openTimeout = this.openTimeoutSupplier.get();
        }
        return new CircuitBreakerRetryContext(parent, this.delegate, resetTimeout, openTimeout);
    }

    @Override
    public void close(RetryContext context) {
        CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
        this.delegate.close(circuit.context);
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
        circuit.registerThrowable(throwable);
        this.delegate.registerThrowable(circuit.context, throwable);
    }

    /**
     * 断路器重试上下文
     */
    static class CircuitBreakerRetryContext extends RetryContextSupport {

        /**
         * 实际用于重试的上下文
         */
        private volatile RetryContext context;

        /**
         * 实际用于重试的策略
         */
        private final RetryPolicy policy;

        /**
         * 上下文启动的时间戳（毫秒）
         */
        private volatile long start = System.currentTimeMillis();

        /**
         * 重置断路器的超时时间（毫秒）。断路器打开后，经过此时间后会重新关闭，且上下文会被重置。
         */
        private final long timeout;

        /**
         * 触发断路器打开的超时时间（毫秒）
         */
        private final long openWindow;

        /**
         * 断路器短路的计数
         */
        private final AtomicInteger shortCircuitCount = new AtomicInteger();

        public CircuitBreakerRetryContext(RetryContext parent, RetryPolicy policy, long timeout, long openWindow) {
            super(parent);
            this.policy = policy;
            this.timeout = timeout;
            this.openWindow = openWindow;
            this.context = createDelegateContext(policy, parent);
            setAttribute("state.global", true);
        }

        public void reset() {
            shortCircuitCount.set(0);
            setAttribute(CIRCUIT_SHORT_COUNT, shortCircuitCount.get());
        }

        public void incrementShortCircuitCount() {
            shortCircuitCount.incrementAndGet();
            setAttribute(CIRCUIT_SHORT_COUNT, shortCircuitCount.get());
        }

        private RetryContext createDelegateContext(RetryPolicy policy, RetryContext parent) {
            RetryContext context = policy.open(parent);
            reset();
            return context;
        }

        public boolean isOpen() {
            long time = System.currentTimeMillis() - this.start;
            boolean retryable = this.policy.canRetry(this.context);
            if (!retryable) {
                if (time > this.timeout) {
                    logger.trace("Closing");
                    this.context = createDelegateContext(policy, getParent());
                    this.start = System.currentTimeMillis();
                    retryable = this.policy.canRetry(this.context);
                } else if (time < this.openWindow) {
                    if (!hasAttribute(CIRCUIT_OPEN) || (Boolean) getAttribute(CIRCUIT_OPEN) == false) {
                        logger.trace("Opening circuit");
                        setAttribute(CIRCUIT_OPEN, true);
                        this.start = System.currentTimeMillis();
                    }

                    return true;
                }
            } else {
                if (time > this.openWindow) {
                    logger.trace("Resetting context");
                    this.start = System.currentTimeMillis();
                    this.context = createDelegateContext(policy, getParent());
                }
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Open: " + !retryable);
            }
            setAttribute(CIRCUIT_OPEN, !retryable);
            return !retryable;
        }

        @Override
        public int getRetryCount() {
            return this.context.getRetryCount();
        }

        @Override
        public String toString() {
            return this.context.toString();
        }
    }
}
