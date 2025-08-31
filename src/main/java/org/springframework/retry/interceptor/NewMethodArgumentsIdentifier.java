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
 * 策略接口，用于区分新参数和已被处理过的参数，
 * 例如通过检查消息标志来实现。
 *
 * @author Dave Syer
 */
public interface NewMethodArgumentsIdentifier {

	/**
     * 检查参数并判断它们是否从未被处理过。如果无法确定，最安全的选择是返回 false。
     * @param args 当前方法参数。
     * @return 如果已知该项从未被处理过，则返回 true。
	 */
	boolean isNew(Object[] args);

}
