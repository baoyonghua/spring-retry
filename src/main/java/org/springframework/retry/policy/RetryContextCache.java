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

package org.springframework.retry.policy;

import org.springframework.retry.RetryContext;

/**
 * 用于有状态重试策略在存储和检索 {@link RetryContext} 实例时使用的RetryContext缓存接口
 * <p>
 * 调用方不应传入 null 键，但如果传入，具体实现可以选择丢弃该上下文（null 键表示“无信息”），而不是保存它。
 * </p>
 * <p>
 * 对于RetryContext所对应的唯一键，是由 {@link org.springframework.retry.RetryState}生成的
 * </p>
 *
 * @author Dave Syer
 * @see MapRetryContextCache
 */
public interface RetryContextCache {

    /**
     * 根据给定的键获取 {@link RetryContext} 实例
     *
     * @param key
     * @return
     */
    RetryContext get(Object key);

    /**
     * 缓存给定键的 {@link RetryContext} 实例
     *
     * @param key
     * @param context
     * @throws RetryCacheCapacityExceededException
     */
    void put(Object key, RetryContext context) throws RetryCacheCapacityExceededException;

    /**
     * 从缓存中移除给定键的 {@link RetryContext} 实例
     *
     * @param key
     */
    void remove(Object key);

    /**
     * 检查缓存中是否存在给定键的 {@link RetryContext} 实例
     *
     * @param key
     * @return
     */
    boolean containsKey(Object key);

}
