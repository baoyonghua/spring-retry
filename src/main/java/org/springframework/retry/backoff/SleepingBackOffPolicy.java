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

/**
 * 一个混入了{@link BackOffPolicy}的接口，用于表示具体的实现在退避时会进行休眠。
 *
 * @param <T> the type of the policy itself
 */
public interface SleepingBackOffPolicy<T extends SleepingBackOffPolicy<T>> extends BackOffPolicy {

    /**
     * 克隆该{@link BackOffPolicy}并返回一个使用传入的sleeper的新{@link BackOffPolicy}。
     *
     * @param sleeper 每当退避策略休眠时将被调用的目标
     * @return 该策略的一个克隆，后续所有的休眠操作都将通过传入的sleeper处理
     */
    T withSleeper(Sleeper sleeper);

}
