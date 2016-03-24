/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.connection.ProjectIdentity;

public class DefaultModelResult<T> implements ModelResult<T> {
    private final T model;
    private final ProjectIdentity projectIdentity;
    private final GradleConnectionException failure;

    public DefaultModelResult(ProjectIdentity projectIdentity, T model) {
        this.projectIdentity = projectIdentity;
        this.model = model;
        this.failure = null;
    }

    public DefaultModelResult(ProjectIdentity projectIdentity, GradleConnectionException failure) {
        this.projectIdentity = projectIdentity;
        this.model = null;
        this.failure = failure;
    }

    @Override
    public ProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    @Override
    public T getModel() {
        if (failure != null) {
            throw failure;
        }
        return model;
    }

    @Override
    public GradleConnectionException getFailure() {
        return failure;
    }

    @Override
    public String toString() {
        if (model != null) {
            return String.format("result={ project=%s, model=%s }", projectIdentity, model.getClass().getCanonicalName());
        }
        assert failure != null;
        return String.format("result={ project=%s, failure=%s }", projectIdentity, failure.getMessage());
    }
}
