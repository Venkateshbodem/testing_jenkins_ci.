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

import com.google.common.collect.Lists;
import org.gradle.tooling.*;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolingClientCompositeModelBuilder<T> {
    private static final GradleVersion USE_CUSTOM_MODEL_ACTION_VERSION = GradleVersion.version("1.12");

    private final ConsumerOperationParameters operationParameters;
    private final ToolingClientCompositeUtil util;
    private final Class<T> modelType;
    private final List<CompositeModelResultsBuilder> builders = Lists.newArrayList();

    ToolingClientCompositeModelBuilder(final Class<T> modelType, ConsumerOperationParameters operationParameters) {
        this.modelType = modelType;

        builders.add(new GradleBuildModelResultsBuilder());
        builders.add(new BuildEnvironmentModelResultsBuilder());
        builders.add(new IdeaProjectModelResultsBuilder());
        builders.add(new HierarchicalModelResultsBuilder());
        builders.add(new CustomActionModelResultsBuilder());
        builders.add(new BruteForceModelResultsBuilder());
        this.util = new ToolingClientCompositeUtil(operationParameters);
        this.operationParameters = operationParameters;
    }

    public Iterable<ModelResult<T>> get() throws GradleConnectionException, IllegalStateException {
        final List<ModelResult<T>> results = Lists.newArrayList();

        for (GradleConnectionParticipant participant : operationParameters.getBuilds()) {
            try {
                final List<ModelResult<T>> participantResults = buildResultsForParticipant(util.createParticipantConnector(participant));
                results.addAll(participantResults);
            } catch (GradleConnectionException e) {
                String message = String.format("Could not fetch models of type '%s' using client-side composite connection.", modelType.getSimpleName());
                results.add(new DefaultModelResult<T>(participant.toProjectIdentity(":"), new GradleConnectionException(message, e)));
            }
        }
        return results;
    }

    private List<ModelResult<T>> buildResultsForParticipant(ParticipantConnector participant) throws GradleConnectionException {
        for (CompositeModelResultsBuilder builder : builders) {
            if (builder.canBuild(participant)) {
                final List<ModelResult<T>> participantResults = Lists.newArrayList();
                builder.addModelResults(participant, participantResults);
                return participantResults;
            }
        }
        throw new GradleConnectionException("Not a supported model type for this participant: " + modelType.getCanonicalName());
    }

    private abstract class CompositeModelResultsBuilder {
        public abstract boolean canBuild(ParticipantConnector participant);

        public abstract void addModelResults(ParticipantConnector participant, List<ModelResult<T>> results);

        protected DefaultModelResult<T> createModelResult(ParticipantConnector participant, String projectPath, T value) {
            return new DefaultModelResult<T>(participant.toProjectIdentity(projectPath), value);
        }

        protected <V> V getProjectModel(ParticipantConnector build, Class<V> modelType) throws GradleConnectionException {
            ProjectConnection connection = build.connect();
            try {
                ModelBuilder<V> modelBuilder = connection.model(modelType);
                util.configureRequest(modelBuilder);
                return modelBuilder.get();
            } finally {
                connection.close();
            }
        }
    }

    /**
     * Builds results for a 'per-build' model, using `GradleBuild` to determine the project structure and
     * creating a result for each project using the same model instance.
     */
    private abstract class PerBuildModelResultsBuilder extends CompositeModelResultsBuilder {
        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            GradleBuild gradleBuild = getProjectModel(participant, GradleBuild.class);
            Object model = getModel(participant, gradleBuild);
            addPerBuildModelResult(participant, gradleBuild.getRootProject(), model, modelResults);
        }

        protected abstract Object getModel(ParticipantConnector participant, GradleBuild gradleBuild);

        private void addPerBuildModelResult(ParticipantConnector participant, BasicGradleProject project, Object value, List<ModelResult<T>> results) {
            results.add(createModelResult(participant, project.getPath(), (T) value));

            for (BasicGradleProject childProject : project.getChildren()) {
                addPerBuildModelResult(participant, childProject, value, results);
            }
        }
    }

    /**
     * Adds the same `GradleBuild` result for every subproject.
     */
    private class GradleBuildModelResultsBuilder extends PerBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return GradleBuild.class.isAssignableFrom(modelType);
        }

        @Override
        protected Object getModel(ParticipantConnector participant, GradleBuild gradleBuild) {
            return gradleBuild;
        }
    }

    /**
     * Adds the same `BuildEnvironment` result for every subproject.
     */
    private class BuildEnvironmentModelResultsBuilder extends PerBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return BuildEnvironment.class.isAssignableFrom(modelType);
        }

        @Override
        protected Object getModel(ParticipantConnector participant, GradleBuild gradleBuild) {
            return getProjectModel(participant, BuildEnvironment.class);
        }
    }

    /**
     * Adds the same `IdeaProject` result for every subproject.
     *
     * TODO: This could be more efficient, since the IdeaProject inherently contains the project structure.
     * However, we might be better off simply supporting `IdeaModule` as a model type.
     */
    private class IdeaProjectModelResultsBuilder extends PerBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return IdeaProject.class.isAssignableFrom(modelType);
        }

        @Override
        protected Object getModel(ParticipantConnector participant, GradleBuild gradleBuild) {
            return getProjectModel(participant, modelType);
        }
    }

    /**
     * Builds results for a 'hierarchical' model, that provides both the Gradle project structure and the model for each subproject.
     */
    private class HierarchicalModelResultsBuilder extends GradleBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return hasProjectHierarchy(modelType);
        }

        private boolean hasProjectHierarchy(Class<T> modelType) {
            return HierarchicalElement.class.isAssignableFrom(modelType)
                && (GradleProject.class.isAssignableFrom(modelType) || HasGradleProject.class.isAssignableFrom(modelType));
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            addResultsFromHierarchicalModel(participant, modelResults);
        }

        private void addResultsFromHierarchicalModel(ParticipantConnector participant, List<ModelResult<T>> results) {
            try {
                T model = getProjectModel(participant, modelType);
                addHierarchicalModel(model, participant, results);
            } catch (GradleConnectionException e) {
                String message = String.format("Could not fetch models of type '%s' using client-side composite connection.", modelType.getSimpleName());
                DefaultModelResult<T> failureResult = new DefaultModelResult<T>(participant.toProjectIdentity(":"), new GradleConnectionException(message, e));
                results.add(failureResult);
            }
        }

        private void addHierarchicalModel(T model, ParticipantConnector participant, List<ModelResult<T>> results) {
            String projectPath = getGradleProject(model).getPath();
            ModelResult<T> result = createModelResult(participant, projectPath, model);
            results.add(result);

            for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
                addHierarchicalModel((T) child, participant, results);
            }
        }

        private GradleProject getGradleProject(T model) {
            assert hasProjectHierarchy(modelType);
            if (GradleProject.class.isAssignableFrom(modelType)) {
                return (GradleProject) model;
            }
            return ((HasGradleProject) model).getGradleProject();
        }
    }

    /**
     * Adds results using a custom model action.
     */
    private class CustomActionModelResultsBuilder extends GradleBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            // Only use custom model action for Gradle >= 1.12, since `BuildInvocations` is adapted in the Tooling API for earlier versions.
            return canUseCustomModelAction(participant);
        }

        private boolean canUseCustomModelAction(ParticipantConnector participant) {
            BuildEnvironment buildEnvironment = getProjectModel(participant, BuildEnvironment.class);
            GradleVersion gradleVersion = GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
            return gradleVersion.compareTo(USE_CUSTOM_MODEL_ACTION_VERSION) >= 0;
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            addResultsUsingModelAction(participant, modelResults);
        }

        private void addResultsUsingModelAction(ParticipantConnector participant, List<ModelResult<T>> results) {
            ProjectConnection projectConnection = participant.connect();

            try {
                BuildActionExecuter<Map<String, T>> actionExecuter = projectConnection.action(new FetchPerProjectModelAction<T>(modelType));
                util.configureRequest(actionExecuter);
                Map<String, T> actionResults = actionExecuter.run();
                for (String projectPath : actionResults.keySet()) {
                    ModelResult<T> result = createModelResult(participant, projectPath, actionResults.get(projectPath));
                    results.add(result);
                }
            } finally {
                projectConnection.close();
            }
        }
    }

    /**
     * Adds results using 'brute force': uses `EclipseProject` to determine the project structure, and opens a `ProjectConnection`
     * for every subproject to get the associated model instance.
     *
     * Currently used for: BuildInvocations/ProjectPublications with Gradle < 1.12
     *
     * TODO: Currently fails badly when subproject directory does not exist.
     * TODO: Could use {@link org.gradle.tooling.internal.consumer.connection.BuildInvocationsAdapterProducer} to create BuildInvocations for Gradle < 1.12.
     * TODO: Could directly construct failures for `ProjectPublications` in Gradle < 1.12, rather than connecting to each project.
     */
    private class BruteForceModelResultsBuilder extends GradleBuildModelResultsBuilder {
        @Override
        public boolean canBuild(ParticipantConnector participant) {
            return true;
        }

        @Override
        public void addModelResults(ParticipantConnector participant, List<ModelResult<T>> modelResults) {
            EclipseProject rootProject = getProjectModel(participant, EclipseProject.class);
            buildResultsWithSeparateProjectConnections(participant, rootProject, modelResults);
        }

        private void buildResultsWithSeparateProjectConnections(ParticipantConnector participant, EclipseProject project, List<ModelResult<T>> results) {
            ParticipantConnector childBuild = participant.withProjectDirectory(project.getProjectDirectory());
            T model = getProjectModel(childBuild, modelType);
            ModelResult<T> result = createModelResult(participant, project.getGradleProject().getPath(), model);
            results.add(result);

            for (EclipseProject childProject : project.getChildren()) {
                buildResultsWithSeparateProjectConnections(participant, childProject, results);
            }
        }
    }

    private static final class FetchPerProjectModelAction<V> implements org.gradle.tooling.BuildAction<Map<String, V>> {
        private final Class<V> modelType;
        private FetchPerProjectModelAction(Class<V> modelType) {
            this.modelType = modelType;
        }

        @Override
        public Map<String, V> execute(BuildController controller) {
            final Map<String, V> results = new HashMap<String, V>();
            fetchResults(modelType, results, controller, controller.getBuildModel().getRootProject());
            return results;
        }

        private void fetchResults(Class<V> modelType, Map<String, V> results, BuildController controller, BasicGradleProject project) {
            results.put(project.getPath(), controller.getModel(project, modelType));
            for (BasicGradleProject child : project.getChildren()) {
                fetchResults(modelType, results, controller, child);
            }
        }
    }
}
