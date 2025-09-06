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

/**
 * {@link BackOffPolicy} 实现的简单基类，它是无状态的, 在多次调用之间不维护任何状态。
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public abstract class StatelessBackOffPolicy implements BackOffPolicy {

    /**
     * 直接调用 {@link #doBackOff（）} 方法，而不传递所需的 {@link BackOffContext} 参数，因为对于无状态实现来说 {@link BackOffContext} 是不必要的。
     * <p>
     * 因为对于无状态的回避策略而言，每次的退避调用都是相同的，都可能会暂停相同的时间。
     */
    @Override
    public final void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
        doBackOff();
    }

    /**
     * Returns '<code>null</code>'. Subclasses can add behaviour, e.g. initial sleep
     * before first attempt.
     * 返回 '<code>null</code>'。代表其不需要使用上下文来存储退避状态。因为对于无状态的回避策略而言，每次的退避调用都是相同的，都可能会暂停相同的时间。
     * <p>
     * 子类可以添加行为，例如在第一次尝试之前进行一次初始休眠。
     */
    @Override
    public BackOffContext start(RetryContext status) {
        return null;
    }

    /**
     * 子类应实现此方法以执行实际的退避操作。
     *
     * @throws BackOffInterruptedException if the backoff is interrupted
     */
    protected abstract void doBackOff() throws BackOffInterruptedException;

}
