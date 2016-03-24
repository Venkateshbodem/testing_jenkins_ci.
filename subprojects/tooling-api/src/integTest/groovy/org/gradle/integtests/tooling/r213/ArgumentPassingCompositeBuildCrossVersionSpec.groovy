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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.model.eclipse.EclipseProject

class ArgumentPassingCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def "can pass additional command-line arguments for project properties"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        when:
        def modelResults = withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-PprojectProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum()
        modelResults.each {
            it.model.description == "Set from project property = foo"
        }

        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            BuildLauncher buildLauncher = connection.newBuild(buildIds[0])
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

    def "can pass additional command-line arguments for system properties"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        when:
        def modelResults = withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.withArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum()
        modelResults.each {
            it.model.description == "Set from system property = foo"
        }

        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            BuildLauncher buildLauncher = connection.newBuild(buildIds[0])
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

    def "can pass additional jvm arguments"() {
        given:
        def builds = createBuilds(numberOfParticipants, numberOfSubprojects)
        when:
        def modelResults = withCompositeConnection(builds) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setJvmArguments("-DsystemProperty=foo")
            modelBuilder.get()
        }
        then:
        modelResults.size() == numberOfParticipants + numberOfSubprojects.sum()
        modelResults.each {
            it.model.description == "Set from system property = foo"
        }

        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            BuildLauncher buildLauncher = connection.newBuild(buildIds[0])
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

    private List createBuilds(int numberOfParticipants, List numberOfSubprojects) {
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
