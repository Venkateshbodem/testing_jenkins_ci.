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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import javax.annotation.Nullable;

/**
 * State for a component instance that is used to perform dependency graph resolution.
 */
public interface ComponentGraphResolveState {
    ComponentIdentifier getId();

    /**
     * @return the sources information for this component.
     */
    @Nullable
    ModuleSources getSources();

    ComponentGraphResolveMetadata getMetadata();

    /**
     * When this component is a lenient platform, create a copy with the given ids.
     */
    @Nullable
    ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier);

    /**
     * Determines the set of artifacts for the given variant of this component.
     *
     * <p>Note that this may be expensive, for example it may block waiting for access to the source project or for network or IO requests to the source repository.
     */
    VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant);

    ComponentArtifactResolveState prepareForArtifactResolution();
}
