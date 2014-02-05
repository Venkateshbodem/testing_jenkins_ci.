/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.UnsupportedMethodException

@ToolingApiVersion('>=1.12')
@TargetGradleVersion('>=1.12')
class PublicationsCrossVersionSpec extends ToolingApiSpecification {
    def "project without any configured publications"() {
        buildFile << "apply plugin: 'java'"

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.empty
    }

    def "Ivy repository based publication"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "base"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        ivy { url uri("\$buildDir/ivy-repo") }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 1
        with(project.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    def "Maven repository based publication with coordinates inferred from project"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "maven"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("\$buildDir/maven-repo"))
        }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 1
        with(project.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    def "Maven repository based publication with coordinates inferred from POM configuration"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
                """
apply plugin: "maven"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("\$buildDir/maven-repo"))
            pom.groupId = "test.groupId"
            pom.artifactId = "test.artifactId"
            pom.version = "1.1"
        }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 1
        with(project.publications.iterator().next()) {
            id.group == "test.groupId"
            id.name == "test.artifactId"
            id.version == "1.1"
        }
    }

    def "publishing.publications based publication"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
                """
apply plugin: "ivy-publish"
apply plugin: "maven-publish"
apply plugin: "java"

version = 1.0
group = "test.group"

publishing {
    repositories {
        ivy { url uri("\$buildDir/ivy-repo") }
        maven { url uri("\$buildDir/maven-repo") }
    }
    publications {
        mainIvy(IvyPublication) {
            from components.java
            organisation 'test.org'
            module 'test-module'
            revision '1.1'
        }
        mainMaven(MavenPublication) {
            from components.java
            groupId 'test.groupId'
            artifactId 'test-artifactId'
            version '1.2'
        }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 2

        and:
        def pub1 = project.publications.find { it.id.group == "test.org" }
        pub1 != null
        pub1.id.name == "test-module"
        pub1.id.version == "1.1"

        and:
        def pub2 = project.publications.find { it.id.group == "test.groupId" }
        pub2 != null
        pub2.id.name == "test-artifactId"
        pub2.id.version == "1.2"
    }

    @ToolingApiVersion('current')
    @TargetGradleVersion('<1.12')
    def "decent error message for Gradle version that doesn't expose publications"() {
        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }
        project.publications

        then:
        UnsupportedMethodException e = thrown()
        e.message.contains("Unsupported method: GradleProject.getPublications()")
    }
}
