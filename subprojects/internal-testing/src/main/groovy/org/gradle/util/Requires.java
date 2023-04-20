/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.util;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@ExtensionAnnotation(TestPreconditionExtension.class)
public @interface Requires {

    /**
     * The list of preconditions, which will be checked by {@link TestPreconditionExtension}
     */
    Class<? extends TestPrecondition>[] value();

    /**
     * Controls if you require the predicate <b>NOT</b> to be satisfied.
     *
     * @return true if the conjunction of the predicates should be negated. Default is  {@code false}
     */
    boolean not() default false;

    String reason() default "";
}
