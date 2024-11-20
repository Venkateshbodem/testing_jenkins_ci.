/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems;

/**
 * Provides options to configure deprecations.
 *
 * @see ProblemReporter
 * @since 8.6
 */
public interface DeprecationSpec {

    ProblemSpec id(String name, String displayName);

    ProblemSpec id(String name, String displayName, ProblemGroup parent);

    /**
     * Defines a deprecation type.
     *
     * @param type
     * @return
     */
    DeprecationSpec type(DeprecationType type);

    /**
     * Declares a short, but context-dependent message for this problem.
     *
     * @param contextualLabel the short message
     * @return this
     * @since 8.8
     */
    ProblemSpec contextualLabel(String contextualLabel);

    /**
     * The long description of this problem.
     *
     * @param details the details
     * @return this
     * @since 8.6
     */
    ProblemSpec details(String details);

    /**
     * A description of how to solve this problem.
     *
     * @param solution the solution.
     * @return this
     * @since 8.6
     */
    ProblemSpec solution(String solution);

    /**
     * Declares where this problem is documented.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec documentedAt(String url);



}
