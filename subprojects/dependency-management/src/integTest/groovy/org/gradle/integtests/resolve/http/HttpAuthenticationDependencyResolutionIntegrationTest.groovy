/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve.http
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.authentication.http.DigestAuthentication
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.hamcrest.Matchers
import spock.lang.Unroll

class HttpAuthenticationDependencyResolutionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    static String badCredentials = "credentials{username 'testuser'; password 'bad'}"

    @Unroll
    def "can resolve dependencies using #authSchemeName scheme from #authScheme authenticated HTTP ivy repository"() {
        given:
        def moduleA = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        ivyHttpRepo.module('group', 'projectB', '2.1').publish()
        ivyHttpRepo.module('group', 'projectB', '2.2').publish()
        def moduleB = ivyHttpRepo.module('group', 'projectB', '2.3').publish()
        ivyHttpRepo.module('group', 'projectB', '3.0').publish()
        and:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"

        credentials {
            password 'password'
            username 'username'
        }

        ${authSchemeType}
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
    compile 'group:projectB:2.+'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar','projectB-2.3.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        moduleA.ivy.expectGet('username', 'password')
        moduleA.jar.expectGet('username', 'password')
        server.expectGetDirectoryListing("/repo/group/projectB/", 'username', 'password', moduleB.backingModule.moduleDir.parentFile)
        moduleB.ivy.expectGet('username', 'password')
        moduleB.jar.expectGet('username', 'password')

        then:
        succeeds('listJars')
        and:
        server.authenticationOrder.asList() == authenticationOrder

        where:
        authSchemeName     | authSchemeType                                                                | authScheme                             | authenticationOrder
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HttpServer.AuthScheme.BASIC            | ['Basic']
        'digest'           | 'authentication { auth(DigestAuthentication) }'                               | HttpServer.AuthScheme.DIGEST           | ['None', 'Digest']
        'default'          | ''                                                                            | HttpServer.AuthScheme.BASIC            | ['None', 'Basic']
        'default'          | ''                                                                            | HttpServer.AuthScheme.DIGEST           | ['None', 'Digest']
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HttpServer.AuthScheme.PREEMPTIVE_BASIC | ['Basic']
        'basic and digest' | 'authentication { basic(BasicAuthentication)\ndigest(DigestAuthentication) }' | HttpServer.AuthScheme.DIGEST           | ['Basic', 'Digest']
    }

    @Unroll
    public void "can resolve dependencies from #authScheme authenticated HTTP maven repository"() {
        given:
        def moduleA = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        mavenHttpRepo.module('group', 'projectB', '2.0').publish()
        mavenHttpRepo.module('group', 'projectB', '2.2').publish()
        def moduleB = mavenHttpRepo.module('group', 'projectB', '2.3').publish()
        mavenHttpRepo.module('group', 'projectB', '3.0').publish()
        def moduleC = mavenHttpRepo.module('group', 'projectC', '3.1-SNAPSHOT').publish()
        def moduleD = mavenHttpRepo.module('group', 'projectD', '4-SNAPSHOT').withNonUniqueSnapshots().publish()
        and:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"

        credentials {
            password 'password'
            username 'username'
        }

        ${authSchemeType}
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
    compile 'group:projectB:2.+'
    compile 'group:projectC:3.1-SNAPSHOT'
    compile 'group:projectD:4-SNAPSHOT'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-2.3.jar', 'projectC-3.1-SNAPSHOT.jar', 'projectD-4-SNAPSHOT.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        moduleA.pom.expectGet('username', 'password')
        moduleA.artifact.expectGet('username', 'password')
        moduleB.rootMetaData.expectGet('username', 'password')
        moduleB.pom.expectGet('username', 'password')
        moduleB.artifact.expectGet('username', 'password')

        moduleC.metaData.expectGet('username', 'password')
        moduleC.pom.expectGet('username', 'password')
        moduleC.artifact.expectGet('username', 'password')

        moduleD.metaData.expectGet('username', 'password')
        moduleD.pom.expectGet('username', 'password')
        moduleD.artifact.expectGet('username', 'password')

        then:
        succeeds('listJars')
        and:
        server.authenticationOrder.asList() == authenticationOrder

        where:
        authSchemeName     | authSchemeType                                                                | authScheme                             | authenticationOrder
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HttpServer.AuthScheme.BASIC            | ['Basic']
        'digest'           | 'authentication { auth(DigestAuthentication) }'                               | HttpServer.AuthScheme.DIGEST           | ['None', 'Digest']
        'default'          | ''                                                                            | HttpServer.AuthScheme.BASIC            | ['None', 'Basic']
        'default'          | ''                                                                            | HttpServer.AuthScheme.DIGEST           | ['None', 'Digest']
        'basic'            | 'authentication { auth(BasicAuthentication) }'                                | HttpServer.AuthScheme.PREEMPTIVE_BASIC | ['Basic']
        'basic and digest' | 'authentication { basic(BasicAuthentication)\ndigest(DigestAuthentication) }' | HttpServer.AuthScheme.DIGEST           | ['Basic', 'Digest']
    }

    @Unroll
    def "reports failure resolving with #credsName credentials from #authScheme authenticated HTTP ivy repository"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
        $creds
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = authScheme
        server.allowGetOrHead('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | credsName | creds
        HttpServer.AuthScheme.BASIC  | 'missing'   | ''
        HttpServer.AuthScheme.DIGEST | 'missing'   | ''
        HttpServer.AuthScheme.BASIC  | 'bad'     | badCredentials
        HttpServer.AuthScheme.DIGEST | 'bad'     | badCredentials
    }

    @Unroll
    def "reports failure resolving with #credsName credentials from #authScheme authenticated HTTP maven repository"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
       $creds
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = authScheme
        module.pom.allowGetOrHead('username', 'password')

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | credsName | creds
        HttpServer.AuthScheme.BASIC  | 'missing'   | ''
        HttpServer.AuthScheme.DIGEST | 'missing'   | ''
        HttpServer.AuthScheme.BASIC  | 'bad'     | badCredentials
        HttpServer.AuthScheme.DIGEST | 'bad'     | badCredentials
    }

    @Unroll
    def "reports failure resolving with #configuredAuthScheme from #authScheme authenticated HTTP ivy repository"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
        credentials {
            username = 'username'
            password = 'password'
        }

        authentication {
            auth(${configuredAuthScheme})
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = authScheme
        server.allowGetOrHead('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | configuredAuthScheme
        HttpServer.AuthScheme.BASIC  | DigestAuthentication.class.getSimpleName()
        HttpServer.AuthScheme.DIGEST | BasicAuthentication.class.getSimpleName()
    }

    @Unroll
    def "reports failure resolving with #configuredAuthScheme from #authScheme authenticated HTTP maven repository"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
        credentials {
            username = 'username'
            password = 'password'
        }

        authentication {
            auth(${configuredAuthScheme})
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = authScheme
        module.pom.allowGetOrHead('username', 'password')

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme                   | configuredAuthScheme
        HttpServer.AuthScheme.BASIC  | DigestAuthentication.class.getSimpleName()
        HttpServer.AuthScheme.DIGEST | BasicAuthentication.class.getSimpleName()
    }

    def "fails resolving from preemptive authenticated HTTP ivy repository"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
        credentials {
            username = 'username'
            password = 'password'
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = HttpServer.AuthScheme.PREEMPTIVE_BASIC
        server.allowGetOrHead('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)
        server.allowGetOrHead('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.jarFile)

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Could not find group:projectA:1.2'))
    }

    def "fails resolving from preemptive authenticated HTTP maven repository"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
        credentials {
            username = 'username'
            password = 'password'
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        and:
        server.authenticationScheme = HttpServer.AuthScheme.PREEMPTIVE_BASIC
        module.pom.allowGetOrHead('username', 'password')
        module.artifact.allowGetOrHead('username', 'password')

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Could not find group:projectA:1.2'))
    }
}
