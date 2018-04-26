/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import spock.lang.Ignore
import spock.lang.Unroll

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
)
class ComponentAttributesDynamicVersionIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Ignore
    def "ignored test because Spock doesn't support Unroll-only tests"() {
        expect:
        true
    }

    @Unroll("#outcome if component-level attribute is #requested")
    def "component attributes are used to reject fixed version"() {
        given:
        repository {
            'org.test:module:1.0' {
                attribute('quality', 'qa')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, '$requested')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf 'org.test:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module:1.0' {
                expectGetMetadata()
                if (requested == 'qa') {
                    expectGetArtifact()
                }
            }
        }

        then:
        if (requested == 'qa') {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org.test:module:1.0')
                }
            }
        } else {
            fails ':checkDeps'
            failure.assertHasCause("Unable to find a matching configuration of org.test:module:1.0:")
            failure.assertThatCause(containsNormalizedString("Required quality '$requested' and found incompatible value 'qa'"))
        }

        where:
        requested | outcome
        'qa'      | 'succeeds'
        'canary'  | 'fails'
    }

    @Unroll("selects the first version which matches the component-level attributes (requested=#requested)")
    def "selects the first version which matches the component-level attributes"() {
        given:
        repository {
            'org.test:module:1.3' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.2' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.1' {
                attribute('quality', 'qa')
            }
            'org.test:module:1.0' {
                attribute('quality', 'beta')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)
            configurations {
                conf.attributes.attribute(quality, 'qa')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf 'org.test:module:$requested'
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.3' {
                expectGetMetadata()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:

        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:module:${requested}", 'org.test:module:1.1')
            }
        }

        where:
        requested << ["[1.0,)", latestNotation(), "1.+", "1+", "+"]
    }

    @Unroll("selects the first version which matches the component-level attributes (requested=#requested) using dependency attributes")
    def "selects the first version which matches the component-level attributes using dependency attributes"() {
        given:
        repository {
            'org.test:module:1.3' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.2' {
                attribute('quality', 'rc')
            }
            'org.test:module:1.1' {
                attribute('quality', 'qa')
            }
            'org.test:module:1.0' {
                attribute('quality', 'beta')
            }
        }
        buildFile << """
            def quality = Attribute.of("quality", String)

            configurations {
                // This test also makes sure that configuration-level attributes are overwritten
                // by dependency-level attributes. This should really belong to a different test
                // but since integration tests are pretty slow we do both in one go, knowing that
                // configuration-level already has its own test
                conf.attributes.attribute(quality, 'boo')
            }
            
            dependencies {
                attributesSchema {
                    attribute(quality)
                }
                conf('org.test:module:$requested') {
                    attributes {
                        attribute(quality, 'qa')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org.test:module' {
                expectVersionListing()
            }
            'org.test:module:1.3' {
                expectGetMetadata()
            }
            'org.test:module:1.2' {
                expectGetMetadata()
            }
            'org.test:module:1.1' {
                expectResolve()
            }
        }

        then:

        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:module:${requested}", 'org.test:module:1.1')
            }
        }

        where:
        requested << ["[1.0,)", latestNotation(), "1.+", "1+", "+"]
    }

    static Closure<String> latestNotation() {
        { -> GradleMetadataResolveRunner.useIvy() ? "latest.integration" : "latest.release" }
    }

}
