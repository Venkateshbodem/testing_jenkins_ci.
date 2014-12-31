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

package org.gradle.integtests.resolve.aws.s3

import org.gradle.test.fixtures.server.s3.MavenS3Module

class MavenS3RepoResolveIntegrationTest extends AbstractS3DependencyResolutionTest {

    @Override
    String getBucket() {
        return 'tests3bucket'
    }

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def "should not download artifacts when already present in maven home"() {
        setup:
        String artifactVersion = "1.85"
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
        remoteModule.publish()

        m2Installation.generateGlobalSettingsFile()
        def localModule = m2Installation.mavenRepo().module("org.gradle", "test", artifactVersion).publish()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        remoteModule.pom.expectMetadataRetrieve()
        remoteModule.pomSha1.expectDownload()
        remoteModule.artifact.expectMetadataRetrieve()
        remoteModule.artifactSha1.expectDownload()

        when:
        using m2Installation

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsCopyOf(remoteModule.artifactFile)
        localModule.pomFile.assertIsCopyOf(remoteModule.pomFile)
        file('libs/test-1.85.jar').assertIsCopyOf(remoteModule.artifactFile)

        and:
        assertLocallyAvailableLogged(remoteModule.pom, remoteModule.artifact)
    }


    def "should download artifacts when maven local artifacts are different to remote "() {
        setup:
        String artifactVersion = "1.85"
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
        remoteModule.publish()

        m2Installation.generateGlobalSettingsFile()
        def localModule = m2Installation.mavenRepo().module("org.gradle", "test", artifactVersion).publishWithChangedContent()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        remoteModule.pom.expectMetadataRetrieve()
        remoteModule.pomSha1.expectDownload()
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectMetadataRetrieve()
        remoteModule.artifactSha1.expectDownload()
        remoteModule.artifact.expectDownload()

        when:
        using m2Installation
        run 'retrieve'

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsDifferentFrom(remoteModule.artifactFile)
        localModule.pomFile.assertIsDifferentFrom(remoteModule.pomFile)
        file('libs/test-1.85.jar').assertIsCopyOf(remoteModule.artifactFile)
    }
}
