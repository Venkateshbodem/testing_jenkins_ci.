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


package org.gradle.integtests.tooling.r72

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.util.Exceptions

@Issue('https://github.com/gradle/gradle/issues/9339')
@TargetGradleVersion(">=7.2")
class JavaVersionCrossVersionTest extends ToolingApiSpecification {

    def setup() {
        buildFile << """
            task myTask {
                doLast {
                    throw new RuntimeException("Boom")
                }
            }
        """
    }

    @Requires(TestPrecondition.JDK11_OR_LATER)
    @IgnoreIf({ AvailableJavaHomes.jdk8 == null })
    def "can deserialize failures with post-jigsaw client and pre-jigsaw daemon"() {
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk8.javaHome.absolutePath)

        when:
        toolingApi.withConnection { connection ->
            connection.newBuild().forTasks('myTask').run()
        }

        then:
        GradleConnectionException e = thrown()
        def rootCause = Exceptions.getRootCause(e)
        rootCause instanceof RuntimeException
        rootCause.message == "Boom"
        rootCause.stackTrace.find {
            it.fileName.endsWith("build.gradle") && it.lineNumber == 4
        }
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    @IgnoreIf({ AvailableJavaHomes.jdk11 == null })
    def "can deserialize failures with pre-jigsaw client and post-jigsaw daemon"() {
        projectDir.file("gradle.properties").writeProperties("org.gradle.java.home": AvailableJavaHomes.jdk11.javaHome.absolutePath)

        when:
        toolingApi.withConnection { connection ->
            connection.newBuild().forTasks('myTask').run()
        }

        then:
        GradleConnectionException e = thrown()
        def rootCause = Exceptions.getRootCause(e)
        rootCause instanceof RuntimeException
        rootCause.message == "Boom"
        rootCause.stackTrace.find {
            it.fileName.endsWith("build.gradle") && it.lineNumber == 4
        }
    }
}
