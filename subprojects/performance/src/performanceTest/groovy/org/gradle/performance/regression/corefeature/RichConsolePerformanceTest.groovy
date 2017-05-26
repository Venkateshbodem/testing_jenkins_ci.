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

package org.gradle.performance.regression.corefeature

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_MONOLITHIC_JAVA_PROJECT

class RichConsolePerformanceTest extends AbstractCrossVersionPerformanceTest {

    private static final List<String> CLEAN_ASSEMBLE_TASKS = ['clean', 'assemble']

    def setup() {
        runner.args << '--console=rich'
    }

    @Unroll
    def "execute #testProject with rich console"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = tasks
        runner.gradleOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.targetVersions = ['3.5-20170221000043+0000']

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                   | tasks
        LARGE_JAVA_MULTI_PROJECT      | CLEAN_ASSEMBLE_TASKS
        LARGE_MONOLITHIC_JAVA_PROJECT | CLEAN_ASSEMBLE_TASKS
        'bigNative'                   | CLEAN_ASSEMBLE_TASKS
        'withVerboseJUnit'            | ['cleanTest', 'test']
    }
}
