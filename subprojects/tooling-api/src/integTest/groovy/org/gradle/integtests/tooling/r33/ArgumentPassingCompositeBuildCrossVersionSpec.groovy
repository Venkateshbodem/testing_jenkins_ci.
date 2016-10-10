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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Ignore

@TargetGradleVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
class ArgumentPassingCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    def setup() {
        toolingApi.requireDaemons()
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional command-line arguments for project properties when loading models"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        def modelResults = withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-PprojectProperty=foo")
            modelBuilder.get()
        }

        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum() + 1
        modelResults.each {
            assert it.model.description == "Set from project property = foo"
        }

        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional command-line arguments for project properties when executing tasks"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("run")
            buildLauncher.withArguments("-PprojectProperty=foo")
            buildLauncher.run()
        }
        then:
        noExceptionThrown()
        def results = builds.first().file("result")
        results.text.count("Project property = foo") == (numberOfSubprojects[0]+1)

        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional command-line arguments for system properties when loading models"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        def modelResults = withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum() + 1
        modelResults.each {
            it.model.description == "Set from system property = foo"
        }

        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional command-line arguments for system properties when executing tasks"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        withConnection { connection ->
            BuildLauncher buildLauncher = connection.newBuild()
            buildLauncher.forTasks("run")
            buildLauncher.withArguments("-DsystemProperty=foo")
            buildLauncher.run()
        }
        then:
        noExceptionThrown()
        def results = builds.first().file("result")
        results.text.count("System property = foo") == (numberOfSubprojects[0]+1)
        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional jvm arguments when loading models"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        def modelResults = withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setJvmArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum() + 1
        modelResults.each {
            it.model.description == "Set from system property = foo"
        }

        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    @Ignore("Argument passing not yet implemented")
    def "can pass additional jvm arguments"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        withConnection { connection ->
            BuildLauncher buildLauncher = connection.newBuild()
            buildLauncher.forTasks("run")
            buildLauncher.setJvmArguments("-DsystemProperty=foo")
            buildLauncher.run()
        }
        then:
        noExceptionThrown()
        def results = builds.first().file("result")
        results.text.count("System property = foo") == (numberOfSubprojects[0]+1)
        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    def "can set java home for model requests"() {
        given:
        File javaHome = new File("not/javahome")
        javaHome.mkdirs()
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        def modelResults = withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setJavaHome(javaHome)
            modelBuilder.get()
        }
        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "The supplied javaHome seems to be invalid.")

        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    def "can set javahome for build launcher"() {
        given:
        File javaHome = new File("not/javahome")
        javaHome.mkdirs()
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        includeBuilds(builds)

        when:
        withConnection { connection ->
            BuildLauncher buildLauncher = connection.newBuild()
            buildLauncher.setJavaHome(javaHome)
            buildLauncher.forTasks("run")
            buildLauncher.run()
        }
        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e, "The supplied javaHome seems to be invalid.")
        where:
        numberOfParticipants | numberOfSubprojects
        1                    | [0]
        3                    | [0, 0, 0]
        2                    | [3, 0]
    }

    private List createBuilds(int numberOfParticipants, List numberOfSubprojects) {
        buildFile << """
            task run(dependsOn: gradle.includedBuilds*.task(':run'))
        """
        createBuilds(numberOfParticipants, numberOfSubprojects,
"""
    rootProject.ext.results = rootProject.file("result")

    allprojects {
        apply plugin: 'java'

        description = "not set"
        if (project.hasProperty("projectProperty")) {
            description = "Set from project property = \${project.projectProperty}"
        }
        if (System.properties.systemProperty) {
            description = "Set from system property = \${System.properties.systemProperty}"
        }

        task run() << {
            if (project.hasProperty("projectProperty")) {
                rootProject.results << "Project property = \${project.projectProperty}"
            }
            if (System.properties.systemProperty) {
                rootProject.results << "System property = \${System.properties.systemProperty}"
            }
            if (project.hasProperty("printJavaHome")) {
                rootProject.results << System.properties['java.home']
            }
        }
    }
""")
    }

    private List createBuilds(int numberOfParticipants, List<Integer> numberOfSubprojects, String buildFileText) {
        def builds = []
        numberOfParticipants.times { participantIndex ->
            builds << populate("build-${participantIndex}") {
                buildFile << buildFileText
                numberOfSubprojects[participantIndex].times {
                    settingsFile << """
                        include 'sub-$participantIndex-$it'
                    """
                    file("sub-$participantIndex-$it").mkdirs()
                }
            }
        }
        builds
    }
}
