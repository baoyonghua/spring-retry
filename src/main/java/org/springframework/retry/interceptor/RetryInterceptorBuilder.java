/*
 * Copyright 2014 the original author or authors.
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
package org.springframework.retry.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.classify.Classifier;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * 简化的Builder，使构建 {@link StatefulRetryOperationsInterceptor} 或（无状态的）
 * {@link RetryOperationsInterceptor} 更加容易和简单，通过提供流式接口来定义异常时的行为。
 * <p>
 * Typical example:
 * </p>
 *
 * <pre class="code">
 * StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful()
 * 		.maxAttempts(5).backOffOptions(1, 2, 10) // initialInterval, multiplier,
 * 													// maxInterval
 * 		.build();
 * </pre>
 *
 * @param <T> The type of {@link org.aopalliance.intercept.MethodInterceptor} returned by
 *            the builder's {@link #build()} method.
 * @author James Carr
 * @author Gary Russell
 * @author Artem Bilan
 * @since 1.1
 */
public abstract class RetryInterceptorBuilder<T extends MethodInterceptor> {

    /**
     * RetryTemplate
     */
    protected final RetryTemplate retryTemplate = new RetryTemplate();

    /**
     * 简单的重试策略实例，默认情况下最大尝试次数为 3。
     */
    protected final SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();

    /**
     * @see #retryTemplate
     */
    protected RetryOperations retryOperations;

    /**
     * 恢复器，在所有重试尝试失败后调用恢复器以尝试进行恢复操作
     */
    protected MethodInvocationRecoverer<?> recoverer;

    /**
     * 指示是否修改了默认的 RetryTemplate
     */
    private boolean templateAltered;

    /**
     * 指示是否设置了 BackOffPolicy
     */
    private boolean backOffPolicySet;

    /**
     * 指示是否设置了 RetryPolicy
     */
    private boolean retryPolicySet;

    /**
     * 指示是否设置了 BackOffOptions
     */
    private boolean backOffOptionsSet;

    /**
     * 用于统计报告的唯一标签
     */
    protected String label;

    /**
     * 创建一个有状态的重试拦截器的构建器。
     *
     * @return The interceptor builder.
     * @see StatefulRetryInterceptorBuilder
     */
    public static StatefulRetryInterceptorBuilder stateful() {
        return new StatefulRetryInterceptorBuilder();
    }

    /**
     * 创建一个断路器重试拦截器的构建器。
     *
     * @return The interceptor builder.
     * @see CircuitBreakerInterceptorBuilder
     */
    public static CircuitBreakerInterceptorBuilder circuitBreaker() {
        return new CircuitBreakerInterceptorBuilder();
    }

    /**
     * 创建一个无状态重试拦截器的构建器。
     *
     * @return The interceptor builder.
     * @see StatelessRetryInterceptorBuilder
     */
    public static StatelessRetryInterceptorBuilder stateless() {
        return new StatelessRetryInterceptorBuilder();
    }

    /**
     * 应用一个{@link RetryOperations}
     * <p>
     * 设置后，其他属性将不能再被设置；如果其他属性已被应用，则无法再设置。
     *
     * @param retryOperations The retry operations.
     * @return this.
     */
    public RetryInterceptorBuilder<T> retryOperations(RetryOperations retryOperations) {
        Assert.isTrue(!this.templateAltered, "Cannot set retryOperations when the default has been modified");
        this.retryOperations = retryOperations;
        return this;
    }

    /**
     * 应用最大重生次数 - 将使用 SimpleRetryPolicy 作为默认的重试策略
     * <p>
     * 如果已设置了{@link RetryOperations}或 {@link RetryPolicy}，则无法再次进行设置
     *
     * @param maxAttempts the max attempts (including the initial attempt).
     * @return this.
     */
    public RetryInterceptorBuilder<T> maxAttempts(int maxAttempts) {
        Assert.isNull(this.retryOperations, "cannot alter the retry policy when a custom retryOperations has been set");
        Assert.isTrue(!this.retryPolicySet, "cannot alter the retry policy when a custom retryPolicy has been set");
        this.simpleRetryPolicy.setMaxAttempts(maxAttempts);
        this.retryTemplate.setRetryPolicy(this.simpleRetryPolicy);
        this.templateAltered = true;
        return this;
    }

    /**
     * 应用退避选项。会使用 {@link ExponentialBackOffPolicy} 作为退避策略。
     * <p>
     * 如果已设置了{@link RetryOperations} 或 {@link BackOffPolicy}，则无法应用这些选项。
     *
     * @param initialInterval The initial interval.
     * @param multiplier      The multiplier.
     * @param maxInterval     The max interval.
     * @return this.
     */
    public RetryInterceptorBuilder<T> backOffOptions(long initialInterval, double multiplier, long maxInterval) {
        Assert.isNull(this.retryOperations,
                "cannot set the back off policy when a custom retryOperations has been set");
        Assert.isTrue(!this.backOffPolicySet, "cannot set the back off options when a back off policy has been set");
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(initialInterval);
        policy.setMultiplier(multiplier);
        policy.setMaxInterval(maxInterval);
        this.retryTemplate.setBackOffPolicy(policy);
        this.backOffOptionsSet = true;
        this.templateAltered = true;
        return this;
    }

    /**
     * 应用重试策略 - 如果已提供{@link RetryOperations}，或者已应用最大尝试次数、退避选项或退避策略，则无法使用此功能。
     *
     * @param policy The policy.
     * @return this.
     */
    public RetryInterceptorBuilder<T> retryPolicy(RetryPolicy policy) {
        Assert.isNull(this.retryOperations, "cannot set the retry policy when a custom retryOperations has been set");
        Assert.isTrue(!this.templateAltered,
                "cannot set the retry policy if max attempts or back off policy or options changed");
        this.retryTemplate.setRetryPolicy(policy);
        this.retryPolicySet = true;
        this.templateAltered = true;
        return this;
    }

    /**
     * 应用退避策略。如果已应用{@link RetryOperations}或{@link BackOffPolicy}，则不能使用。
     *
     * @param policy The policy.
     * @return this.
     */
    public RetryInterceptorBuilder<T> backOffPolicy(BackOffPolicy policy) {
        Assert.isNull(this.retryOperations,
                "cannot set the back off policy when a custom retryOperations has been set");
        Assert.isTrue(!this.backOffOptionsSet,
                "cannot set the back off policy when the back off policy options have been set");
        this.retryTemplate.setBackOffPolicy(policy);
        this.templateAltered = true;
        this.backOffPolicySet = true;
        return this;
    }

    /**
     * Apply a {@link MethodInvocationRecoverer} for the Retry interceptor.
     *
     * @param recoverer The recoverer.
     * @return this.
     */
    public RetryInterceptorBuilder<T> recoverer(MethodInvocationRecoverer<?> recoverer) {
        this.recoverer = recoverer;
        return this;
    }

    public RetryInterceptorBuilder<T> label(String label) {
        this.label = label;
        return this;
    }

    public abstract T build();

    private RetryInterceptorBuilder() {
    }

    public static class StatefulRetryInterceptorBuilder
            extends RetryInterceptorBuilder<StatefulRetryOperationsInterceptor> {

        /**
         * 有状态的重试拦截器实例
         */
        private final StatefulRetryOperationsInterceptor interceptor = new StatefulRetryOperationsInterceptor();

        private MethodArgumentsKeyGenerator keyGenerator;

        private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

        private Classifier<? super Throwable, Boolean> rollbackClassifier;

        /**
         * Stateful retry requires items to be identifiable.
         *
         * @param keyGenerator The key generator.
         * @return this.
         */
        public StatefulRetryInterceptorBuilder keyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
            this.keyGenerator = keyGenerator;
            return this;
        }

        /**
         * Apply a custom new item identifier.
         *
         * @param newMethodArgumentsIdentifier The new item identifier.
         * @return this.
         */
        public StatefulRetryInterceptorBuilder newMethodArgumentsIdentifier(
                NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
            this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
            return this;
        }

        /**
         * 控制正在进行中事务是否需要进行回滚。当给定的异常被分类为{@code true}时，事务将回滚。
         * <p>
         * 在有状态的重试中，通常所有异常都会导致回滚（即重新抛出）。
         *
         * @param rollbackClassifier 回滚分类器（对于应重新抛出的异常，value应该为true）
         * @return this.
         */
        public StatefulRetryInterceptorBuilder rollbackFor(Classifier<? super Throwable, Boolean> rollbackClassifier) {
            this.rollbackClassifier = rollbackClassifier;
            return this;
        }

        /**
         * 应用一个{@link RetryOperations}
         * <p>
         *
         * @param retryOperations The retry operations.
         * @return
         */
        @Override
        public StatefulRetryInterceptorBuilder retryOperations(RetryOperations retryOperations) {
            super.retryOperations(retryOperations);
            return this;
        }

        @Override
        public StatefulRetryInterceptorBuilder maxAttempts(int maxAttempts) {
            super.maxAttempts(maxAttempts);
            return this;
        }

        @Override
        public StatefulRetryInterceptorBuilder backOffOptions(long initialInterval, double multiplier,
                                                              long maxInterval) {
            super.backOffOptions(initialInterval, multiplier, maxInterval);
            return this;
        }

        @Override
        public StatefulRetryInterceptorBuilder retryPolicy(RetryPolicy policy) {
            super.retryPolicy(policy);
            return this;
        }

        @Override
        public StatefulRetryInterceptorBuilder backOffPolicy(BackOffPolicy policy) {
            super.backOffPolicy(policy);
            return this;
        }

        @Override
        public StatefulRetryInterceptorBuilder recoverer(MethodInvocationRecoverer<?> recoverer) {
            super.recoverer(recoverer);
            return this;
        }

        @Override
        public StatefulRetryOperationsInterceptor build() {
            if (this.recoverer != null) {
                this.interceptor.setRecoverer(this.recoverer);
            }
            if (this.retryOperations != null) {
                this.interceptor.setRetryOperations(this.retryOperations);
            } else {
                this.interceptor.setRetryOperations(this.retryTemplate);
            }
            if (this.keyGenerator != null) {
                this.interceptor.setKeyGenerator(this.keyGenerator);
            }
            if (this.rollbackClassifier != null) {
                this.interceptor.setRollbackClassifier(this.rollbackClassifier);
            }
            if (this.newMethodArgumentsIdentifier != null) {
                this.interceptor.setNewItemIdentifier(this.newMethodArgumentsIdentifier);
            }
            if (this.label != null) {
                this.interceptor.setLabel(this.label);
            }
            return this.interceptor;
        }

        private StatefulRetryInterceptorBuilder() {
        }

    }

    /**
     * 断路器重试拦截器的构建器。
     */
    public static class CircuitBreakerInterceptorBuilder
            extends RetryInterceptorBuilder<StatefulRetryOperationsInterceptor> {

        /**
         * 有状态的重试拦截器实例
         */
        private final StatefulRetryOperationsInterceptor interceptor = new StatefulRetryOperationsInterceptor();

        private MethodArgumentsKeyGenerator keyGenerator;

        @Override
        public CircuitBreakerInterceptorBuilder retryOperations(RetryOperations retryOperations) {
            super.retryOperations(retryOperations);
            return this;
        }

        @Override
        public CircuitBreakerInterceptorBuilder maxAttempts(int maxAttempts) {
            super.maxAttempts(maxAttempts);
            return this;
        }

        @Override
        public CircuitBreakerInterceptorBuilder retryPolicy(RetryPolicy policy) {
            super.retryPolicy(policy);
            return this;
        }

        public CircuitBreakerInterceptorBuilder keyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
            this.keyGenerator = keyGenerator;
            return this;
        }

        @Override
        public CircuitBreakerInterceptorBuilder recoverer(MethodInvocationRecoverer<?> recoverer) {
            super.recoverer(recoverer);
            return this;
        }

        @Override
        public StatefulRetryOperationsInterceptor build() {
            if (this.recoverer != null) {
                this.interceptor.setRecoverer(this.recoverer);
            }
            if (this.retryOperations != null) {
                this.interceptor.setRetryOperations(this.retryOperations);
            } else {
                this.interceptor.setRetryOperations(this.retryTemplate);
            }
            if (this.keyGenerator != null) {
                this.interceptor.setKeyGenerator(this.keyGenerator);
            }
            if (this.label != null) {
                this.interceptor.setLabel(this.label);
            }
            this.interceptor.setRollbackClassifier(new BinaryExceptionClassifier(false));
            return this.interceptor;
        }

        private CircuitBreakerInterceptorBuilder() {
        }

    }

    public static class StatelessRetryInterceptorBuilder extends RetryInterceptorBuilder<RetryOperationsInterceptor> {

        private final RetryOperationsInterceptor interceptor = new RetryOperationsInterceptor();

        @Override
        public RetryOperationsInterceptor build() {
            if (this.recoverer != null) {
                this.interceptor.setRecoverer(this.recoverer);
            }
            if (this.retryOperations != null) {
                this.interceptor.setRetryOperations(this.retryOperations);
            } else {
                this.interceptor.setRetryOperations(this.retryTemplate);
            }
            if (this.label != null) {
                this.interceptor.setLabel(this.label);
            }
            return this.interceptor;
        }

        private StatelessRetryInterceptorBuilder() {
        }

    }

}
