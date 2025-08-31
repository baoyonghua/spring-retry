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

package org.springframework.retry;

import org.springframework.core.AttributeAccessor;
import org.springframework.lang.Nullable;

/**
 * 重试上下文
 * 对正在进行的重试操作提供低层级访问。通常客户端不需要使用，但可以用于改变重试的过程，例如强制提前终止。
 *
 * @author Dave Syer
 * @author Emanuele Ivaldi
 * @author Marcin Zajączkowski
 */
public interface RetryContext extends AttributeAccessor {

    /**
     * 重试上下文属性名称用于报告关键指标。它可用于报告目的，例如在重试监听器中，以累积有关性能数据。
     */
    String NAME = "context.name";

    /**
     * 重试上下文中状态键的属性名称。可用于通过其上下文识别有状态的重试。
     */
    String STATE_KEY = "context.state";

    /**
     * 如果上下文已关闭，则重试上下文中的该属性为非空（且为真）。
     */
    String CLOSED = "context.closed";

    /**
     * 如果采取了恢复路径，则重试上下文中该属性为非空（且为真）。
     */
    String RECOVERED = "context.recovered";

    /**
     * 如果重试次数已用完，则重试上下文该属性为非空（且为真）。
     */
    String EXHAUSTED = "context.exhausted";

    /**
     * 如果异常不可恢复，则重试上下文该属性为非空（且为真）。
     */
    String NO_RECOVERY = "context.no-recovery";

    /**
     * 表示在失败前提供最大尝试次数的策略的最大尝试次数。对于其他策略，返回的值为 `RetryPolicy.NO_MAXIMUM_ATTEMPTS_SET`。
     */
    String MAX_ATTEMPTS = "context.max-attempts";

    /**
     * 向框架发出信号，表示不应再重试当前的`RetryCallback`。
     */
    void setExhaustedOnly();

    /**
     * 用于访问已耗尽标志的公共访问器 {@link #setExhaustedOnly（）}。
     *
     * @return true if the flag has been set.
     */
    boolean isExhaustedOnly();

    /**
     * 如果重试是嵌套的，则为父上下文提供访问器。
     *
     * @return the parent or null if there is none.
     */
    @Nullable
    RetryContext getParent();

    /**
     * 计数重试次数。在首次尝试前，该计数器为0，在首次尝试及后续尝试前，应相应地增加计数。
     *
     * @return the number of retries.
     */
    int getRetryCount();

    /**
     * 引发当前重试的异常对象的访问器。
     *
     * @return 最后一个引发重试的异常，也可能是 null。
     * 如果这是第一次尝试且成功完成，或者如果包含的策略决定不提供它（例如，出于内存使用考虑），则该值将为 null。
     */
    @Nullable
    Throwable getLastThrowable();

}
