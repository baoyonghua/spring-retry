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

import java.io.Serializable;

/**
 * 重试策略接口
 * <p>
 * {@link RetryPolicy} 负责分配和管理 {@link RetryOperations} 所需的所有资源 {@link RetryContext}
 * {@link RetryPolicy} 使得重试操作能够感知其上下文 [RetryContext]。
 * 上下文[RetryContext]可以是SpringRetry框架的内部，例如支持嵌套重试，
 * 也可以是外部的，{@link RetryPolicy} 为不同平台的外部上下文提供统一的 API。
 *
 * @author Dave Syer
 * @author Emanuele Ivaldi
 */
public interface RetryPolicy extends Serializable {

    /**
     * 当策略没有提供失败前最大尝试次数时，由 {@link RetryPolicy#getMaxAttempts（）} 返回的值。
     */
    int NO_MAXIMUM_ATTEMPTS_SET = -1;

    /**
     * @param context the current retry status
     * @return true if the operation can proceed
     */
    boolean canRetry(RetryContext context);

    /**
     * 获取重试操作所需的资源。传入回调对象是为了可以使用标记接口，并且管理器可以与回调协作，以在状态令牌中设置一些状态。
     *
     * @param parent 如果是嵌套重试，则为父上下文。
     * @return 针对该策略特定的 {@link RetryContext} 对象。
     */
    RetryContext open(RetryContext parent);

    /**
     * 关闭资源
     *
     * @param context a retry status created by the {@link #open(RetryContext)} method of
     *                this policy.
     */
    void close(RetryContext context);

    /**
     * 在回调失败之后，每次重试尝试都会调用一次，用于将异常注册到{@link RetryContext}中
     *
     * @param context   the current status object.
     * @param throwable the exception to throw
     */
    void registerThrowable(RetryContext context, Throwable throwable);

    /**
     * 用于判断该策略是否有固定的最大重试次数
     *
     * @return 如果策略未提供固定的最大重试次数则返回 -1，否则返回最大重试次数
     */
    default int getMaxAttempts() {
        return NO_MAXIMUM_ATTEMPTS_SET;
    }

}
