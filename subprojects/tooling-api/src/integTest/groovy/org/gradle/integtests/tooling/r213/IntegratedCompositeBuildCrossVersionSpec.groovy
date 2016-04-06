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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.GradleBuild
/**
 * Tooling models for an integrated composite are produced by a single daemon instance.
 * We only do this for target gradle versions that support integrated composite build.
 */
@ToolingApiVersion(">=2.14")
@TargetGradleVersion(">=2.14")
class IntegratedCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    TestFile rootSingle
    TestFile rootMulti

    def setup() {
        rootSingle = singleProjectBuild("A") {
                    buildFile << """
task helloA {
  doLast {
    file('hello.txt').text = "Hello world from \${project.path}"
  }
}
"""
        }
        rootMulti = multiProjectBuild("B", ['x', 'y']) {
                    buildFile << """
allprojects {
  task helloB {
    doLast {
      file('hello.txt').text = "Hello world from \${project.path}"
    }
  }
}
"""
        }
    }

    def "can retrieve models from integrated composite"() {
        when:
        def gradleProjects = []
        def gradleBuilds = []
        def eclipseProjects = []
        withCompositeConnection([rootSingle, rootMulti]) { connection ->
            gradleProjects = unwrap(connection.getModels(GradleProject))
            gradleBuilds = unwrap(connection.getModels(GradleBuild))
            eclipseProjects = unwrap(connection.getModels(EclipseProject))
        }

        then:
        gradleProjects.size() == 4
        gradleProjects*.name.containsAll(['A', 'B', 'x', 'y'])
        gradleProjects*.path.containsAll([':', ':x', ':y'])

        gradleBuilds.size() == 2
        gradleBuilds*.rootProject.name.containsAll(['A', 'B'])

        eclipseProjects.size() == 4
        eclipseProjects*.name.containsAll(['A', 'B', 'x', 'y'])
        eclipseProjects*.gradleProject.path.containsAll([':', ':x', ':y'])
    }

    def "can execute task in integrated composite"() {
        when:
        withCompositeConnection([rootSingle, rootMulti]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(rootSingle, "helloA")
            buildLauncher.run()
        }

        then:
        rootSingle.file('hello.txt').assertExists().text == 'Hello world from :'
        rootMulti.file('hello.txt').assertDoesNotExist()

        when:
        withCompositeConnection([rootSingle, rootMulti]) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(rootMulti, "helloB")
            buildLauncher.run()
        }

        then:
        rootMulti.file('hello.txt').assertExists().text == 'Hello world from :'
        rootMulti.file('x/hello.txt').assertExists().text == 'Hello world from :x'
        rootMulti.file('y/hello.txt').assertExists().text == 'Hello world from :y'
    }
}
