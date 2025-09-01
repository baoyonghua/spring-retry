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
package org.springframework.retry.interceptor;

/**
 * 允许通过唯一键标识和标记方法参数的接口。
 *
 * @author Dave Syer
 */
public interface MethodArgumentsKeyGenerator {

    /**
     * 获取可用于在多次调用之间缓存并随后识别该项的唯一标识符。
     *
     * @param item 当前方法参数（如果没有参数则可能为 null）。
     * @return 唯一标识符。
     */
    Object getKey(Object[] item);

}
