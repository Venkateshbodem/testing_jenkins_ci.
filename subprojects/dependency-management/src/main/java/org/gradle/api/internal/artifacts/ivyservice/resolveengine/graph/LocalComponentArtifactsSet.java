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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleResolutionFilter;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import java.util.LinkedHashSet;
import java.util.Set;

class LocalComponentArtifactsSet extends AbstractArtifactSet {
    private final ResolvedConfigurationIdentifier configurationId;
    private final ModuleResolutionFilter selector;
    private final ComponentIdentifier componentIdentifier;
    private final ComponentOverrideMetadata componentOverrideMetadata;
    private final ComponentMetaDataResolver componentResolver;
    private Set<ComponentArtifactMetaData> artifacts;

    public LocalComponentArtifactsSet(ComponentIdentifier componentIdentifier, ComponentOverrideMetadata componentOverrideMetadata, ModuleVersionIdentifier ownerId, ModuleSource moduleSource, ResolvedConfigurationIdentifier configurationId, ModuleResolutionFilter selector, ComponentMetaDataResolver componentResolver, ArtifactResolver artifactResolver) {
        super(ownerId, moduleSource, artifactResolver);
        this.componentIdentifier = componentIdentifier;
        this.componentOverrideMetadata = componentOverrideMetadata;
        this.componentResolver = componentResolver;
        this.configurationId = configurationId;
        this.selector = selector;
    }

    @Override
    protected Set<ComponentArtifactMetaData> resolveComponentArtifacts() {
        if (artifacts == null) {
            // TODO:DAZ This should really be using the ModuleSource from the previously resolved component
            BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
            componentResolver.resolve(componentIdentifier, componentOverrideMetadata, moduleResolveResult);
            ComponentResolveMetaData component = moduleResolveResult.getMetaData();

            BuildableArtifactSetResolveResult result = new DefaultBuildableArtifactSetResolveResult();
            getArtifactResolver().resolveModuleArtifacts(component, new DefaultComponentUsage(configurationId.getConfiguration()), result);
            artifacts = result.getArtifacts();
        }

        Set<ComponentArtifactMetaData> result = new LinkedHashSet<ComponentArtifactMetaData>();
        ModuleIdentifier moduleId = configurationId.getId().getModule();
        for (ComponentArtifactMetaData artifact : artifacts) {
            IvyArtifactName artifactName = artifact.getName();
            if (!selector.acceptArtifact(moduleId, artifactName)) {
                continue;
            }
            result.add(artifact);
        }

        return result;
    }
}
