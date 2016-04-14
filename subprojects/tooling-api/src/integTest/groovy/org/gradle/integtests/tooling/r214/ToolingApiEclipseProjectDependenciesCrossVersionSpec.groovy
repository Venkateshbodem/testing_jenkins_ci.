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
package org.gradle.integtests.tooling.r214
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProjectDependency
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

@ToolingApiVersion(">=2.14")
@TargetGradleVersion(">=1.0")
class ToolingApiEclipseProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "can build the eclipse project dependencies for a java project"() {
        projectDir.file('settings.gradle').text = '''
include "a", "a:b"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
}
project(':a') {
    dependencies {
        compile project(':')
        compile project(':a:b')
    }
}
'''

        when:
        HierarchicalEclipseProject minimalModel = loadToolingModel(HierarchicalEclipseProject)

        then:
        HierarchicalEclipseProject minimalProject = minimalModel.children[0]

        minimalProject.projectDependencies.size() == 2

        EclipseProjectDependency rootDependency = minimalProject.projectDependencies.find { it.path == 'root' }
        rootDependency != null
        rootDependency.targetProject == minimalModel
        rootDependency.targetProjectDirectory == projectDir

        EclipseProjectDependency otherDependency = minimalProject.projectDependencies.find { it.path == 'b' }
        otherDependency != null
        otherDependency.targetProject == minimalProject.children[0]
        otherDependency.targetProjectDirectory == projectDir.file("a", "b")
    }
}
