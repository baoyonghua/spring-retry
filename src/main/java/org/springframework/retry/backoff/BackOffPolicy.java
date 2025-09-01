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

package org.springframework.retry.backoff;

import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

/**
 * 用于控制单次 {@link RetryTemplate 重试操作} 中各次重试之间退避时间的策略接口。
 * <p>
 * 实现类应当是线程安全的，并且应为并发访问而设计。每个实现的配置也应当是线程安全的，但不必适用于高负载并发访问。
 * <p>
 * 对于每一组重试操作，都会调用 {@link #start} 方法来获取{@link BackOffContext}，
 * 且实现类可以返回特定实现的{@link BackOffContext}，用于在后续退避调用中跟踪状态。
 * 每次退避过程都会通过调用 {@link #backOff} 方法来实现退避操作
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public interface BackOffPolicy {

    /**
     * 开始一组新的退避操作。实现类可以选择在调用此方法时进行暂停，但通常会立即返回。
     *
     * @param context {@link RetryContext} 上下文，可能包含我们用来决定如何继续的信息。
     * @return 特定的 {@link BackOffContext}实现 或 `<code>null</code>`。
     */
    BackOffContext start(RetryContext context);

    /**
     * 以特定的方式进行退避/暂停。传入的 {@link BackOffContext} 对应于为某组重试操作通过调用 {@link #start} 创建的{@link BackOffContext}实例。
     *
     * @param backOffContext 由 {@link #start} 创建的 {@link BackOffContext}
     * @throws BackOffInterruptedException 如果退避操作被中断则抛出该异常。
     */
    void backOff(BackOffContext backOffContext) throws BackOffInterruptedException;

}
