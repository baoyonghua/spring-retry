/*
 * Copyright 2012-2022 the original author or authors.
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
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;

import java.lang.annotation.*;

/**
 * 该注解收集用于 {@link BackOffPolicy} 的元数据。特性如下：
 *
 * <ul>
 * <li>未显式设置时，默认固定延迟为 1000ms</li>
 * <li>仅设置 {@link #delay()} 时，退避为该值的固定延迟</li>
 * <li>当同时设置 {@link #delay()} 和 {@link #maxDelay()} 时，那么退避时间在这两个值之间均匀分布</li>
 * <li>同时设置 {@link #delay()}、{@link #maxDelay()} 和 {@link #multiplier()} 时，退避呈指数增长，直到最大值</li>
 * <li>如果还设置了 {@link #random()} 为true，则每次延迟的乘数会在 [1, multiplier-1] 区间内均匀分布</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Gary Russell
 * @author Aftab Shaikh
 * @since 1.1
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Backoff {

    /**
     * {@link #delay()} 的同义词。当 {@link #delay()} 非零时，此元素的值被忽略，否则取此元素的值。
     *
     * @return 延迟的毫秒数（默认 1000）
     */
    @AliasFor("delay")
    long value() default 1000;

    /**
     * 标准退避周期。在指数退避情况下用作初始值，在均匀退避情况下用作最小值。
     * 当该元素的值为 0 时，取 {@link #value()} 的值，否则取该元素的值并忽略 {@link #value()}的值。
     *
     * @return 初始或标准退避周期（毫秒，默认 1000）
     */
    @AliasFor("value")
    long delay() default 1000;

    /**
     * 重试之间的最大等待时间（毫秒）
     * 如果小于 {@link #delay()}，则会应用{@value ExponentialBackOffPolicy#DEFAULT_MAX_INTERVAL}作为默认值。
     *
     * @return 重试之间的最大延迟（默认值为0，表示忽略）。
     */
    long maxDelay() default 0;

    /**
     * 如果大于 1.0，则作为生成下一个退避延迟的乘数；任何小于或等于 1.0 的值都被视为 1.0，即固定延迟
     *
     * @return 用于计算下一个退避延迟的乘数（默认 0 = 忽略）
     */
    double multiplier() default 0;

    /**
     * 一个用于计算标准退避周期的表达式。在指数退避情况下用作初始值，而在均匀退避情况下用作最小值。
     * 会覆盖 {@link #delay()}。使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则会在运行时求值。
     *
     * @return 以毫秒为单位的初始或标准退避周期。
     * @since 1.2
     */
    String delayExpression() default "";

    /**
     * 一个用于计算重试之间最大等待时间（毫秒）的表达式。如果小于 {@link #delay()}，则会应用
     * {@value ExponentialBackOffPolicy#DEFAULT_MAX_INTERVAL} 作为默认值。
     * 会覆盖 {@link #maxDelay()}。使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则会在运行时求值。
     *
     * @return 重试之间的最大延迟
     * @since 1.2
     */
    String maxDelayExpression() default "";

    /**
     * 计算用于生成下一个退避延迟的乘数表达式；任何小于或等于 1.0 的值都被视为 1.0，即固定延迟。
     * 会覆盖 {@link #multiplier()}。使用 {@code #{...}} 可在初始化时进行一次性求值，省略定界符则会在运行时求值。
     *
     * @return 用于计算下一个退避延迟的乘数表达式
     */
    String multiplierExpression() default "";

    /**
     * 在指数退避情况下（{@link #multiplier()} &gt; 1.0），将此设置为 true 可使退避延迟带有抖动（jitter）进行随机化，
     * 此时<b>最大延迟</b>为上一次延迟乘以 multiplier，<b>最小延迟</b>为上一次延迟，且实际延迟分布在这两个值之间均匀分布。
     * <pre>
     * 这句话的意思是：在指数退避（multiplier &gt 1.0）时，如果将 `random` 设置为 `true`
     * 每次重试的延迟时间会在一定范围内随机分布（即带有“抖动”或 jitter），而不是严格按照上一次延迟乘以 multiplier 递增。
     * 这样可以避免多个重试任务同时发生，减少“惊群效应”。
     *
     * 假设你有如下重试注解：
     * &#064;Retryable(
     *         value = Exception.class,
     *         maxAttempts = 5,
     *         backoff = @Backoff(delay = 1000, multiplier = 2, random = true)
     * )
     * public void doSomething() {
     *      // 可能抛出异常的方法
     * }
     *
     * - 第一次重试延迟 1000ms
     * - 第二次重试延迟会在 1000ms 和 2000ms 之间随机
     * - 第三次重试延迟会在 2000ms 和 4000ms 之间随机
     * - 以此类推
     *
     * 如果 `random = false`，则每次延迟都是上一次延迟乘以 multiplier，延迟时间是确定的。设置为 `true` 后，延迟时间会在区间内随机，有助于分散重试压力。
     * </pre>
     *
     * @return 是否需要随机化的标志（默认 false）
     */
    boolean random() default false;

    /**
     * 计算并返回一个值。在指数退避情况下（{@link #multiplier()} &gt; 1.0），如果表达式的结果为 true 可使退避延迟带有抖动（jitter）进行随机化，
     * 此时<b>最大延迟</b>为上一次延迟乘以 multiplier，<b>最小延迟</b>为上一次延迟，且实际延迟分布在这两个值之间均匀分布。
     * 该表达式始终在初始化时进行求值。如果表达式返回 true，则使用{@link ExponentialRandomBackOffPolicy}，否则使用 {@link ExponentialBackOffPolicy}。
     *
     * @return 是否需要随机化的标志
     */
    String randomExpression() default "";

}
