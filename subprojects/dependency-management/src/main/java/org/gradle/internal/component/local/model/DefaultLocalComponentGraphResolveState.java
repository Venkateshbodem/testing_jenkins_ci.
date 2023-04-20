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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantArtifactSelectionMetadata;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentMetadata, LocalComponentMetadata> implements LocalComponentGraphResolveState {
    private final AttributeDesugaring attributeDesugaring;

    // The artifact resolve state for each variant of this component
    private final ConcurrentMap<LocalConfigurationGraphResolveMetadata, DefaultLocalVariantArtifactResolveState> variants = new ConcurrentHashMap<>();
    // The variants of this component to use for artifact selection when variant reselection is enabled
    private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariantsForArtifactSelection;
    // The public view of all selectable variants of this component
    private final Lazy<List<ResolvedVariantResult>> selectableVariantResults;

    public DefaultLocalComponentGraphResolveState(LocalComponentMetadata metadata, AttributeDesugaring attributeDesugaring) {
        super(metadata, metadata);
        allVariantsForArtifactSelection = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream().
                map(LocalConfigurationGraphResolveMetadata.class::cast).
                map(LocalConfigurationGraphResolveMetadata::prepareToResolveArtifacts).
                flatMap(variant -> variant.getVariants().stream()).
                collect(Collectors.toSet())));
        this.attributeDesugaring = attributeDesugaring;
        selectableVariantResults = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().orElse(Collections.emptyList()).stream().
            map(LocalConfigurationGraphResolveMetadata.class::cast).
            flatMap(variant -> variant.getVariants().stream()).
            map(variant -> new DefaultResolvedVariantResult(
                getId(),
                Describables.of(variant.getName()),
                attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                capabilitiesFor(variant.getCapabilities()),
                null
            )).
            collect(Collectors.toList()));
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        return getMetadata().copy(componentIdentifier, artifacts);
    }

    @Override
    public ComponentArtifactResolveMetadata getResolveMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults.get();
    }

    @Override
    public ResolvedVariantResult getVariantResult(VariantGraphResolveMetadata metadata, @Nullable ResolvedVariantResult externalVariant) {
        return new DefaultResolvedVariantResult(
            getId(),
            Describables.of(metadata.getName()),
            attributeDesugaring.desugar(metadata.getAttributes()),
            capabilitiesFor(metadata.getCapabilities()),
            externalVariant);
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationGraphResolveMetadata) variant);
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        return stateFor((LocalConfigurationGraphResolveMetadata) variant);
    }

    private DefaultLocalVariantArtifactResolveState stateFor(LocalConfigurationGraphResolveMetadata variant) {
        return variants.computeIfAbsent(variant, c -> new DefaultLocalVariantArtifactResolveState(getMetadata(), variant, allVariantsForArtifactSelection));
    }

    private static class DefaultLocalVariantArtifactResolveState implements VariantArtifactResolveState, VariantArtifactGraphResolveMetadata {
        private final LocalComponentMetadata component;
        private final LocalConfigurationGraphResolveMetadata graphSelectedVariant;
        private final Set<? extends VariantResolveMetadata> legacyVariants;
        private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants;

        public DefaultLocalVariantArtifactResolveState(LocalComponentMetadata component, LocalConfigurationGraphResolveMetadata graphSelectedVariant, Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariantsForArtifactSelection) {
            this.component = component;
            this.graphSelectedVariant = graphSelectedVariant;
            this.legacyVariants = graphSelectedVariant.prepareToResolveArtifacts().getVariants();
            this.allVariants = allVariantsForArtifactSelection;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return graphSelectedVariant.prepareToResolveArtifacts().getArtifacts();
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedVariant.prepareToResolveArtifacts().artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(new LocalComponentArtifactResolveMetadata(component), new LocalVariantArtifactSelectionMetadata(allVariants, legacyVariants), exclusions, overriddenAttributes);
        }
    }

    private static class LocalVariantArtifactSelectionMetadata implements VariantArtifactSelectionMetadata {
        private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants;
        private final Set<? extends VariantResolveMetadata> legacyVariants;

        public LocalVariantArtifactSelectionMetadata(Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants, Set<? extends VariantResolveMetadata> legacyVariants) {
            this.allVariants = allVariants;
            this.legacyVariants = legacyVariants;
        }

        @Override
        public Set<? extends VariantResolveMetadata> getAllVariants() {
            return allVariants.get().orElse(legacyVariants);
        }

        @Override
        public Set<? extends VariantResolveMetadata> getLegacyVariants() {
            return legacyVariants;
        }
    }

    private static class LocalComponentArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
        private final LocalComponentMetadata metadata;

        public LocalComponentArtifactResolveMetadata(LocalComponentMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ComponentIdentifier getId() {
            return metadata.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return metadata.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return metadata.getSources();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return metadata.getAttributes();
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return metadata.getAttributesSchema();
        }

        @Override
        public ComponentResolveMetadata getMetadata() {
            return metadata;
        }
    }
}
