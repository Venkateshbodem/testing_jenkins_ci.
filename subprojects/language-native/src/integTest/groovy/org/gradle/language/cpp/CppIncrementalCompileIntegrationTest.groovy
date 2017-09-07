/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import groovy.io.FileType
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalCppStaleCompileOutputApp
import org.gradle.nativeplatform.fixtures.app.IncrementalCppStaleCompileOutputLib

class CppIncrementalCompileIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def "removes stale object files for executable"() {
        settingsFile << "rootProject.name = 'app'"
        def app = new IncrementalCppStaleCompileOutputApp()

        given:
        app.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-executable'
         """

        and:
        succeeds "assemble"
        app.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugCpp", ":linkDebug", ":installDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugCpp", ":linkDebug", ":installDebug", ":assemble")

        files("build/obj/main")*.name as Set == app.expectedAlternateIntermediateFilenames
        executable("build/exe/main/debug/App").assertExists()
        installation("build/install/main/debug").exec().out == app.expectedOutput
    }

    def "removes stale object files for library"() {
        def lib = new IncrementalCppStaleCompileOutputLib()
        settingsFile << "rootProject.name = 'hello'"

        given:
        lib.writeToProject(testDirectory)

        and:
        buildFile << """
            apply plugin: 'cpp-library'
         """

        and:
        succeeds "assemble"
        lib.applyChangesToProject(testDirectory)

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(":compileDebugCpp", ":linkDebug", ":assemble")
        result.assertTasksNotSkipped(":compileDebugCpp", ":linkDebug", ":assemble")

        files("build/obj/main")*.name as Set == lib.expectedAlternateIntermediateFilenames
        sharedLibrary("build/lib/main/debug/hello").assertExists()
    }


    private Set<File> files(Object path) {
        File directory = file(path)
        directory.assertIsDir()

        def result = [] as Set
        directory.eachFileRecurse(FileType.FILES) {
            result += it
        }

        return result
    }
}
