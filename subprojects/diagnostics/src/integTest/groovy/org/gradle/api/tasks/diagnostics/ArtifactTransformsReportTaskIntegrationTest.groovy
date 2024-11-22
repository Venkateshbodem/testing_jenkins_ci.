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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactTransformsReportTaskIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "if no transforms present in project, task reports complete absence"() {
        expect:
        succeeds ':artifactTransforms'
        reportsCompleteAbsenceOfArtifactTransforms()
    }

    def "if single transform is registered by buildscript, task reports it"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineEmptyTransform()}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
Transform EmptyTransform
--------------------------------------------------
From Attributes:
    - color = blue
    - shape = square
To Attributes:
    - color = red
    - shape = circle
""")
    }

    def "if cacheable transform is registered by buildscript, task reports it"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineEmptyTransform()}
            ${defineEmptyTransform("OtherTransform", true)}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                registerTransform(OtherTransform) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
Transform EmptyTransform
--------------------------------------------------
From Attributes:
    - color = blue
To Attributes:
    - color = red

--------------------------------------------------
Transform OtherTransform (cacheable)
--------------------------------------------------
From Attributes:
    - shape = square
To Attributes:
    - shape = circle
""")
    }

    def "if named transform is registered by buildscript, task reports it"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineEmptyTransform()}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                registerTransform("myTransform", EmptyTransform) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
Transform EmptyTransform
--------------------------------------------------
From Attributes:
    - color = blue
To Attributes:
    - color = red

--------------------------------------------------
Transform myTransform
--------------------------------------------------
Type: EmptyTransform
From Attributes:
    - shape = square
To Attributes:
    - shape = circle
""")
    }

    private String defineAttributes() {
        return """
            def color = Attribute.of("color", String)
            def shape = Attribute.of("shape", String)
        """
    }

    private String defineEmptyTransform(String name = "EmptyTransform", boolean cacheable = false) {
        return """
            ${cacheable ? "@CacheableTransform" : ""}
            abstract class $name implements TransformAction<org.gradle.api.artifacts.transform.TransformParameters.None> {
                void transform(TransformOutputs outputs) {}
            }
        """
    }

    private void reportsCompleteAbsenceOfArtifactTransforms(String projectName = "myLib") {
        outputContains("There are no transforms registered in project '$projectName'.")
    }
}
