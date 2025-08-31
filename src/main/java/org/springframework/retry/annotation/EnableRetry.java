/*
 * Copyright 2012-2023 the original author or authors.
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

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 全局启用 Spring Bean 中 <code>@Retryable</code> 注解的功能。如果在上下文中的任意
 * <code>@Configuration</code> 上声明了该注解，则包含<code>@Retryable</code>注解的方法所属的 Bean 会被代理，
 * 并根据注解中的元数据处理重试逻辑。
 *
 * @author Dave Syer
 * @author Yanming Zhou
 * @author Ruslan Stelmachenko
 * @since 1.1
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@Import(RetryConfiguration.class)
@Documented
public @interface EnableRetry {

    /**
     * 指示是否创建基于子类（CGLIB）的代理，而不是标准的 Java 接口代理。默认值为 {@code false}。
     *
     * @return 是否对类进行代理
     */
    @AliasFor(annotation = EnableAspectJAutoProxy.class)
    boolean proxyTargetClass() default false;

    /**
     * 指定 {@link RetryConfiguration} AOP <b>通知</b> 应该被应用的顺序。
     * <p>
     * 默认值为 {@code Ordered.LOWEST_PRECEDENCE - 1}，以确保该通知会在其他顺序为 {@link Ordered#LOWEST_PRECEDENCE} 的通知之前进行应用
     * （例如负责 {@code @Transactional} 行为的通知）。
     * <pre>
     *  假设你有一个方法同时使用了 `@Retryable` 和 `@Transactional`：
     *  &#064;Retryable
     *  &#064;Transactional
     *  public void doSomething() {
     *     // 业务逻辑
     *  }
     *
     *  如果 `order` 设置为默认值，那么重试逻辑会在事务逻辑之前应用。
     *  如果你想让事务逻辑先于重试逻辑执行，可以通过如下方式调整顺序：
     *  <code>@EnableRetry(order  = Ordered.HIGHEST_PRECEDENCE)</code>
     *  这样，重试切面的优先级就会更高，先于事务切面执行
     * </pre>
     */
    int order() default Ordered.LOWEST_PRECEDENCE - 1;

}
