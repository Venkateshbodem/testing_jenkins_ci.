/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.test.fixtures.ArtifactResolutionExpectationSpec
import org.gradle.test.fixtures.GradleMetadataAwarePublishingSpec
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.SingleArtifactResolutionResultSpec
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyJavaModule
import org.gradle.test.fixtures.ivy.IvyModule

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepositoryDefinition

abstract class AbstractIvyPublishIntegTest extends AbstractIntegrationSpec implements GradleMetadataAwarePublishingSpec {

    def setup() {
        prepare()
    }

    @Override
    void setResolveModuleMetadata(boolean resolveModuleMetadata) {
        if (!resolveModuleMetadata) {
            throw new IllegalStateException("Ivy test fixture doesn't need to disable resolving anymore. Use the appropriate 'resolveArtifact' expectations")
        }
    }

    protected static IvyJavaModule javaLibrary(IvyFileModule module) {
        new IvyJavaModule(module)
    }

    void resolveArtifacts(Object dependencyNotation, @DelegatesTo(value = IvyArtifactResolutionExpectation, strategy = Closure.DELEGATE_FIRST) Closure<?> expectationSpec) {
        IvyArtifactResolutionExpectation expectation = new IvyArtifactResolutionExpectation()
        expectation.dependency = convertDependencyNotation(dependencyNotation)
        expectationSpec.resolveStrategy = Closure.DELEGATE_FIRST
        expectationSpec.delegate = expectation
        expectationSpec()

        expectation.validate()

    }

    private def doResolveArtifacts(ResolveParams params) {
        // Replace the existing buildfile with one for resolving the published module
        settingsFile.text = "rootProject.name = 'resolve'"

        if (params.resolveModuleMetadata) {
            ExperimentalFeaturesFixture.enable(settingsFile)
        } else {
            executer.beforeExecute {
                // Remove the experimental flag set earlier...
                // TODO:DAZ Remove this once we support excludes and we can have a single flag to enable publish/resolve
                withArguments()
            }
        }

        String attributes = params.variant == null ?
            "" :
            """ 
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.${params.variant}))
    }
"""

        String extraArtifacts = ""
        if (params.additionalArtifacts) {
            String artifacts = params.additionalArtifacts.collect {
                def tokens = it.ivyTokens
                """
                    artifact {
                        name = '${sq(tokens.artifact)}'
                        classifier = '${sq(tokens.classifier)}'
                        type = '${sq(tokens.type)}'
                    }"""
            }.join('\n')
            extraArtifacts = """
                {
                    transitive = false
                    $artifacts
                }
            """
        }

        String dependencyNotation = params.dependency
        if (params.configuration) {
            dependencyNotation = "${dependencyNotation}, configuration: '${sq(params.configuration)}'"
        }

        buildFile.text = """
            configurations {
                resolve {
                    $attributes
                }
            }
            repositories {
                ivy { 
                    url "${ivyRepo.uri}"
                    metadataSources {
                       ${params.resolveModuleMetadata?'gradleMetadata':'ivyDescriptor'}()
                    }
                }
                ${mavenCentralRepositoryDefinition()}
            }
            dependencies {
                resolve($dependencyNotation) $extraArtifacts
            }

            task resolveArtifacts(type: Sync) {
                from configurations.resolve
                into "artifacts"
            }
        """

        if (params.status != null) {
            buildFile.text = buildFile.text + """

                dependencies.components.all { ComponentMetadataDetails details, IvyModuleDescriptor ivyModule ->
                    details.statusScheme = [ '${sq(params.status)}' ]
                }
            """
        }
        if (params.expectFailure) {
            fails "resolveArtifacts"
            return []
        } else {
            run "resolveArtifacts"
            def artifactsList = file("artifacts").exists() ? file("artifacts").list() : []
            return artifactsList.sort()
        }
    }

    private static String convertDependencyNotation(Object notation) {
        if (notation instanceof CharSequence) {
            return notation
        }
        if (notation instanceof IvyModule) {
            return "group: '${sq(notation.organisation)}', name: '${sq(notation.module)}', version: '${sq(notation.revision)}'"
        }
        throw new UnsupportedOperationException("Unsupported dependency notation: $notation")
    }

    static String sq(String input) {
        return escapeForSingleQuoting(input)
    }

    static String escapeForSingleQuoting(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }

    static class ResolveParams {
        String dependency
        List<? extends ModuleArtifact> additionalArtifacts

        String configuration
        String status
        String variant
        boolean resolveModuleMetadata = GradleMetadataResolveRunner.isExperimentalResolveBehaviorEnabled()
        boolean expectFailure
    }

    class IvyArtifactResolutionExpectation extends ResolveParams implements ArtifactResolutionExpectationSpec {

        void validate() {
            singleValidation(true, withModuleMetadataSpec)
            singleValidation(false, withoutModuleMetadataSpec)
        }

        void singleValidation(boolean withModuleMetadata, SingleArtifactResolutionResultSpec expectationSpec) {
            ResolveParams params = new ResolveParams(
                dependency: dependency,
                configuration: configuration,
                additionalArtifacts: additionalArtifacts?.asImmutable(),
                status: status,
                variant: variant,
                resolveModuleMetadata: withModuleMetadata,
                expectFailure: !expectationSpec.expectSuccess
            )
            println "Checking ${additionalArtifacts?'additional artifacts':'artifacts'} when resolving ${withModuleMetadata?'with':'without'} Gradle module metadata"
            def actualFileNames = doResolveArtifacts(params)
            expectationSpec.with {
                if (expectSuccess) {
                    assert actualFileNames == expectedFileNames
                }
            }
        }

    }

}
