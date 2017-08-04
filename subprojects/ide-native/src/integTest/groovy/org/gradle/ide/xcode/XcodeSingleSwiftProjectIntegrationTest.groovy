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

package org.gradle.ide.xcode

import org.gradle.ide.xcode.fixtures.AbstractXcodeIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.SwiftHelloWorldApp

class XcodeSingleSwiftProjectIntegrationTest extends AbstractXcodeIntegrationSpec {
    def app = new SwiftHelloWorldApp()

    def "create xcode project executable"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        app.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeProjectWorkspaceSettings", ":xcodeScheme${rootProjectName}Executable", ":xcodeWorkspace", ":xcodeWorkspaceWorkspaceSettings", ":xcode")

        def project = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.sourceFiles*.name)
        project.targets.size() == 2
        project.assertTargetsAreTools()
        project.targets.every { it.productName == rootProjectName }

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "create xcode project library"() {
        given:
        buildFile << """
apply plugin: 'swift-library'
"""

        app.library.writeSources(file('src/main'))

        when:
        succeeds("xcode")

        then:
        executedAndNotSkipped(":xcodeProject", ":xcodeScheme${rootProjectName}SharedLibrary", ":xcodeProjectWorkspaceSettings", ":xcode")

        def project = xcodeProject("${rootProjectName}.xcodeproj").projectFile
        project.mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.sourceFiles*.name)
        project.targets.size() == 2
        project.assertTargetsAreDynamicLibraries()
        project.targets.every { it.productName == rootProjectName }

        assertProjectHasEqualsNumberOfGradleAndIndexTargets(project.targets)
    }

    def "new source files are included in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        app.library.writeSources(file('src/main'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.sourceFiles*.name)

        when:
        app.writeSources(file('src/main'))
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.sourceFiles*.name)
    }

    def "deleted source files are not included in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'
"""

        when:
        app.writeSources(file('src/main'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.sourceFiles*.name)

        when:
        file('src/main').deleteDir()
        app.library.writeSources(file('src/main'))
        succeeds('xcode')

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.library.sourceFiles*.name)
    }

    def "changing source location still include them in the project"() {
        given:
        buildFile << """
apply plugin: 'swift-executable'

def sourceTree = fileTree('Sources')
sourceTree.include('**/*.swift')
tasks.compileSwift.source(sourceTree)
"""

        when:
        app.writeSources(file('Sources'))
        succeeds("xcode")

        then:
        xcodeProject("${rootProjectName}.xcodeproj").projectFile
            .mainGroup.assertHasChildren(['Products', 'build.gradle'] + app.sourceFiles*.name)
    }
}
