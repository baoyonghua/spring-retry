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
 * 一个回调接口
 * <p>
 * 它用来表示一个操作，这个操作可能会失败并且需要重试。
 * 在具体实现中，需要使用{@link RetryOperations} 进行重试。
 *
 * @param <T> 回调函数返回的对象的类型。
 * @param <E> RetryCallback可能会被抛出异常的类型
 * @author Rob Harrop
 * @author Dave Syer
 * @author Artem Bilan
 */
public interface RetryCallback<T, E extends Throwable> {


    /**
     * 执行一个带有重试语义的操作。操作通常应当是幂等的，但实现时也可以选择在重试时实现补偿语义。
     *
     * @param context 当前的重试上下文。
     * @return 成功操作的结果。
     * @throws E 如果处理失败会抛出类型为E的异常
     */
    T doWithRetry(RetryContext context) throws E;

    /**
     * 用于区分业务操作中重试的此回调的逻辑标识符。
     *
     * @return 此回调函数的标识符
     * @since 2.0.6
     */
    default String getLabel() {
        return null;
    }

}
