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

public enum DeprecationType {

    /**
     * The key characteristic is that the trace to the usage indicates the offending user code.
     *
     * Example: calling a deprecated method.
     */
    USER_CODE_DIRECT,

    /**
     * The key characteristic is that the trace to the usage DOES NOT indicate the offending user code,
     * but the usage happens during runtime and may be associated to a logical entity (e.g. task, plugin).
     *
     * The association between a usage and entity is not modelled by the usage,
     * but can be inferred from the operation stream (for deprecations, for which operation progress events are emitted).
     *
     * Example: annotation processor on compile classpath (feature is used at compile, not classpath definition)
     */
    USER_CODE_INDIRECT,

    /**
     * The key characteristic is that there is no useful “where was it used information”,
     * as the usage relates to how/where Gradle was invoked.
     *
     * Example: deprecated CLI switch.
     */
    BUILD_INVOCATION;

}
