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

package org.gradle.api.internal;

import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.invocation.GradleLifecycle;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Gradle.class)
public interface GradleLifecycleInternal extends GradleLifecycle {

    /**
     * Sets the invocation source to be used with {@link BuildScopeListenerRegistrationListener#onBuildScopeListenerRegistration(Object, String, Object)}.
     */
    void setInvocationSource(Object invocationSource);

    boolean hasRootProject();

    ProjectInternal getRootProject(Object consumer);

    void rootProject(Action<? super Project> action);

    void setRootProject(ProjectInternal rootProject);

    void resetState();

    ProjectEvaluationListener getProjectEvaluationBroadcaster();

    BuildListener getBuildListenerBroadcaster();

    void allprojects(Action<? super Project> action);

    void beforeProject(Closure closure);

    @SuppressWarnings("overloads")
    void beforeProject(Action<? super Project> action);

    void afterProject(Closure closure);

    void afterProject(Action<? super Project> action);

    void beforeSettings(Closure<?> closure);

    void beforeSettings(Action<? super Settings> action);

    void settingsEvaluated(Closure closure);

    void settingsEvaluated(Action<? super Settings> action);

    void projectsLoaded(Closure closure);

    void projectsLoaded(Action<? super Gradle> action);

    void projectsEvaluated(Closure closure);

    void projectsEvaluated(Action<? super Gradle> action);

    void buildFinished(Closure closure);

    void buildFinished(Action<? super BuildResult> action);

    void notifyListenerRegistration(String registrationPoint, Object listener);
}
