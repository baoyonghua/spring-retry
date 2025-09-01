/*
 * Copyright 2006-2024 the original author or authors.
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

package org.springframework.retry.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.lang.Nullable;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryOperations;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 针对可重试的 Spring AOP 反射 `MethodInvocation` 的回调类，可通过 {@link RetryOperations} 进行重试。
 * <p>
 * 在具体的 {@link org.springframework.retry.RetryListener} 实现中，
 * 可以分析 `MethodInvocation`，以便提供关于被调用方法及其参数值的洞察，这些信息可用于监控目的。
 *
 * @param <T> 回调返回的对象类型
 * @param <E> 声明可能抛出的异常类型
 * @author Marius Grama
 * @author Artem Bilan
 * @see StatefulRetryOperationsInterceptor
 * @see RetryOperationsInterceptor
 * @see org.springframework.retry.listener.MethodInvocationRetryListenerSupport
 * @since 1.3
 */
public abstract class MethodInvocationRetryCallback<T, E extends Throwable> implements RetryCallback<T, E> {

    /**
     * 要重试的反射方法调用实例。
     */
    protected final MethodInvocation invocation;

    /**
     * 用于统计报告的唯一标签
     * <p>
     * 如果在构造函数中未提供标签，则默认为方法的完全限定名称。
     * </p>
     */
    protected final String label;

    /**
     * Constructor for the class.
     *
     * @param invocation the method invocation
     * @param label      a unique label for statistics reporting.
     */
    public MethodInvocationRetryCallback(MethodInvocation invocation, @Nullable String label) {
        this.invocation = invocation;
        if (StringUtils.hasText(label)) {
            this.label = label;
        } else {
            this.label = ClassUtils.getQualifiedMethodName(invocation.getMethod());
        }
    }

    public MethodInvocation getInvocation() {
        return this.invocation;
    }

    @Override
    public String getLabel() {
        return this.label;
    }

}
