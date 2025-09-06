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

/**
 * 重试状态，它用于有状态重试的场景
 * <p>
 * 有状态重试的特点是需要识别正在处理的重试，因此该接口主要用于在失败的重试之间提供一个缓存键。
 * RetryState负责返回一个唯一键用于标识各个{@link RetryContext}，同时此唯一键也会用作于RetryContextCache中的key，
 * 以便在重试过程中检索和更新RetryContext
 * </p>
 * <p>
 * 它还为 {@link RetryOperations} 提供了一些优化提示，
 * 比如避免不必要的缓存查找操作{@link #isForceRefresh()}，
 * 以及在不需要回滚时切换为无状态重试{@link #rollbackFor(Throwable)}
 *
 * @author Dave Syer
 */
public interface RetryState {

    /**
     * 表示重试状态的键。有状态重试的特点是需要识别正在处理的项目，因此该值会在失败的重试之间作为缓存键使用。
     *
     * @return 此状态所代表的键
     */
    Object getKey();

    /**
     * 指示是否可以避免缓存查找。如果在重试之前已知该键{@link #getKey()}是新的（即以前从未见过, 在之前不可能是失败的）
     * 那么如果此标志为 true，则可以避免进行缓存查找。
     *
     * @return 如果该状态不需要显式检索键，则返回 true
     */
    boolean isForceRefresh();

    /**
     * 检查此异常是否需要回滚。
     * <p>
     * 默认始终为 true，这是一种保守做法，即当有状态重试遇到了任何异常都会选择抛出异常以进行回滚
     * <p>
     * 该方法为在有状态重试切换为无状态重试提供了建议，当有状态重试遇到不需要回滚的异常时那么此方法应该返回false,这样有状态重试就转换为了无状态重试
     * <p>
     * 例如，当有状态重试遇到了不需要进行回滚的异常时，那么并不会抛出异常，而是选择继续在当前线程栈上进行重试，
     * 而对于默认情况而言，当有状态重试遇到任何异常时都会抛出异常以进行回滚，也就是说每次重试都在一个新的事务中进行
     *
     * @param exception 导致重试尝试失败的异常
     * @return 如果该异常应导致回滚则返回 true
     */
    boolean rollbackFor(Throwable exception);

}