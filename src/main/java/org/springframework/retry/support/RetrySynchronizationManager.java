/*
 * Copyright 2006-2023 the original author or authors.
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

package org.springframework.retry.support;

import org.springframework.lang.Nullable;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global variable support for retry clients. Normally it is not necessary for clients to
 * be aware of the surrounding environment because a {@link RetryCallback} can always use
 * the context it is passed by the enclosing {@link RetryOperations}. But occasionally it
 * might be helpful to have lower level access to the ongoing {@link RetryContext} so we
 * provide a global accessor here. The mutator methods ({@link #clear()} and
 * {@link #register(RetryContext)} should not be used except internally by
 * {@link RetryOperations} implementations.
 *
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public final class RetrySynchronizationManager {

    private RetrySynchronizationManager() {
    }

    private static final ThreadLocal<RetryContext> context = new ThreadLocal<>();

    private static final Map<Thread, RetryContext> contexts = new ConcurrentHashMap<>();

    /**
     * 是否将重试上下文存储在 ThreadLocal 中，默认为 true。
     * 设置为 false 时，重试上下文将存储在一个 Map 中，该 Map 以当前线程为键。
     * 当使用虚拟线程时，需要将此值设置为 false。
     */
    private static boolean useThreadLocal = true;

    /**
     * Set to false to store the context in a map (keyed by the current thread) instead of
     * in a {@link ThreadLocal}. Recommended when using virtual threads.
     *
     * @param use true to use a {@link ThreadLocal} (default true).
     * @since 2.0.3
     */
    public static void setUseThreadLocal(boolean use) {
        useThreadLocal = use;
    }

    /**
     * Return true if contexts are held in a ThreadLocal (default) rather than a Map.
     *
     * @return the useThreadLocal
     * @since 2.0.3
     */
    public static boolean isUseThreadLocal() {
        return useThreadLocal;
    }

    /**
     * Public accessor for the locally enclosing {@link RetryContext}.
     *
     * @return the current retry context, or null if there isn't one
     */
    @Nullable
    public static RetryContext getContext() {
        if (useThreadLocal) {
            return context.get();
        } else {
            return contexts.get(Thread.currentThread());
        }
    }

    /**
     * 注册重试上下文的方法，应该仅由 {@link RetryOperations} 实现类使用，以确保 {@link #getContext()} 总是返回正确的值。
     *
     * @param context 要注册的新重试上下文
     * @return 如果存在，则返回旧的上下文
     */
    @Nullable
    public static RetryContext register(RetryContext context) {
        RetryContext oldContext;
        if (useThreadLocal) {
            oldContext = getContext();
            RetrySynchronizationManager.context.set(context);
        } else {
            oldContext = contexts.get(Thread.currentThread());
            contexts.put(Thread.currentThread(), context);
        }
        return oldContext;
    }

    /**
     * Clear the current context at the end of a batch - should only be used by
     * {@link RetryOperations} implementations.
     *
     * @return the old value if there was one.
     */
    @Nullable
    public static RetryContext clear() {
        RetryContext value = getContext();
        RetryContext parent = value == null ? null : value.getParent();
        if (useThreadLocal) {
            RetrySynchronizationManager.context.set(parent);
        } else {
            if (parent != null) {
                contexts.put(Thread.currentThread(), parent);
            } else {
                contexts.remove(Thread.currentThread());
            }
        }
        return value;
    }

}
