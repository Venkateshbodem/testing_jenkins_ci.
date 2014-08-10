/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.sonar.runner

import groovy.json.JsonSlurper
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.AvailablePortFinder
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Shared

@Requires(TestPrecondition.JDK7_OR_EARLIER)
class SonarRunnerSmokeIntegrationTest extends AbstractIntegrationSpec {

    @Shared
    AvailablePortFinder portFinder = AvailablePortFinder.createPrivate()

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    @Rule
    TestResources testResources = new TestResources(temporaryFolder)

    @Rule
    SonarServerRule sonarServerRule = new SonarServerRule(tempDir, portFinder)

    def "execute 'sonarRunner' task"() {
        when:
        runSonarRunnerTask()

        then:
        noExceptionThrown()
        sonarServerRule.assertProjectPresent('org.gradle.test.sonar:SonarTestBuild')
    }

    private ExecutionResult runSonarRunnerTask() {
        executer
                .withArgument("-i")
                .withArgument("-Dsonar.host.url=http://localhost:9000")
                .withArgument("-Dsonar.jdbc.url=jdbc:h2:tcp://localhost:${sonarServerRule.databasePort}/sonar")
                .withArgument("-Dsonar.jdbc.username=sonar")
                .withArgument("-Dsonar.jdbc.password=sonar")
                // sonar.dynamicAnalysis is deprecated since SonarQube 4.3
                .withDeprecationChecksDisabled()
                .withTasks("sonarRunner").run()
    }
}


class SonarServerRule implements TestRule {

    private TestNameTestDirectoryProvider provider
    private AvailablePortFinder portFinder

    private int databasePort
    private Process sonarProcess

    String serverUrl = 'http://localhost:9000'

    SonarServerRule(TestNameTestDirectoryProvider provider, AvailablePortFinder portFinder) {
        this.provider = provider
        this.portFinder = portFinder
    }

    int getDatabasePort() {
        return databasePort
    }

    @Override
    Statement apply(Statement base, Description description) {
        return [ evaluate: {
            try {
                startServer()
                assertDbIsEmpty()
                base.evaluate()
            } finally {
                stopServer()
            }
        } ] as Statement
    }

    void startServer() {
        TestFile sonarHome = prepareSonarHomeFolder()

        ProcessBuilder processBuilder = new ProcessBuilder()
                .directory(sonarHome)
                .inheritIO()
                .command(
                    Jvm.current().getJavaExecutable().absolutePath,
                    '-XX:MaxPermSize=160m', '-Xmx512m', '-Djava.awt.headless=true',
                    '-Dfile.encoding=UTF-8', '-Djruby.management.enabled=false',
                    '-cp', 'lib/*:conf', 'org.sonar.application.StartServer'
        )

        sonarProcess = processBuilder.start()

        // Can't find another way to be sure the server is up
        Thread.sleep(2000)
        assert apiRequest('webservices/list').statusLine.statusCode < 400
    }

    private TestFile prepareSonarHomeFolder() {
        databasePort = portFinder.nextAvailable

        def classpath = ClasspathUtil.getClasspath(getClass().classLoader).collect() { new File(it.toURI()) }
        def zipFile = classpath.find { it.name ==~ "sonarqube.*\\.zip" }
        assert zipFile

        new AntBuilder().unzip(src: zipFile, dest: provider.testDirectory, overwrite: true)
        TestFile sonarHome = provider.testDirectory.file(zipFile.name - '.zip')

        sonarHome.file("conf/sonar.properties") << """\n
            sonar.jdbc.username=sonar
            sonar.jdbc.password=sonar
            sonar.jdbc.url=jdbc:h2:tcp://localhost:$databasePort/sonar
            sonar.embeddedDatabase.port=$databasePort
        """.stripIndent()
        sonarHome
    }

    void stopServer() {
        sonarProcess?.waitForOrKill(100)
    }

    def apiRequest(String path) {
        HttpClient httpClient = new DefaultHttpClient();
        def request = new HttpGet("$serverUrl/api/$path");
        httpClient.execute(request)
    }

    void assertDbIsEmpty() {
        assert getResources().empty
    }

    def getResources() {
        new JsonSlurper().parse(
                apiRequest('resources?format=json').entity.content
        )
    }

    void assertProjectPresent(String name) {
        assert getResources()*.key.contains(name)
    }
}
