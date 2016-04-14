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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.*;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Set;

public class CompositeContextBuilder implements BuildActionRunner {
    private final DefaultCompositeBuildContext context = new DefaultCompositeBuildContext();
    private final boolean propagateFailures;

    public CompositeContextBuilder(boolean propagateFailures) {
        this.propagateFailures = propagateFailures;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        try {
            GradleInternal gradle = buildController.configure();
            ProjectInternal rootProject = gradle.getRootProject();

            String participantName = rootProject.getName();
            for (Project project : rootProject.getAllprojects()) {
                registerProject(participantName, (ProjectInternal) project);
            }
        } catch(ReportedException e) {
            // Ignore exceptions creating composite context
            // TODO:DAZ Handle this better. Test coverage.
            if (propagateFailures) {
                throw e;
            }
        }
    }

    private void registerProject(String buildName, ProjectInternal project) {
        String projectPath = buildName + ":" + project.getPath();
        ComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(projectPath);

        ProjectComponentRegistry projectComponentRegistry = project.getServices().get(ProjectComponentRegistry.class);

        DefaultLocalComponentMetaData projectComponentMetadata = (DefaultLocalComponentMetaData) projectComponentRegistry.getProject(project.getPath());
        LocalComponentMetaData localComponentMetaData = createCompositeCopy(componentIdentifier, projectComponentMetadata);

        context.register(localComponentMetaData.getId().getModule(), projectPath, localComponentMetaData, project.getProjectDir());
    }

    private LocalComponentMetaData createCompositeCopy(ComponentIdentifier componentIdentifier, DefaultLocalComponentMetaData projectComponentMetadata) {
        DefaultLocalComponentMetaData compositeComponentMetadata = new DefaultLocalComponentMetaData(projectComponentMetadata.getId(), componentIdentifier, projectComponentMetadata.getStatus());

        for (String configurationName : projectComponentMetadata.getConfigurationNames()) {
            LocalConfigurationMetaData configuration = projectComponentMetadata.getConfiguration(configurationName);
            compositeComponentMetadata.addConfiguration(configurationName,
                configuration.getDescription(), configuration.getExtendsFrom(), configuration.getHierarchy(),
                configuration.isVisible(), configuration.isTransitive(), new DefaultTaskDependency());


            // TODO:DAZ Probably shouldn't need to convert back into PublishArtifact.
            Set<ComponentArtifactMetaData> artifacts = configuration.getArtifacts();
            Set<PublishArtifact> detachedArtifacts = CollectionUtils.collect(artifacts, new Transformer<PublishArtifact, ComponentArtifactMetaData>() {
                @Override
                public PublishArtifact transform(ComponentArtifactMetaData componentArtifactMetaData) {
                    return new DetachedPublishArtifact(componentArtifactMetaData.getName(), ((LocalComponentArtifactIdentifier) componentArtifactMetaData).getFile());
                }
            });

            compositeComponentMetadata.addArtifacts(configurationName, detachedArtifacts);
        }
        for (DependencyMetaData dependency : projectComponentMetadata.getDependencies()) {
            compositeComponentMetadata.addDependency(dependency);
        }
        for (ExcludeRule excludeRule : projectComponentMetadata.getExcludeRules()) {
            compositeComponentMetadata.addExcludeRule(excludeRule);
        }

        return compositeComponentMetadata;
    }

    public CompositeBuildContext build() {
        return context;
    }

    private static class DetachedPublishArtifact extends DefaultPublishArtifact {
        public DetachedPublishArtifact(IvyArtifactName ivyArtifactName, File artifactFile) {
            super(ivyArtifactName.getName(), ivyArtifactName.getExtension(), ivyArtifactName.getType(), ivyArtifactName.getClassifier(), null, artifactFile);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DetachedPublishArtifact)) {
                return false;
            }

            DetachedPublishArtifact that = (DetachedPublishArtifact) o;
            return getFile().equals(that.getFile());

        }

        @Override
        public int hashCode() {
            return getFile().hashCode();
        }
    }

}
