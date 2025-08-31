/*
 * Copyright 2012-2024 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于方法调用的恢复处理器的注解。合适的恢复处理器方法的第一个参数应为 Throwable 类型（或其子类），
 * 并且返回值类型应与需要恢复的 <code>@Retryable</code> 方法相同。
 * Throwable 类型的第一个参数是可选的（但如果没有该参数，只有在没有其他方法匹配时才会被调用）。
 * 后续参数会按照失败方法的参数列表顺序进行填充。
 *
 * @author Dave Syer
 * @since 2.0
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Recover {

}
