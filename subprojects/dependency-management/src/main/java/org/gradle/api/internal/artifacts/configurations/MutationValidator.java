/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

/**
 * Used to validate mutation of an object and its sub-parts.
 */
public interface MutationValidator {
    /**
     * Check if mutation is allowed.
     *
     * @param lenient <code>false</code> if mutation should be completely forbidden, or <code>true</code> if mutation is allowed, but a deprecation warning should be shown.
     */
    void validateMutation(boolean lenient);

    static final MutationValidator IGNORE = new MutationValidator() {
        @Override
        public void validateMutation(boolean lenient) {
        }
    };
}
