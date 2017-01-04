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

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.api.artifacts.transform.ArtifactTransformTargets
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributeContainer
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.internal.component.model.ComponentAttributeMatcher
import spock.lang.Specification

class DefaultArtifactTransformRegistrationsTest extends Specification {

    def matcher = Mock(ComponentAttributeMatcher)
    def schema = new DefaultAttributesSchema(matcher)
    def reg = new DefaultArtifactTransformRegistrations(schema)

    def a1 = Attribute.of("a1", String)
    def a2 = Attribute.of("a2", Integer)
    def c1 = new DefaultAttributeContainer().attribute(a1, "1").attribute(a2, 1).asImmutable()
    def c2 = new DefaultAttributeContainer().attribute(a1, "1").attribute(a2, 2).asImmutable()
    def c3 = new DefaultAttributeContainer().attribute(a1, "1").attribute(a2, 3).asImmutable()

    static class Transform extends ArtifactTransform {
        void configure(AttributeContainer from, ArtifactTransformTargets targetRegistry) {
            def a1 = Attribute.of("a1", String)
            def a2 = Attribute.of("a2", Integer)

            from.attribute(a1, "1").attribute(a2, 1)
            targetRegistry.newTarget().attribute(a1, "1").attribute(a2, 2)
            targetRegistry.newTarget().attribute(a1, "1").attribute(a2, 3)
        }

        List<File> transform(File input, AttributeContainer target) {}
    }

    def "Artifact is matched using matcher"() {
        when:
        reg.areMatchingAttributes(c1, c1)
        reg.areMatchingAttributes(c1, c2)

        then:
        1 * matcher.isMatching(schema, c1, c1)
        1 * matcher.isMatching(schema, c1, c2)
        0 * matcher._
    }

    def "Artifact match is reused"() {
        given:
        reg.areMatchingAttributes(c1, c1)

        when:
        reg.areMatchingAttributes(c1, c1)

        then:
        0 * matcher._
    }

    def "Transform is matched using matcher"() {
        given:
        reg.registerTransform(Transform, {})

        when:
        reg.getTransform(c1, c2)

        then:
        1 * matcher.isMatching(schema, c1, c1) >> true
        1 * matcher.isMatching(schema, c2, c2) >> true
        0 * matcher._
    }

    def "Transform match is reused"() {
        given:
        reg.registerTransform(Transform, {})
        reg.getTransform(c1, c2)

        when:
        reg.getTransform(c1, c2)

        then:
        0 * matcher._
    }

    def "Transformed artifact match is reused"() {
        given:
        def original = Mock(ResolvedArtifact)
        def result = Mock(ResolvedArtifact)

        when:
        reg.putTransformedArtifact(original , c2, [result])

        then:
        reg.getTransformedArtifacts(original, c2) == [result]
        0 * matcher._
    }

    def "Match with similar input for transformations and filtering is reused"() {
        given:
        reg.registerTransform(Transform, {})

        when:
        reg.getTransform(c1, c2)
        reg.getTransform(c1, c3)
        reg.areMatchingAttributes(c1, c1)
        reg.areMatchingAttributes(c2, c3)
        reg.areMatchingAttributes(c3, c2)

        then:
        1 * matcher.isMatching(schema, c1, c1) >> true
        1 * matcher.isMatching(schema, c2, c2) >> true
        1 * matcher.isMatching(schema, c2, c3) >> false
        1 * matcher.isMatching(schema, c3, c2) >> false
        1 * matcher.isMatching(schema, c3, c3) >> true
        0 * matcher._
    }

}
