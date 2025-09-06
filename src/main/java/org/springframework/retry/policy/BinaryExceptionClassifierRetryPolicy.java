/*
 * Copyright 2006-2019 the original author or authors.
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

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

/**
 *
 * 一个基于 {@link BinaryExceptionClassifier} 的重试策略。
 * 通常，二元分类对于重试场景已经足够。如果你需要更灵活的分类，请使用 {@link ExceptionClassifierRetryPolicy}。
 *
 * @author Aleksandr Shamukov
 */
@SuppressWarnings("serial")
public record BinaryExceptionClassifierRetryPolicy(
        BinaryExceptionClassifier exceptionClassifier) implements RetryPolicy {


    @Override
    public boolean canRetry(RetryContext context) {
        Throwable t = context.getLastThrowable();
        return t == null || exceptionClassifier.classify(t);
    }

    @Override
    public void close(RetryContext status) {
    }

    @Override
    public void registerThrowable(RetryContext context, Throwable throwable) {
        RetryContextSupport simpleContext = ((RetryContextSupport) context);
        simpleContext.registerThrowable(throwable);
    }

    @Override
    public RetryContext open(RetryContext parent) {
        return new RetryContextSupport(parent);
    }

}
