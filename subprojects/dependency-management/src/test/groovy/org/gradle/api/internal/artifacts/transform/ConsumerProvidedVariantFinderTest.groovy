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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.Action
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.util.AttributeTestUtil
import spock.lang.Issue
import spock.lang.Specification

class ConsumerProvidedVariantFinderTest extends Specification {
    def attributeMatcher = Mock(AttributeMatcher)
    def transformRegistry = Mock(VariantTransformRegistry)

    ConsumerProvidedVariantFinder transformations

    def setup() {
        def schema = Mock(AttributesSchemaInternal)
        schema.matcher() >> attributeMatcher
        transformations = new ConsumerProvidedVariantFinder(transformRegistry, schema, AttributeTestUtil.attributesFactory())
    }

    def "selects transform that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, compatible)
        def transform3 = registration(compatible, incompatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2)

        and:
        1 * attributeMatcher.isMatching(incompatible, requested) >> false
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromSource) >> false
        0 * attributeMatcher._
    }

    def "selects all transforms that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes(usage: "fromOther")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def compatible2 = AttributeTestUtil.attributes(usage: "compatible2")

        def transform1 = registration(fromSource, compatible)
        def transform2 = registration(fromSource, compatible2)
        def transform3 = registration(fromOther, compatible2)
        def transform4 = registration(fromOther, compatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 4
        assertTransformChain(result[0], sourceVariant, compatible, transform1)
        assertTransformChain(result[1], sourceVariant, compatible2, transform2)
        assertTransformChain(result[2], otherVariant, compatible2, transform3)
        assertTransformChain(result[3], otherVariant, compatible, transform4)

        and:
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(compatible2, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromOther) >> false
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromSource) >> false
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromOther) >> true
        0 * attributeMatcher._
    }

    def "transform match is reused"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def compatible = AttributeTestUtil.attributes(usage: "compatible")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, compatible)

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2)

        and:
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromSource) >> false
        1 * attributeMatcher.isMatching(incompatible, requested) >> false
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        0 * attributeMatcher._

        when:
        def anotherVariants = [
                variant(otherVariant.getAttributes()),
                variant(sourceVariant.getAttributes())
        ]
        def result2 = transformations.findTransformedVariants(anotherVariants, requested)

        then:
        result2.size() == 1
        assertTransformChain(result2.first(), anotherVariants[1], compatible, transform2)

        and:
        0 * attributeMatcher._
    }

    def "selects chain of transforms that can produce variant that is compatible with requested"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes([usage: "fromOther"])

        def fromIntermediate = AttributeTestUtil.attributes([usage: "fromIntermediate"])
        def intermediate = AttributeTestUtil.attributes(usage: "intermediate")

        def fromIntermediate2 = AttributeTestUtil.attributes([usage: "fromIntermediate2"])
        def intermediate2 = AttributeTestUtil.attributes([usage: "intermediate2"])

        def compatible = AttributeTestUtil.attributes([usage: "compatible"])
        def compatible2 = AttributeTestUtil.attributes([usage: "compatible2"])

        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes([usage: "incompatible2"])

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromIntermediate, compatible)
        def transform4 = registration(fromOther, intermediate2)
        def transform5 = registration(fromIntermediate2, compatible2)
        def transform6 = registration(fromIntermediate2, incompatible2)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4, transform5, transform6]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], sourceVariant, compatible, transform2, transform3)
        assertTransformChain(result[1], otherVariant, compatible2, transform4, transform5)

        and:
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromOther) >> true
        1 * attributeMatcher.isMatching(intermediate, fromIntermediate) >> true
        1 * attributeMatcher.isMatching(intermediate2, fromIntermediate2) >> true
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(compatible2, requested) >> true
        _ * attributeMatcher.isMatching(_ ,_) >> false
        0 * attributeMatcher._
    }

    def "prefers direct transformation over indirect"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromIndirect = AttributeTestUtil.attributes(usage: "fromIndirect")
        def compatibleIndirect = AttributeTestUtil.attributes(usage: "compatibleIndirect")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")

        def fromSource = AttributeTestUtil.attributes([usage: "fromSource"])
        def compatible = AttributeTestUtil.attributes([usage: "compatible"])
        def fromOther = AttributeTestUtil.attributes([usage: "fromOther"])
        def compatible2 = AttributeTestUtil.attributes([usage: "compatible2"])

        def sourceVariant = variant([usage: "source"])
        def otherVariant = variant([usage: "other"])
        def variants = [ sourceVariant, otherVariant ]

        def transform1 = registration(fromIndirect, incompatible)
        def transform2 = registration(fromIndirect, compatibleIndirect)
        def transform3 = registration(fromSource, compatible)
        def transform4 = registration(fromOther, compatible2)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3, transform4]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 2
        assertTransformChain(result[0], sourceVariant, compatible, transform3)
        assertTransformChain(result[1], otherVariant, compatible2, transform4)
        // possible longer chains
        // (sourceVariant:fromSource) + transform3 -> (compatible:fromIndirect) + transform2 -> (compatibleIndirect:requested)
        // (otherVariant:fromOther) + transform4 -> (compatible2:fromIndirect) + transform2 -> (compatibleIndirect:requested)

        and:
        1 * attributeMatcher.isMatching(compatibleIndirect, requested) >> true
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(compatible2, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(otherVariant.getAttributes(), fromOther) >> true
        0 * attributeMatcher.isMatching(compatible, fromIndirect) >> true
        0 * attributeMatcher.isMatching(compatible2, fromIndirect) >> true
        _ * attributeMatcher.isMatching(_, _) >> false
        0 * attributeMatcher._
    }

    def "prefers shortest chain of transforms #registrationsIndex"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def fromOther = AttributeTestUtil.attributes(usage: "fromOther")

        def intermediate = AttributeTestUtil.attributes([usage: "intermediate"])
        def compatible = AttributeTestUtil.attributes([usage: "compatible"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, fromOther)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromOther, intermediate)
        def transform4 = registration(intermediate, compatible)
        def registrations = [transform1, transform2, transform3, transform4]

        def fromIntermediate = AttributeTestUtil.attributesFactory().concat(requested, intermediate)

        given:
        transformRegistry.transforms >> [registrations[registrationsIndex[0]], registrations[registrationsIndex[1]], registrations[registrationsIndex[2]], registrations[registrationsIndex[3]]]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform4)

        and:
        1 * attributeMatcher.isMatching(fromOther, requested) >> false
        1 * attributeMatcher.isMatching(intermediate, requested) >> false
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), intermediate) >> false
        1 * attributeMatcher.isMatching(fromOther, fromIntermediate) >> false
        1 * attributeMatcher.isMatching(intermediate, fromIntermediate) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromOther) >> false
        0 * attributeMatcher._

        where:
        registrationsIndex << (0..3).permutations()
    }

    @Issue("gradle/gradle#7061")
    def "selects chain of transforms that only all the attributes are satisfied"() {
        def requested = AttributeTestUtil.attributes([usage: "requested", other: "transform3"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource", other: "fromSource")
        def fromIntermediate = AttributeTestUtil.attributes([usage: "fromIntermediate"])
        def partialTransformed = AttributeTestUtil.attributes([usage: "fromIntermediate", other: "transform3"])

        def incompatible = AttributeTestUtil.attributes([usage: "incompatible"])
        def intermediate = AttributeTestUtil.attributes([usage: "intermediate", other: "transform2"])
        def compatible = AttributeTestUtil.attributes([usage: "compatible", other: "transform3"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, intermediate)
        def transform3 = registration(fromIntermediate, compatible)

        given:
        transformRegistry.transforms >> [transform1, transform2, transform3]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.size() == 1
        assertTransformChain(result.first(), sourceVariant, compatible, transform2, transform3)

        and:
        1 * attributeMatcher.isMatching(incompatible, requested) >> false
        1 * attributeMatcher.isMatching(intermediate, requested) >> false
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromIntermediate) >> false
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(incompatible, partialTransformed) >> false
        1 * attributeMatcher.isMatching(intermediate, partialTransformed) >> true
        0 * attributeMatcher._
    }

    def "returns empty list when no transforms are available to produce requested variant"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes(usage: "incompatible2")

        def transform1 = registration(fromSource, incompatible)
        def transform2 = registration(fromSource, incompatible2)

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * attributeMatcher.isMatching(incompatible, requested) >> false
        1 * attributeMatcher.isMatching(incompatible2, requested) >> false
        0 * attributeMatcher._
    }

    def "caches negative match"() {
        def requested = AttributeTestUtil.attributes([usage: "requested"])

        def fromSource = AttributeTestUtil.attributes(usage: "fromSource")
        def incompatible = AttributeTestUtil.attributes(usage: "incompatible")
        def incompatible2 = AttributeTestUtil.attributes(usage: "incompatible2")

        def transform1 = registration(fromSource, incompatible2)
        def transform2 = registration(fromSource, incompatible)

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        given:
        transformRegistry.transforms >> [transform1, transform2]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        result.empty

        and:
        1 * attributeMatcher.isMatching(incompatible2, requested) >> false
        1 * attributeMatcher.isMatching(incompatible, requested) >> false
        0 * attributeMatcher._

        when:
        def result2 = transformations.findTransformedVariants(variants, requested)

        then:
        result2.empty

        and:
        0 * attributeMatcher._
    }

    def "does not match on unrelated transform"() {
        def requested = AttributeTestUtil.attributes([usage: "hello"])

        def fromSource = AttributeTestUtil.attributes([other: "fromSource"])
        def compatible = AttributeTestUtil.attributes([other: "compatible"])

        def sourceVariant = variant([usage: "source"])
        def variants = [ sourceVariant ]

        def transform1 = registration(fromSource, compatible)

        def finalAttributes = AttributeTestUtil.attributesFactory().concat(sourceVariant.getAttributes().asImmutable(), compatible)

        given:
        transformRegistry.transforms >> [transform1]

        when:
        def result = transformations.findTransformedVariants(variants, requested)

        then:
        // sourceVariant transformed by transform1 produces a variant with attributes incompatible with requested
        result.empty

        and:
        1 * attributeMatcher.isMatching(compatible, requested) >> true
        1 * attributeMatcher.isMatching(sourceVariant.getAttributes(), fromSource) >> true
        1 * attributeMatcher.isMatching(finalAttributes, requested) >> false
        0 * attributeMatcher._
    }

    private void assertTransformChain(TransformedVariant chain, ResolvedVariant source, AttributeContainer finalAttributes, ArtifactTransformRegistration... steps) {
        assert chain.root == source
        assert chain.attributes == finalAttributes
        assert chain.transformation.stepsCount() == steps.length
        def actualSteps = []
        chain.transformation.visitTransformationSteps {
            actualSteps << it
        }
        def expectedSteps = steps*.transformationStep
        assert actualSteps == expectedSteps
    }

    private ArtifactTransformRegistration registration(AttributeContainer from, AttributeContainer to) {
        def transformationStep = Stub(TransformationStep)
        _ * transformationStep.visitTransformationSteps(_) >> { Action action -> action.execute(transformationStep) }
        _ * transformationStep.stepsCount() >> 1

        return Mock(ArtifactTransformRegistration) {
            getFrom() >> from
            getTo() >> to
            getTransformationStep() >> transformationStep
        }
    }

    private ResolvedVariant variant(Map<String, Object> attributes) {
        return variant(AttributeTestUtil.attributes(attributes))
    }

    private ResolvedVariant variant(AttributeContainerInternal attributes) {
        return Mock(ResolvedVariant) {
            getAttributes() >> attributes
        }
    }
}
