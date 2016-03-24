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
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.internal.protocol.DefaultBuildIdentity
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.gradle.BuildInvocations
import org.gradle.util.GradleVersion

/**
 * Tooling client can define a composite and execute tasks
 */
class ExecuteBuildCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    def "executes task in a single project within a composite "() {
        given:
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
"""
        }
        def builds = [build1]
        if(numberOfOtherBuilds > 0) {
            builds.addAll([2..(numberOfOtherBuilds+1)].collect {
                populate("build${it}") {
                    buildFile << "apply plugin: 'java'"
                }
            })
        }
        when:
        withCompositeBuildParticipants(builds) { connection, List buildIds ->
            def build1Id = buildIds[0]
            def buildLauncher = connection.newBuild(build1Id)
            buildLauncher.forTasks("hello")
            buildLauncher.run()
        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'

        where:
        numberOfOtherBuilds << [0, 3]
    }

    def "throws exception when task executed on build that doesn't exist in the composite"() {
        given:
        def build1 = populate("build1") {
            buildFile << "apply plugin: 'java'"
        }
        def build2 = populate("build2") {
            buildFile << "apply plugin: 'java'"
        }
        def build3 = populate("build3") {
            buildFile << "apply plugin: 'java'"
        }
        def builds = [build1, build2]
        when:
        def buildId = new DefaultBuildIdentity(build3)
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild(buildId)
            buildLauncher.forTasks("jar")
            buildLauncher.run()

        }
        then:
        def e = thrown(GradleConnectionException)
        e.cause.message == "Build not part of composite"
    }

    def "executes task in single project selected with Launchable"() {
        given:
        def build1 = populate("build1") {
            buildFile << """
task hello {
  doLast {
     file('hello.txt').text = "Hello world"
  }
}
"""
        }
        def builds = [build1]
        builds.addAll([2..3].collect {
            populate("build${it}") {
                buildFile << "apply plugin: 'java'"
            }
        })
        when:
        withCompositeBuildParticipants(builds) { connection, List buildIds ->
            def build1Id = buildIds[0]
            def task
            connection.getModels(modelType).each { modelresult ->
                if (modelresult.projectIdentity.build == build1Id) {
                    task = modelresult.model.getTasks().find { it.name == 'hello' }
                }
            }
            assert task != null
            def buildLauncher = connection.newBuild(build1Id)
            buildLauncher.forTasks(task)
            buildLauncher.run()
        }
        then:
        def helloFile = build1.file("hello.txt")
        helloFile.exists()
        helloFile.text == 'Hello world'

        where:
        modelType << launchableSources()
    }

    private static List<Class<?>> launchableSources() {
        List<Class<?>> launchableSources = [GradleProject]
        if (getTargetDistVersion() >= GradleVersion.version("1.12")) {
            // BuildInvocations returns InternalLauncher instances with accesses a different code path
            // TODO: We should support `BuildInvocations` back further than 1.12
            launchableSources += BuildInvocations
        }
        return launchableSources
    }

}
