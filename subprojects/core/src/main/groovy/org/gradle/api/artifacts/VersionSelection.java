/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

/***
 * Represents a tuple of the requested version of a module and a candidate version
 * to be evaluated in a version selection rule.
 */
@Incubating
public interface VersionSelection {
    /**
     * Gets the requested version of the module.
     *
     * @return the requested version of the module
     */
    ModuleComponentSelector getRequested();

    /**
     * Gets the candidate version of the module.
     *
     * @return the candidate version of the module
     */
    ModuleComponentIdentifier getCandidate();

    /**
     * Accepts the selection as a resolution.
     */
    void accept();

    /**
     * Rejects the selection as a resolution.
     */
    void reject();
}
