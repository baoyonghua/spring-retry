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

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 简单的重试策略，会针对一组指定的异常（及其子类）重试固定次数。尝试次数包含初始尝试，例如：
 * <pre>
 * retryTemplate = new RetryTemplate(new SimpleRetryPolicy(3));
 * retryTemplate.execute(callback);
 * </pre>
 * <p>
 * 至少会执行一次callback，最多可能执行三次。
 * <p>
 * 从 1.3 版本开始，不再必须使用此类。可以通过构建一个包含
 * {@link MaxAttemptsRetryPolicy} 和 {@link BinaryExceptionClassifierRetryPolicy} 的
 * {@link CompositeRetryPolicy} 来实现相同的行为，实际上等价于如下方式：
 *
 * <pre>
 * RetryTemplate.builder()
 *                  .maxAttempts(3)  // MaxAttemptsRetryPolicy
 *                  .retryOn(Exception.class)  // BinaryExceptionClassifierRetryPolicy
 *                  .build();
 * </pre>
 * <p>
 * 或者通过 {@link org.springframework.retry.support.RetryTemplate#defaultInstance()} 也能实现相同的行为
 *
 * @author Dave Syer
 * @author Rob Harrop
 * @author Gary Russell
 * @author Aleksandr Shamukov
 * @author Artem Bilan
 * @author Emanuele Ivaldi
 */
@SuppressWarnings("serial")
public class SimpleRetryPolicy implements RetryPolicy {

    /**
     * 尝试次数的默认限制。
     */
    public final static int DEFAULT_MAX_ATTEMPTS = 3;

    private int maxAttempts;

    private Supplier<Integer> maxAttemptsSupplier;

    private BinaryExceptionClassifier retryableClassifier;

    private BinaryExceptionClassifier recoverableClassifier = new BinaryExceptionClassifier(Collections.emptyMap(),
            true, true);

    /**
     * Create a {@link SimpleRetryPolicy} with the default number of retry attempts,
     * retrying all exceptions.
     */
    public SimpleRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, BinaryExceptionClassifier.defaultClassifier());
    }

    /**
     * Create a {@link SimpleRetryPolicy} with the specified number of retry attempts,
     * retrying all exceptions.
     *
     * @param maxAttempts the maximum number of attempts
     */
    public SimpleRetryPolicy(int maxAttempts) {
        this(maxAttempts, BinaryExceptionClassifier.defaultClassifier());
    }

    /**
     * Create a {@link SimpleRetryPolicy} with the specified number of retry attempts.
     *
     * @param maxAttempts         the maximum number of attempts
     * @param retryableExceptions the map of exceptions that are retryable
     */
    public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions) {
        this(maxAttempts, retryableExceptions, false);
    }

    /**
     * Create a {@link SimpleRetryPolicy} with the specified number of retry attempts. If
     * traverseCauses is true, the exception causes will be traversed until a match or the
     * root cause is found.
     *
     * @param maxAttempts         the maximum number of attempts
     * @param retryableExceptions the map of exceptions that are retryable based on the
     *                            map value (true/false).
     * @param traverseCauses      true to traverse the exception cause chain until a classified
     *                            exception is found or the root cause is reached.
     */
    public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
                             boolean traverseCauses) {
        this(maxAttempts, retryableExceptions, traverseCauses, false);
    }

    /**
     * Create a {@link SimpleRetryPolicy} with the specified number of retry attempts. If
     * traverseCauses is true, the exception causes will be traversed until a match or the
     * root cause is found. The default value indicates whether to retry or not for
     * exceptions (or super classes thereof) that are not found in the map.
     *
     * @param maxAttempts         the maximum number of attempts
     * @param retryableExceptions the map of exceptions that are retryable based on the
     *                            map value (true/false).
     * @param traverseCauses      true to traverse the exception cause chain until a classified
     *                            exception is found or the root cause is reached.
     * @param defaultValue        the default action.
     */
    public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
                             boolean traverseCauses, boolean defaultValue) {
        super();
        this.maxAttempts = maxAttempts;
        this.retryableClassifier = new BinaryExceptionClassifier(retryableExceptions, defaultValue);
        this.retryableClassifier.setTraverseCauses(traverseCauses);
    }

    /**
     * Create a {@link SimpleRetryPolicy} with the specified number of retry attempts and
     * provided exception classifier.
     *
     * @param maxAttempts the maximum number of attempts
     * @param classifier  custom exception classifier
     */
    public SimpleRetryPolicy(int maxAttempts, BinaryExceptionClassifier classifier) {
        super();
        this.maxAttempts = maxAttempts;
        this.retryableClassifier = classifier;
    }

    /**
     * Set the number of attempts before retries are exhausted. Includes the initial
     * attempt before the retries begin so, generally, will be {@code >= 1}. For example
     * setting this property to 3 means 3 attempts total (initial + 2 retries).
     *
     * @param maxAttempts the maximum number of attempts including the initial attempt.
     */
    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Configure throwables that should not be passed to a recoverer (if present) but
     * thrown immediately.
     *
     * @param noRecovery the throwables.
     * @since 3.0
     */
    @SuppressWarnings("unchecked")
    public void setNotRecoverable(Class<? extends Throwable>... noRecovery) {
        Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
        for (Class<? extends Throwable> clazz : noRecovery) {
            map.put(clazz, false);
        }
        this.recoverableClassifier = new BinaryExceptionClassifier(map, true, true);
    }

    /**
     * Set a supplier for the number of attempts before retries are exhausted. Includes
     * the initial attempt before the retries begin so, generally, will be {@code >= 1}.
     * For example setting this property to 3 means 3 attempts total (initial + 2
     * retries). IMPORTANT: This policy cannot be serialized when a max attempts supplier
     * is provided. Serialization might be used by a distributed cache when using this
     * policy in a {@code CircuitBreaker} context.
     *
     * @param maxAttemptsSupplier the maximum number of attempts including the initial
     *                            attempt.
     * @since 2.0
     */
    public void maxAttemptsSupplier(Supplier<Integer> maxAttemptsSupplier) {
        Assert.notNull(maxAttemptsSupplier, "'maxAttemptsSupplier' cannot be null");
        this.maxAttemptsSupplier = maxAttemptsSupplier;
    }

    /**
     * The maximum number of attempts before failure.
     *
     * @return the maximum number of attempts
     */
    @Override
    public int getMaxAttempts() {
        if (this.maxAttemptsSupplier != null) {
            return this.maxAttemptsSupplier.get();
        }
        return this.maxAttempts;
    }

    /**
     * Test for retryable operation based on the status.
     *
     * @return true if the last exception was retryable and the number of attempts so far
     * is less than the limit.
     * @see org.springframework.retry.RetryPolicy#canRetry(org.springframework.retry.RetryContext)
     */
    @Override
    public boolean canRetry(RetryContext context) {
        Throwable t = context.getLastThrowable();
        boolean can = (t == null || retryForException(t)) && context.getRetryCount() < getMaxAttempts();
        if (!can && t != null && !this.recoverableClassifier.classify(t)) {
            context.setAttribute(RetryContext.NO_RECOVERY, true);
        } else {
            context.removeAttribute(RetryContext.NO_RECOVERY);
        }
        return can;
    }

    /**
     * @see org.springframework.retry.RetryPolicy#close(RetryContext)
     */
    @Override
    public void close(RetryContext status) {
    }

    /**
     * Update the status with another attempted retry and the latest exception.
     *
     * @see RetryPolicy#registerThrowable(RetryContext, Throwable)
     */
    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        SimpleRetryContext simpleContext = ((SimpleRetryContext) context);
        simpleContext.registerThrowable(throwable);
    }

    /**
     * Get a status object that can be used to track the current operation according to
     * this policy. Has to be aware of the latest exception and the number of attempts.
     *
     * @see org.springframework.retry.RetryPolicy#open(RetryContext)
     */
    @Override
    public RetryContext open(RetryContext parent) {
        return new SimpleRetryContext(parent);
    }

    private static class SimpleRetryContext extends RetryContextSupport {

        public SimpleRetryContext(RetryContext parent) {
            super(parent);
        }

    }

    /**
     * Delegates to an exception classifier.
     *
     * @param ex the exception to classify.
     * @return true if this exception or its ancestors have been registered as retryable.
     */
    private boolean retryForException(Throwable ex) {
        return this.retryableClassifier.classify(ex);
    }

    @Override
    public String toString() {
        return ClassUtils.getShortName(getClass()) + "[maxAttempts=" + getMaxAttempts() + "]";
    }

}
