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

package org.gradle.tooling.connection;

import org.gradle.api.Incubating;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;

/**
 * Represents a connection to a composite Gradle build.
 *
 * <p>A composite build is a lightweight assembly of Gradle projects that a developer is working on.
 * These projects may come from different Gradle builds, but when assembled into a composite Gradle is
 * able to coordinate across these projects, so that they appear in some way as a single build unit.</p>
 *
 * <p>Operations (fetching models, executing tasks, etc) are performed across all Gradle projects in a composite.</p>
 *
 * @since 2.13
 */
@Incubating
public interface GradleConnection {

    /**
     * Fetches a Set of snapshots of the model of the given type for this composite. This method blocks until the model is available.
     *
     * <p>This method is simply a convenience for calling {@code models(modelType).get()}</p>
     *
     * @param modelType
     * @param <T>
     * @throws GradleConnectionException
     * @throws IllegalStateException
     */
    <T> ModelResults<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException;

    /**
     * Starts fetching a Set of snapshots of the model of the given type for this composite, passing the result to the given handler when complete. This method returns immediately, and the result is later
     * passed to the given handler's {@link ResultHandler#onComplete(Object)} method after fetching all of the composite's models.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception.
     * See {@link #getModels(Class)} for a description of the various exceptions that the operation may fail with.</p>
     *
     * <p>An operation will fail if there is a problem fetching the model from any of the composite's builds.
     * The handler's {@code onFailure} method will only be called one time with the first failure.</p>
     *
     * <p>This method is simply a convenience for calling {@code models(modelType).get(handler)}</p>
     *
     * @param modelType
     * @param handler
     * @param <T>
     * @throws IllegalStateException
     */
    <T> void getModels(Class<T> modelType, ResultHandler<? super ModelResults<T>> handler) throws IllegalStateException;

    /**
     * Creates a builder which can be used to query the model of the given type for all projects in the composite.
     *
     * <p>The set of projects is "live", so that models from projects added to the overall composite after the builder
     * was been created will appear in the results without recreating the builder.</p>
     *
     * <p>Any of following models types may be available, depending on the version of Gradle being used by the target
     * build:
     *
     * <ul>
     *     <li>{@link org.gradle.tooling.model.gradle.GradleBuild}</li>
     *     <li>{@link org.gradle.tooling.model.build.BuildEnvironment}</li>
     *     <li>{@link org.gradle.tooling.model.GradleProject}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.BuildInvocations}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.ProjectPublications}</li>
     *     <li>{@link org.gradle.tooling.model.idea.IdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.idea.BasicIdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.EclipseProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.HierarchicalEclipseProject}</li>
     * </ul>
     *
     * <p>A build may also expose additional custom tooling models. You can use this method to query these models.
     *
     * @param modelType
     * @param <T>
     */
    <T> ModelBuilder<ModelResults<T>> models(Class<T> modelType);


    BuildLauncher newBuild(BuildIdentity buildIdentity);


    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     */
    void close();
}
