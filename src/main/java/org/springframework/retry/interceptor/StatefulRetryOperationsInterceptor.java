/*
 * Copyright 2006-2025 the original author or authors.
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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.classify.Classifier;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.RetryState;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * 该拦截器（`MethodInterceptor`）可用于在服务方法调用失败时自动重试。
 * 服务方法的参数被视为在调用失败时需要记住的条目，因此重试操作是有状态的
 * 失败的条目会通过其唯一键（通过 `MethodArgumentsKeyGenerator`）进行跟踪，直到重试耗尽，当重试耗尽时会调用 `MethodInvocationRecoverer` 进行恢复
 * <p>
 * 主要的使用场景是服务方法具有事务性，即: 拦截器链中有事务拦截器。在这种情况下，重试（以及重试耗尽时的恢复）总是在新事务中进行。
 * <p>
 * 注入的 `RetryOperations` 用于控制重试次数。默认情况下，它将根据 `RetryTemplate` 的默认设置进行固定次数的重试。
 *
 * @author Dave Syer
 * @author Gary Russell
 */
public class StatefulRetryOperationsInterceptor implements MethodInterceptor {

    private transient final Log logger = LogFactory.getLog(getClass());

    /**
     * 用于生成唯一键的 `MethodArgumentsKeyGenerator`，以标识相同的重复操作
     */
    private MethodArgumentsKeyGenerator keyGenerator;

    /**
     * 当重试耗尽时用于恢复的 `MethodInvocationRecoverer`
     */
    private MethodInvocationRecoverer<?> recoverer;

    /**
     * 用于识别方法参数是否为新参数的 `NewMethodArgumentsIdentifier`
     */
    private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

    /**
     * 用于执行重试操作的 `RetryOperations` 实例
     */
    private RetryOperations retryOperations;

    private String label;

    /**
     * 用于分类异常以确定是否应回滚重试状态的分类器。默认值为 null（表示对所有异常进行回滚）。
     */
    private Classifier<? super Throwable, Boolean> rollbackClassifier;

    /**
     * 是否使用由{@link #keyGenerator}生成的原始键。
     * 仅当键在所有情况下都保证唯一时，才应将其设置为 true。
     * <p>
     * 默认值为 false。即：使用包含调用元数据的复合键。这样可以防止不同方法使用相同参数值时发生冲突。
     */
    private boolean useRawKey;

    public StatefulRetryOperationsInterceptor() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new NeverRetryPolicy());
        this.retryOperations = retryTemplate;
    }

    public void setRetryOperations(RetryOperations retryTemplate) {
        Assert.notNull(retryTemplate, "'retryOperations' cannot be null.");
        this.retryOperations = retryTemplate;
    }

    /**
     * 用于 {@link MethodInvocationRecoverer} 的公共设置器，用于在重试次数用尽时使用。
     * 该恢复器应能够返回与目标对象类型相同的对象，因为它的返回值将在恢复情况下用于返回给调用者。
     *
     * @param recoverer the {@link MethodInvocationRecoverer} to set
     */
    public void setRecoverer(MethodInvocationRecoverer<?> recoverer) {
        this.recoverer = recoverer;
    }

    /**
     * 用于分类异常以确定是否应回滚重试状态的分类器。默认值为 null（表示对所有异常进行回滚）。
     *
     * @param rollbackClassifier the rollbackClassifier to set
     */
    public void setRollbackClassifier(Classifier<? super Throwable, Boolean> rollbackClassifier) {
        this.rollbackClassifier = rollbackClassifier;
    }

    public void setKeyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
        this.keyGenerator = keyGenerator;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * 这是 `NewMethodArgumentsIdentifier` 的公共 setter 方法。只有在你能够检查被拦截方法的参数，并确定这些参数是否“从未被处理过”时，才需要设置这个属性。
     * 也就是说，它用于判断当前方法参数是否是“新”的（即之前没有处理过），从而决定是否需要重置重试状态。
     * <p>
     * 示例说明：
     * <pre class="code">
     *     假设你有一个Service方法，每次传入的参数是订单号。你希望只有在订单号是新的（之前没处理过）时，才重置重试状态，否则继续之前的重试。
     *     此时你可以实现一个 `NewMethodArgumentsIdentifier`，比如：
     *     public class OrderIdNewIdentifier implements NewMethodArgumentsIdentifier {
     *         private Set<Object> processedOrderIds = new HashSet<>();
     *
     *         &#064;Override
     *         public boolean isNew(Object[] args) {
     *             Object orderId = args[0];
     *             if (processedOrderIds.contains(orderId)) {
     *                 return false;
     *             }
     *             processedOrderIds.add(orderId);
     *             return true;
     *         }
     *     }
     * 然后在配置 `StatefulRetryOperationsInterceptor` 时注入：
     *      StatefulRetryOperationsInterceptor interceptor = new StatefulRetryOperationsInterceptor();
     *      interceptor.setNewItemIdentifier(new OrderIdNewIdentifier());
     * 这样，只有当订单号是新的时，才会重置重试状态。否则会继续之前的重试记录。
     * </pre>
     *
     * @param newMethodArgumentsIdentifier the {@link NewMethodArgumentsIdentifier} to set
     */
    public void setNewItemIdentifier(NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
        this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
    }

    /**
     * 设为 true 时，将使用键生成器生成的原始键。仅当键在所有情况下都能保证唯一时才应设置为 true。
     * 当为 false 时，将使用包含调用元数据的复合键。默认值：false。
     *
     * @param useRawKey the useRawKey to set.
     */
    public void setUseRawKey(boolean useRawKey) {
        this.useRawKey = useRawKey;
    }

    /**
     * 使用提供的策略和其他辅助工具，将方法调用包装在有状态重试中。
     * <p>
     * 如果发生失败，异常通常会被重新抛出。唯一不会重新抛出的情况是重试耗尽并进入恢复路径时
     * （如果提供了 {@link MethodInvocationRecoverer}，则会通过它进行恢复）。
     * 在这种情况下，方法调用返回的值将是恢复器返回的值（因此恢复器的返回类型应与被拦截方法相同）。
     *
     * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
     * @see MethodInvocationRecoverer#recover(Object[], Throwable)
     */
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing proxied method in stateful retry: " + invocation.getStaticPart() + "("
                    + ObjectUtils.getIdentityHexString(invocation) + ")");
        }

        Object[] args = invocation.getArguments();
        Object defaultKey = Arrays.asList(args);
        if (args.length == 1) {
            defaultKey = args[0];
        }

        // 通过 keyGenerator 来创建唯一键，用于标识 RetryState
        Object key = createKey(invocation, defaultKey);
        // 只有在 newMethodArgumentsIdentifier 存在且识别到参数为新参数时，才强制刷新重试状态
        boolean forceRefresh = this.newMethodArgumentsIdentifier != null && this.newMethodArgumentsIdentifier.isNew(args);
        // 对于有状态重试，需要创建一个 RetryState 来跟踪重试状态
        RetryState retryState = new DefaultRetryState(key, forceRefresh, this.rollbackClassifier);

        // 将方法调用包装在有状态重试回调中，以便于在必要时进行重试
        StatefulMethodInvocationRetryCallback callback = new StatefulMethodInvocationRetryCallback(invocation, label);
        // 如果提供了恢复器，则创建一个恢复回调，用于在重试耗尽时进行恢复
        ItemRecovererCallback recovererCallback = this.recoverer != null ? new ItemRecovererCallback(args, this.recoverer) : null;
        // 调用目标方法, 并在必要时进行重试
        Object result = this.retryOperations.execute(callback, recovererCallback, retryState);

        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Exiting proxied method in stateful retry with result: (" + result + ")");
        }

        return result;

    }

    /**
     * 通过 `keyGenerator` 来创建唯一键，用于标识{@link RetryState}
     *
     * @param invocation
     * @param defaultKey
     * @return
     */
    private Object createKey(final MethodInvocation invocation, Object defaultKey) {
        Object generatedKey = defaultKey;
        if (this.keyGenerator != null) {
            generatedKey = this.keyGenerator.getKey(invocation.getArguments());
        }
        if (generatedKey == null) {
            // 如果存在keyGenerator，而它生成的key为空，那就意味着它确实不想进行重试。
            return null;
        }
        if (this.useRawKey) {
            return generatedKey;
        }

        // 创建复合key以避免冲突 -> generatedKey + label（默认为方法的完全限定名）
        String name = StringUtils.hasText(label) ? label : invocation.getMethod().toGenericString();
        return Arrays.asList(name, generatedKey);
    }

    /**
     * 默认的有状态的 `MethodInvocation` 重试回调实现。
     * <p>
     * 它包装了方法调用MethodInvocation
     * </p>
     *
     * @author Dave Syer
     */
    private static final class StatefulMethodInvocationRetryCallback
            extends MethodInvocationRetryCallback<Object, Throwable> {

        private StatefulMethodInvocationRetryCallback(MethodInvocation invocation, String label) {
            super(invocation, label);
        }

        @Override
        public Object doWithRetry(RetryContext context) throws Exception {
            context.setAttribute(RetryContext.NAME, label);
            try {
                return this.invocation.proceed();
            } catch (Exception | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * 默认的 `RecoveryCallback` 实现，它使用 `MethodInvocationRecoverer` 来恢复失败的条目。
     *
     * @author Dave Syer
     */
    private record ItemRecovererCallback(Object[] args, MethodInvocationRecoverer<?> recoverer)
            implements RecoveryCallback<Object> {

        /**
         * @param args the item that failed.
         */
        private ItemRecovererCallback(Object[] args, MethodInvocationRecoverer<?> recoverer) {
            this.args = args.clone();
            this.recoverer = recoverer;
        }

        @Override
        public Object recover(RetryContext context) {
            // 调用恢复器来进行恢复
            return this.recoverer.recover(this.args, context.getLastThrowable());
        }
    }
}
