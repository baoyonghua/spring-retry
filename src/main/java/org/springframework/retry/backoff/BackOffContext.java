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

import java.io.Serializable;

/**
 * 用于跟踪单次重试操作中各次退避调用状态的上下文接口。
 * <p>
 * 对于需要在重试过程中记录状态的 {@link BackOffPolicy} 实现，可以使用 {@link BackOffContext} 来存储这些状态。
 * 例如，{@link ExponentialBackOffPolicy} 会在 {@link BackOffContext} 中存储当前的退避时间，以便在每次退避调用时递增该时间。
 * </p>
 * 而对于不需要记录状态的 {@link BackOffPolicy} 实现，对于其{@link BackOffPolicy#start(RetryContext)}方法可以返回`null`，来
 * 代表其不需要使用上下文来存储退避状态，因为通常对于这些{@link BackOffPolicy} 实现来说，每次的退避调用都是相同的，都可能会暂停相同的时间。
 *
 * @author Rob Harrop
 * @since 2.1
 */
public interface BackOffContext extends Serializable {

}
