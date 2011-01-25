/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

class EclipseIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void canCreateAndDeleteMetaData() {
        File buildFile = testFile("master/build.gradle")
        usingBuildFile(buildFile).run()
    }

    @Test
    void sourceEntriesInClasspathFileAreSortedAsPerUsualConvention() {
        def expectedOrder = [
            "src/main/java",
            "src/main/groovy",
            "src/main/resources",
            "src/test/java",
            "src/test/groovy",
            "src/test/resources",
            "src/integTest/java",
            "src/integTest/groovy",
            "src/integTest/resources"
        ]

        expectedOrder.each { testFile(it).mkdirs() }

        def buildFile = testFile("build.gradle")
        buildFile << """
apply plugin: "java"
apply plugin: "groovy"
apply plugin: "eclipse"

sourceSets {
    integTest {
        resources { srcDir "src/integTest/resources" }
        java { srcDir "src/integTest/java" }
        groovy { srcDir "src/integTest/groovy" }
    }
}
        """

        usingBuildFile(buildFile).withTasks("eclipse").run()

        def classpath = parseClasspathFile()
        def sourceEntries = findEntries(classpath, "src")
        assert sourceEntries*.@path == expectedOrder
    }

    @Test
    void outputDirDefaultsToEclipseDefault() {
        def buildFile = testFile("build.gradle")
        buildFile << "apply plugin: 'java'; apply plugin: 'eclipse'"

        usingBuildFile(buildFile).withTasks("eclipse").run()

        def classpath = parseClasspathFile()

        def outputs = findEntries(classpath, "output")
        assert outputs*.@path == ["bin"]

        def sources = findEntries(classpath, "src")
        sources.each { assert !it.attributes().containsKey("path") }
    }

    @Test
    void canHandleCircularModuleDependencies() {
        def repoDir = file("repo")
        def artifact1 = publishArtifact(repoDir, "myGroup", "myArtifact1", "myArtifact2")
        def artifact2 = publishArtifact(repoDir, "myGroup", "myArtifact2", "myArtifact1")

        def buildFile = testFile("build.gradle")
        buildFile << """
apply plugin: "java"
apply plugin: "eclipse"

repositories {
    mavenRepo urls: "file://$repoDir.absolutePath"
}

dependencies {
    compile "myGroup:myArtifact1:1.0"
}
        """

        usingBuildFile(buildFile).withTasks("eclipse").run()

        def classpath = parseClasspathFile()
        def libs = findEntries(classpath, "lib")
        assert libs.size() == 2
        assert libs*.@path*.text().collect { new File(it).name } as Set == [artifact1.name, artifact2.name] as Set
    }

    private parseClasspathFile(print = false) {
        parseXmlFile(".classpath", print)
    }

    private parseProjectFile(print = false) {
        parseXmlFile(".project", print)
    }

    private findEntries(classpath, kind) {
        classpath.classpathentry.findAll { it.@kind == kind }
    }
}