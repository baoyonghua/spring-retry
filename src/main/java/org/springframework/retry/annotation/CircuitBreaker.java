/*
 * Copyright 2016-2024 the original author or authors.
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

package org.springframework.retry.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 用于标注在需要进行断路保护的方法上
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @since 1.2
 *
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Retryable(stateful = true)
public @interface CircuitBreaker {

    /**
     * 可重试的异常类型。默认为空（如果 excludes 也为空，则所有异常都会被重试）。
     *
     * @return 需要重试的异常类型
     * @deprecated 建议使用 {@link #retryFor()} 代替
     */
    @AliasFor(annotation = Retryable.class)
    @Deprecated
    Class<? extends Throwable>[] value() default {};

    /**
     * 可重试的异常类型。默认为空（如果 excludes 也为空，则所有异常都会被重试）。
     *
     * @return 需要重试的异常类型
     * @deprecated 建议使用 {@link #retryFor()} 代替
     */
    @AliasFor(annotation = Retryable.class)
    @Deprecated
    Class<? extends Throwable>[] include() default {};

    /**
     * 可重试的异常类型。默认为空（如果 noRetryFor 也为空，则所有异常都会被重试）。
     *
     * @return 需要重试的异常类型
     * @since 2.0
     */
    @AliasFor(annotation = Retryable.class)
    Class<? extends Throwable>[] retryFor() default {};

    /**
     * 不可重试的异常类型。默认为空（如果 includes 也为空，则所有异常都会被重试）。
     * 如果 includes 为空但 excludes 不为空，则会重试所有未被排除的异常。
     *
     * @return 不需要重试的异常类型
     * @deprecated 建议使用 {@link #noRetryFor()} 代替。
     */
    @Deprecated
    @AliasFor(annotation = Retryable.class)
    Class<? extends Throwable>[] exclude() default {};

    /**
     * 不可重试的异常类型。默认为空（如果 retryFor 也为空，则所有异常都会被重试）。
     * 如果 retryFor 为空但 excludes 不为空，则会重试所有未被排除的异常。
     *
     * @return 不需要重试的异常类型
     * @since 2.0
     */
    @AliasFor(annotation = Retryable.class)
    Class<? extends Throwable>[] noRetryFor() default {};

    /**
     * 不可恢复的异常类型；这些异常会直接抛给调用者（如果也在 {@link #noRetryFor()} 中则会立即抛出），不会调用任何恢复器。
     * 默认为空。
     *
     * @return 不需要重试的异常类型
     * @since 2.0
     */
    @AliasFor(annotation = Retryable.class)
    Class<? extends Throwable>[] notRecoverable() default {};

    /**
     * @return 最大尝试次数（包括首次失败），默认为3。
     */
    @AliasFor(annotation = Retryable.class)
    int maxAttempts() default 3;

    /**
     * @return 一个用于计算最大尝试次数（包括首次失败）的表达式，默认为3。
     * 会覆盖 {@link #maxAttempts()}。使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则在运行时求值。
     * @since 1.2.3
     */
    @AliasFor(annotation = Retryable.class)
    String maxAttemptsExpression() default "";

    /**
     * 用于CircuitBreaker的唯一标签，用于报告和状态管理。默认为声明该注解的方法签名。
     *
     * @return 电路的标签
     */
    @AliasFor(annotation = Retryable.class)
    String label() default "";

    /**
     * 如果CircuitBreaker保持打开状态超过此超时时间，则会在下一次调用时重置，以便下游组件有机会再次响应。
     *
     * @return 打开CircuitBreaker后重置前的超时时间（毫秒），默认值为20000
     */
    long resetTimeout() default 20000;

    /**
     * 如果CircuitBreaker保持打开状态超过此超时时间，则会在下一次调用时重置，
     * 以便下游组件有机会再次响应。会覆盖 {@link #resetTimeout()}。
     * 使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则在运行时求值。
     *
     * @return 打开CircuitBreaker后重置前的超时时间（毫秒），无默认值。
     * @since 1.2.3
     */
    String resetTimeoutExpression() default "";

    /**
     * 当在此超时时间内达到 {@link #maxAttempts()} 次失败时，断路器会自动打开，阻止对下游组件的访问。
     *
     * @return 关闭状态的断路器在被打开前的超时时间（毫秒），默认值为5000
     */
    long openTimeout() default 5000;

    /**
     * 当在此超时时间内达到 {@link #maxAttempts()} 次失败时，断路器会自动打开，阻止对下游组件的访问。
     * 会覆盖 {@link #openTimeout()}。使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则在运行时求值。
     *
     * @return 关闭状态的断路器在被打开前的超时时间（毫秒），无默认值。
     * @since 1.2.3
     */
    String openTimeoutExpression() default "";

    /**
     * 指定一个表达式，在 {@code SimpleRetryPolicy.canRetry()} 返回 true 后进行评估 —> 可用于有条件地抑制重试。
     * 仅在抛出异常后调用。表达式的根对象为最后一个 {@code Throwable}。也可以引用上下文中的其他 bean。
     * <p>
     * 两个示例如下：
     * <pre class=code>
     *  // 如果异常消息包含 "you can retry this" 则进行重试
     *  {@code "message.contains('you can retry this')"}。
     * </pre>
     * <p>
     * 以及
     *
     * <pre class=code>
     *  // 调用名为 someBean 的 bean 的 shouldRetry 方法，并将根异常作为参数传递
     *  {@code "@someBean.shouldRetry(#root)"}。
     * </pre>
     *
     * @return 表达式
     * @since 1.2.3
     */
    @AliasFor(annotation = Retryable.class)
    String exceptionExpression() default "";

    /**
     * 设为 {@code true} 时，在重试耗尽后不会将最后一个异常包装为
     * {@link org.springframework.retry.ExhaustedRetryException}。
     *
     * @return 是否在重试耗尽时将最后一个异常包装为
     * {@link org.springframework.retry.ExhaustedRetryException} 的布尔标志
     * @since 2.0.6
     */
    boolean throwLastExceptionOnExhausted() default false;

    /**
     * 在此类中用于恢复的方法名称。该方法必须使用 {@link Recover} 注解标记。
     *
     * @return 恢复方法的名称
     * @since 2.0.9
     */
    @AliasFor(annotation = Retryable.class)
    String recover() default "";

}
