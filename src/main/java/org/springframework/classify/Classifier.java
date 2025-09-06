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

package org.springframework.classify;

import java.io.Serializable;

/**
 * 分类器接口。最简单情况下，{@link Classifier} 就是将一种类型的对象映射为另一种类型的对象。
 * <p>
 * 注意，只有当参数类型本身可序列化时，实现类才能被序列化。
 *
 * @param <C> 需要分类的事物的类型
 * @param <T> 分类器的输出类型，将输入类型映射为输出类型
 * @author Dave Syer
 *
 */
public interface Classifier<C, T> extends Serializable {

    /**
     * 对给定的对象进行分类，并返回一个不同类型（可能是一个枚举类型）的对象。
     *
     * @param classifiable the input object. Can be null.
     * @return an object. Can be null, but implementations should declare if this is the
     * case.
     */
    T classify(C classifiable);

}
