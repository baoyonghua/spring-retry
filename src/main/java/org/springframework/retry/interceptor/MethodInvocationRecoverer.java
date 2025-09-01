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
 * 当处理某个重试操作失败时用于恢复操作的策略接口。
 *
 * @param <T> the return type
 * @author Dave Syer
 */
public interface MethodInvocationRecoverer<T> {

    /**
     * 优雅地从错误中恢复。如果处理某个条目时抛出了意外异常，客户端可以调用此方法。
     * 调用者可以根据返回值决定是否采取更多补救措施，或抛出异常。
     *
     * @param args  失败的方法调用参数。
     * @param cause 导致恢复的失败原因。
     * @return 返回给调用者的值
     */
    T recover(Object[] args, Throwable cause);

}
