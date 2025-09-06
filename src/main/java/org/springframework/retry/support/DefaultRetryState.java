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
package org.springframework.retry.support;

import org.springframework.classify.Classifier;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.RetryState;

/**
 * 默认的 {@link RetryState} 实现
 *
 * @author Dave Syer
 */
public class DefaultRetryState implements RetryState {

    /**
     * 用于标识此RetryState的键
     */
    final private Object key;

    /**
     * 如果已知该重试是一个全新的状态（之前不可能失败过），则为真。
     */
    final private boolean forceRefresh;

    /**
     * 回滚分类器。key为Throwable, value用于标识是否需要进行回滚, 如果提供的异常应导致回滚，则value为true。
     */
    final private Classifier<? super Throwable, Boolean> rollbackClassifier;

    /**
     * 创建一个 {@link DefaultRetryState}，代表新的重试状态
     *
     * @param key                用于标识此RetryState的键
     * @param forceRefresh       如果已知该重试是一个全新的状态（之前不可能失败过），则为真。
     * @param rollbackClassifier 回滚分类器。key为Throwable, value用于标识是否需要进行回滚, 如果提供的异常应导致回滚，则value为true。
     * @see RetryOperations#execute(RetryCallback, RetryState)
     * @see RetryOperations#execute(RetryCallback, RecoveryCallback, RetryState)
     */
    public DefaultRetryState(Object key, boolean forceRefresh,
                             Classifier<? super Throwable, Boolean> rollbackClassifier) {
        this.key = key;
        this.forceRefresh = forceRefresh;
        this.rollbackClassifier = rollbackClassifier;
    }

    /**
     * Defaults the force refresh flag to false.
     *
     * @param key                the key
     * @param rollbackClassifier the rollback {@link Classifier}
     * @see DefaultRetryState#DefaultRetryState(Object, boolean, Classifier)
     */
    public DefaultRetryState(Object key, Classifier<? super Throwable, Boolean> rollbackClassifier) {
        this(key, false, rollbackClassifier);
    }

    /**
     * Defaults the rollback classifier to null.
     *
     * @param key          the key
     * @param forceRefresh whether to force a refresh
     * @see DefaultRetryState#DefaultRetryState(Object, boolean, Classifier)
     */
    public DefaultRetryState(Object key, boolean forceRefresh) {
        this(key, forceRefresh, null);
    }

    /**
     * Defaults the force refresh flag (to false) and the rollback classifier (to null).
     *
     * @param key the key to use
     * @see DefaultRetryState#DefaultRetryState(Object, boolean, Classifier)
     */
    public DefaultRetryState(Object key) {
        this(key, false, null);
    }

    public Object getKey() {
        return key;
    }

    public boolean isForceRefresh() {
        return forceRefresh;
    }

    public boolean rollbackFor(Throwable exception) {
        if (rollbackClassifier == null) {
            return true;
        }
        return rollbackClassifier.classify(exception);
    }

    @Override
    public String toString() {
        return String.format("[%s: key=%s, forceRefresh=%b]", getClass().getSimpleName(), key, forceRefresh);
    }
}
