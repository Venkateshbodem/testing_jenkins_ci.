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
import org.gradle.internal.component.NoMatchingArtifactVariantsException;
import org.gradle.internal.component.model.AttributeDescriberSelector;
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor;
import org.gradle.internal.component.resolution.failure.ResolutionFailure;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.TreeFormatter;

import static org.gradle.internal.exceptions.StyledException.style;

public class NoMatchingArtifactVariantFailureDescriber extends AbstractResolutionFailureDescriber<NoMatchingArtifactVariantsException> {
    private static final String NO_MATCHING_VARIANTS_PREFIX = "No matching variant errors are explained in more detail at ";
    private static final String NO_MATCHING_VARIANTS_SECTION = "sub:variant-no-match";

    public NoMatchingArtifactVariantFailureDescriber(DocumentationRegistry documentationRegistry) {
        super(documentationRegistry);
    }

    @Override
    public boolean canDescribeFailure(ResolutionFailure failure) {
        return failure.getType() == ResolutionFailure.ResolutionFailureType.NO_MATCHING_ARTIFACT_VARIANT;
    }

    @Override
    public NoMatchingArtifactVariantsException describeFailure(ResolutionFailure failure) {
        AttributeDescriber describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), failure.getSchema());
        String message = buildNoMatchingVariantsFailureMsg(failure, describer);
        NoMatchingArtifactVariantsException e = new NoMatchingArtifactVariantsException(message);
        suggestSpecificDocumentation(e, NO_MATCHING_VARIANTS_PREFIX, NO_MATCHING_VARIANTS_SECTION);
        suggestReviewAlgorithm(e);
        return e;
    }

    private String buildNoMatchingVariantsFailureMsg(
        ResolutionFailure failure,
        AttributeDescriber describer
    ) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("No variants of " + style(StyledTextOutput.Style.Info, failure.getRequestedName()) + " match the consumer attributes");
        formatter.startChildren();
        for (ResolutionCandidateAssessor.AssessedCandidate assessedCandidate : failure.getCandidates()) {
            formatter.node(assessedCandidate.getDisplayName());
            formatAttributeMatchesForIncompatibility(assessedCandidate, formatter, describer);
        }
        formatter.endChildren();
        return formatter.toString();
    }
}
