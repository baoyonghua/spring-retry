/*
 * Copyright 2006-2007 the original author or authors.
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

package org.springframework.retry;

import org.springframework.retry.support.DefaultRetryState;

/**
 * 定义了由 {@link RetryOperations} 实现的基本操作集，用于执行具有可配置重试行为的操作。
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public interface RetryOperations {

    /**
     * 使用已配置的重试语义执行用户所提供的 {@link RetryCallback}。具体配置细节请参见实现类。
     *
     * @param <T>           返回值类型
     * @param retryCallback {@link RetryCallback} 实例
     * @param <E>           操作所抛出的异常类型
     * @return 成功调用 {@link RetryCallback} 时返回的值。
     * @throws E 在重试失败时由 {@link RetryCallback} 抛出的任何 {@link Exception}。
     */
    <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback) throws E;

    /**
     * 执行用户所提供的 {@link RetryCallback}，当重试耗尽时会调用 {@link RecoveryCallback}来进行恢复。具体配置细节请参见实现类。
     *
     * @param recoveryCallback {@link RecoveryCallback} 实例
     * @param retryCallback    {@link RetryCallback} 实例
     * @param <T>              返回值类型
     * @param <E>              异常类型
     * @return 成功调用 {@link RetryCallback} 时返回的值，重试失败时返回 {@link RecoveryCallback} 的值。
     * @throws E 在重试失败时由 {@link RetryCallback} 抛出的任何 {@link Exception}。
     */
    <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback)
            throws E;

    /**
     * 一个简单的有状态重试。执行提供的 {@link RetryCallback}，并通过 {@link DefaultRetryState} 标识本次重试的目标对象。
     * <p>
     * 回调抛出的异常总是会被立即传播，因此我们需要RetryState来识别之前的重试操作（如果有）—> 因此RetryState是必需的。
     * 通常的用法是在事务中使用此方法，如果回调失败，那么会立即传播异常，从而可能会使事务失效。
     * <p>
     * 配置细节请参见具体实现。
     *
     * @param retryCallback {@link RetryCallback} 实例
     * @param retryState    {@link RetryState} 实例
     * @param <T>           返回值类型
     * @param <E>           抛出异常的类型
     * @return 成功调用 {@link RetryCallback} 时返回的值
     * @throws E                       由 {@link RetryCallback} 抛出的任何 {@link Exception}。
     * @throws ExhaustedRetryException 如果该状态的最后一次尝试已被用尽
     */
    <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RetryState retryState)
            throws E, ExhaustedRetryException;


    /**
     * 一个带有恢复路径的有状态重试。执行提供的 {@link RetryCallback}，当重试耗尽时回退到 {@link RecoveryCallback}，
     * 并通过 {@link DefaultRetryState} 标识本次重试的目标对象。
     *
     * @param recoveryCallback {@link RecoveryCallback} 实例
     * @param retryState       {@link RetryState} 实例
     * @param retryCallback    {@link RetryCallback} 实例
     * @param <T>              返回值类型
     * @param <E>              异常类型
     * @return 成功调用 {@link RetryCallback} 时返回的值，重试失败时返回 {@link RecoveryCallback} 的值。
     * @throws E 在重试失败时由 {@link RetryCallback} 抛出的任何 {@link Exception}。
     * @see #execute(RetryCallback, RetryState)
     */
    <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback,
                                       RetryState retryState) throws E;

}
