/*
 * Copyright 2023 the original author or authors.
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


import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import java.util.Collection;

public class LocalVariantMetadata extends DefaultVariantMetadata {
    private final CalculatedValueContainer<ImmutableList<LocalComponentArtifactMetadata>, ?> artifacts;

    public LocalVariantMetadata(String name, Identifier identifier, ComponentIdentifier componentId, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> sourceArtifacts, ImmutableCapabilities capabilities, ModelContainer<?> model, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
        artifacts = calculatedValueContainerFactory.create(Describables.of(displayName, "artifacts"), context -> {
            if (sourceArtifacts.isEmpty()) {
                return ImmutableList.of();
            } else {
                return model.fromMutableState(m -> {
                    ImmutableList.Builder<LocalComponentArtifactMetadata> result = ImmutableList.builderWithExpectedSize(sourceArtifacts.size());
                    for (PublishArtifact sourceArtifact : sourceArtifacts) {
                        result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                    }
                    return result.build();
                });
            }
        });
    }

    public LocalVariantMetadata(String name, Identifier identifier, DisplayName displayName, ImmutableAttributes attributes, ImmutableList<LocalComponentArtifactMetadata> artifacts, ImmutableCapabilities capabilities, CalculatedValueContainerFactory calculatedValueContainerFactory) {
        super(name, identifier, displayName, attributes, ImmutableList.of(), capabilities);
        this.artifacts = calculatedValueContainerFactory.create(Describables.of(displayName, "artifacts"), artifacts);
    }

    public void prepareToResolveArtifacts() {
        artifacts.finalizeIfNotAlready();
    }

    @Override
    public boolean isEligibleForCaching() {
        return true;
    }

    @Override
    public ImmutableList<LocalComponentArtifactMetadata> getArtifacts() {
        return artifacts.get();
    }
}
