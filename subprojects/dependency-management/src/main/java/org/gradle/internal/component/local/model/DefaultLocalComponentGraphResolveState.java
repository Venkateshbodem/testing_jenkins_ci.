/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.Set;

public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentMetadata> {
    private final ProjectArtifactSetResolver artifactSetResolver;

    public DefaultLocalComponentGraphResolveState(LocalComponentMetadata metadata, ProjectArtifactSetResolver artifactSetResolver) {
        super(metadata);
        this.artifactSetResolver = artifactSetResolver;
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactGraphResolveMetadata) variant;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        LocalConfigurationMetadata configuration = (LocalConfigurationMetadata) variant;
        return new DefaultLocalVariantArtifactResolveState(getMetadata(), configuration, artifactSetResolver);
    }

    private static class DefaultLocalVariantArtifactResolveState implements VariantArtifactResolveState {
        private final LocalComponentMetadata component;
        private final LocalConfigurationMetadata configuration;
        private final ProjectArtifactSetResolver artifactSetResolver;

        public DefaultLocalVariantArtifactResolveState(LocalComponentMetadata component, LocalConfigurationMetadata configuration, ProjectArtifactSetResolver artifactSetResolver) {
            this.component = component;
            this.configuration = configuration;
            this.artifactSetResolver = artifactSetResolver;
        }

        @Override
        public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
            return configuration.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ArtifactTypeRegistry artifactTypeRegistry, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            Set<? extends VariantResolveMetadata> variants = configuration.getVariants();
            return artifactSetResolver.resolveArtifacts(component.getId(), component.getModuleVersionId(), component.getSources(), exclusions, variants, component.getAttributesSchema(), artifactTypeRegistry, overriddenAttributes);
        }
    }
}
