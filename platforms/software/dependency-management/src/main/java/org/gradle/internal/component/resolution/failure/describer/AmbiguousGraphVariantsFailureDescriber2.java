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

package org.gradle.internal.component.resolution.failure.describer;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.attributes.AttributeDescriber;
import org.gradle.internal.component.AmbiguousGraphVariantsException;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.resolution.failure.AmbiguousResolutionFailure2;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;
import java.util.TreeMap;

import static org.gradle.internal.exceptions.StyledException.style;

public class AmbiguousGraphVariantsFailureDescriber2 extends AbstractResolutionFailureDescriber2<AmbiguousGraphVariantsException, AmbiguousResolutionFailure2> {
    private static final String AMBIGUOUS_VARIANTS_PREFIX = "Ambiguity errors are explained in more detail at ";
    private static final String AMBIGUOUS_VARIANTS_SECTION = "sub:variant-ambiguity";

    @Inject
    protected AmbiguousGraphVariantsFailureDescriber2(DocumentationRegistry documentationRegistry) {
        super(documentationRegistry);
    }

    @Override
    public Class<AmbiguousResolutionFailure2> getDescribedFailureType() {
        return AmbiguousResolutionFailure2.class;
    }

    @Override
    public boolean canDescribeFailure(AmbiguousResolutionFailure2 failure) {
        return true;
    }

    @Override
    public AmbiguousGraphVariantsException describeFailure(AmbiguousResolutionFailure2 failure) {
        String message = buildAmbiguousGraphVariantsFailureMsg(failure);
        AmbiguousGraphVariantsException result = new AmbiguousGraphVariantsException(message);
        suggestSpecificDocumentation(result, AMBIGUOUS_VARIANTS_PREFIX, AMBIGUOUS_VARIANTS_SECTION);
        suggestReviewAlgorithm(result);
        return result;
    }

    private String buildAmbiguousGraphVariantsFailureMsg(AmbiguousResolutionFailure2 failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), failure.getSchema());

        Map<String, ResolutionCandidateAssessor.AssessedCandidate> ambiguousVariants = new TreeMap<>();
        for (ResolutionCandidateAssessor.AssessedCandidate candidate : failure.getCandidates()) {
            ambiguousVariants.put(candidate.getDisplayName(), candidate);
        }
        TreeFormatter formatter = new TreeFormatter();
        String configTerm = failure.isVariantAware() ? "variants" : "configurations";
        if (failure.getRequestedAttributes().isEmpty()) {
            formatter.node("Cannot choose between the following " + configTerm + " of ");
        } else {
            formatter.node("The consumer was configured to find " + describer.describeAttributeSet(failure.getRequestedAttributes().asMap()) + ". However we cannot choose between the following " + configTerm + " of ");
        }
        formatter.append(style(StyledTextOutput.Style.Info, failure.getRequestedName()));
        formatter.startChildren();
        for (String configuration : ambiguousVariants.keySet()) {
            formatter.node(configuration);
        }
        formatter.endChildren();
        formatter.node("All of them match the consumer attributes");
        // We're sorting the names of the variants and later attributes
        // to make sure the output is consistently the same between invocations
        formatter.startChildren();
        for (ResolutionCandidateAssessor.AssessedCandidate assessedCandidate : ambiguousVariants.values()) {
            formatUnselectable(assessedCandidate, formatter, failure.getTargetComponent(), failure.isVariantAware(), describer);
        }
        formatter.endChildren();

        return formatter.toString();
    }

    private void formatUnselectable(
        ResolutionCandidateAssessor.AssessedCandidate assessedCandidate,
        TreeFormatter formatter,
        @Nullable ComponentGraphResolveMetadata targetComponent,
        boolean variantAware,
        AttributeDescriber describer
    ) {
        if (variantAware) {
            formatter.node("Variant '");
        } else {
            formatter.node("Configuration '");
        }
        formatter.append(assessedCandidate.getDisplayName());
        formatter.append("'");
        if (variantAware) {
            formatter.append(" " + CapabilitiesDescriber.describeCapabilitiesWithTitle(targetComponent, assessedCandidate.getCandidateCapabilities().asSet()));
        }

        formatAttributeMatchesForAmbiguity(assessedCandidate, formatter, describer);
    }
}
