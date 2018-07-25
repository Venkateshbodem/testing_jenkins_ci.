/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.nativeintegration;

import org.gradle.api.internal.GradleProcessEnvironment;

public enum GradleEnvVariableHandler implements EnvVariableHandler {
    INSTANCE;

    @Override
    public void unsetenv(String name) {
        GradleProcessEnvironment.INSTANCE.unsetenv(name);
    }

    @Override
    public void setenv(String name, String value) {
        GradleProcessEnvironment.INSTANCE.setenv(name, value);
    }

    @Override
    public EnvironmentModificationResult result() {
        return EnvironmentModificationResult.ONLY_SET_GRADLE_ENV;
    }
}
