/*
 * Copyright 2006-2023 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 一个可以考虑子类型的分类器
 * <p>
 * 可以根据与提供的类型映射的继承关系来对对象进行分类。
 * 如果要分类的对象是映射中的某个键，或是某个键的子类，则返回该键对应的值。否则，返回默认值（默认为 null）。
 *
 * @param <T> the type of the thing to classify
 * @param <C> the output of the classifier
 * @author Dave Syer
 * @author Gary Russell
 * @author Artem Bilan
 */
public class SubclassClassifier<T, C> implements Classifier<T, C> {

    /**
     * 实际的映射
     */
    private ConcurrentMap<Class<? extends T>, C> classified;

    /**
     * 当{@link #classified}中没有找到匹配的键时，返回的默认值
     */
    private C defaultValue;

    /**
     * Create a {@link SubclassClassifier} with null default value.
     */
    public SubclassClassifier() {
        this(null);
    }

    /**
     * Create a {@link SubclassClassifier} with supplied default value.
     *
     * @param defaultValue the default value
     */
    public SubclassClassifier(C defaultValue) {
        this(new HashMap<>(), defaultValue);
    }

    /**
     * Create a {@link SubclassClassifier} with supplied default value.
     *
     * @param defaultValue the default value
     * @param typeMap      the map of types
     */
    public SubclassClassifier(Map<Class<? extends T>, C> typeMap, C defaultValue) {
        super();
        this.classified = new ConcurrentHashMap<>(typeMap);
        this.defaultValue = defaultValue;
    }

    /**
     * Public setter for the default value for mapping keys that are not found in the map
     * (or their subclasses). Defaults to false.
     *
     * @param defaultValue the default value to set
     */
    public void setDefaultValue(C defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * 为{@link SubclassClassifier}设置一个映射。
     * 键是类型，这些类型及其所有的子类将会被映射到相应的值。最具体的类型将优先匹配。
     *
     * @param map a map from type to class
     */
    public void setTypeMap(Map<Class<? extends T>, C> map) {
        this.classified = new ConcurrentHashMap<>(map);
    }

    /**
     * The key is the type and this will be mapped along with all subclasses to the
     * corresponding value. The most specific types will match first.
     *
     * @param type   the type of the input object
     * @param target the target value for all such types
     */
    public void add(Class<? extends T> type, C target) {
        this.classified.put(type, target);
    }

    /**
     * 该方法会根据传入对象的类型，在类型映射表{@link #classified}中查找对应的值。
     * <p>
     * 如果找不到完全匹配的类型，则会继续查找其最近的父类是否有映射。如果找到，则返回该父类对应的值。
     * 主要用于异常分类或类型分类，支持子类优先匹配，找不到则返回默认值。
     *
     * @param classifiable the classifiable thing
     * @return C the classified value
     */
    @Override
    public C classify(T classifiable) {
        if (classifiable == null) {
            return this.defaultValue;
        }

        @SuppressWarnings("unchecked")
        Class<? extends T> exceptionClass = (Class<? extends T>) classifiable.getClass();
        if (this.classified.containsKey(exceptionClass)) {
            return this.classified.get(exceptionClass);
        }

        // check for subclasses
        C value = null;
        for (Class<?> cls = exceptionClass.getSuperclass(); !cls.equals(Object.class)
                && value == null; cls = cls.getSuperclass()) {

            value = this.classified.get(cls);
        }

        // check for interfaces subclasses
        if (value == null) {
            for (Class<?> cls = exceptionClass; !cls.equals(Object.class) && value == null; cls = cls.getSuperclass()) {
                for (Class<?> ifc : cls.getInterfaces()) {
                    value = this.classified.get(ifc);
                    if (value != null) {
                        break;
                    }
                }
            }
        }

        // ConcurrentHashMap doesn't allow nulls
        if (value != null) {
            this.classified.put(exceptionClass, value);
        }

        if (value == null) {
            value = this.defaultValue;
        }

        return value;
    }

    /**
     * Return the default value supplied in the constructor (default false).
     *
     * @return C the default value
     */
    final public C getDefault() {
        return this.defaultValue;
    }

    protected Map<Class<? extends T>, C> getClassified() {
        return this.classified;
    }

}
