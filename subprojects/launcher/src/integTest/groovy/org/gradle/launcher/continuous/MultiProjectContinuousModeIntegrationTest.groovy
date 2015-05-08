/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.jvm.IncrementalTestJvmComponent
import org.gradle.language.fixtures.BadJavaComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.test.fixtures.file.TestFile

/**
 * Runs tests from base class on the root project
 */
class MultiProjectContinuousModeIntegrationTest extends AbstractContinuousModeExecutionIntegrationTest {
    IncrementalTestJvmComponent app = new TestJavaComponent()
    String compileTask = "compileJava"
    List subprojects = [ "sub1", "sub2" ]
    TestFile sourceDir = file("src/main")

    List sourceDirs() {
        [ sourceDir ] + subprojects.collect { projectDir ->
            file("${projectDir}/src/main")
        }
    }
    def setup() {
        buildFile << """
        allprojects {
            apply plugin: 'java'
        }
        """

        settingsFile << """
        include ${subprojects.collect({"\"$it\""}).join(", ")}
        """
    }

    void validSource() {
        sourceFiles = app.writeSources(sourceDir)
        resourceFiles = app.writeResources(sourceDir.createDir("resources"))
    }

    void invalidSource() {
        sourceFiles = new BadJavaComponent().writeSources(sourceDir)
        resourceFiles = new BadJavaComponent().writeResources(sourceDir.createDir("resources"))
    }

    void changeSource() {
        app.changeSources(sourceFiles)
    }

    void createSource() {
        app.writeAdditionalSources(sourceDir)
    }

    TestFile createIgnoredFile() {
        app.createIgnoredFileInSources(sourceDir)
    }

    void deleteSource() { sourceFiles[0].delete() }
}
