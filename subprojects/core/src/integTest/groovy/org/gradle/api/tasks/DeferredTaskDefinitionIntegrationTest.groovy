/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec


class DeferredTaskDefinitionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << '''
            class SomeTask extends DefaultTask {
                SomeTask() {
                    println("Create ${path}")
                }
            }
            class SomeOtherTask extends DefaultTask {
                SomeOtherTask() {
                    println("Create ${path}")
                }
            }
        '''
    }

    def "task is created and configured when included directly in task graph"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.createLater("task2", SomeTask) {
                println "Configure ${path}"
            }
        '''

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        result.assertNotOutput(":task2")

        when:
        run("task2")

        then:
        outputContains("Create :task2")
        outputContains("Configure :task2")
        result.assertNotOutput(":task1")
    }

    def "task is created and configured when referenced as a task dependency"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.createLater("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced as task dependency via task provider"() {
        buildFile << '''
            def t1 = tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn t1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")

        when:
        run("task2")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured when referenced during configuration"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            // Eager
            tasks.create("task2", SomeTask) {
                println "Configure ${path}"
                dependsOn task1
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Create :task2")
        outputContains("Configure :task2")
    }

    def "task is created and configured eagerly when referenced using withType(type, action)"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.createLater("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.create("other")
            tasks.withType(SomeTask) {
                println "Matched ${path}"
            }
        '''

        when:
        run("other")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Matched :task1")
        result.assertNotOutput("task2")
    }

    def "build logic can configure each task only when required"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.createLater("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.configureEachLater {
                println "Received ${path}"
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        outputContains("Received :other")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Received :other")
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
    }

    def "build logic can configure each task of a given type only when required"() {
        buildFile << '''
            tasks.createLater("task1", SomeTask) {
                println "Configure ${path}"
            }
            tasks.createLater("task2", SomeOtherTask) {
                println "Configure ${path}"
            }
            tasks.configureEachLater(SomeTask) {
                println "Received ${path}"
            }
            tasks.create("other")
        '''

        when:
        run("other")

        then:
        result.assertNotOutput("Received")
        result.assertNotOutput("task1")
        result.assertNotOutput("task2")

        when:
        run("task1")

        then:
        outputContains("Create :task1")
        outputContains("Configure :task1")
        outputContains("Received :task1")
        result.assertNotOutput("task2")
    }
}
