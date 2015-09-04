/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class RuleBasedTaskReferenceIntegrationTest extends AbstractIntegrationSpec implements WithRuleBasedTasks {

    def "a non-rule-source task can depend on a rule-source task"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }
        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a non-rule-source task can depend on one or more task of types created via both rule sources and old world container"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task oldClimber(type: ClimbTask) { }
        task customTask << { }

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':customTask', ':climbTask'])
    }

    def "a non-rule-source task can depend on a rule-source task when referenced via various constructs"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
                tasks.create("jumpTask", JumpTask) { }
                tasks.create("echoTask", EchoTask) { }
            }
        }
        apply type: Rules

        task customClimbTask << { }
        task customEchoTask << { }
        task customJumpTask << { }

        tasks.customClimbTask.dependsOn tasks.withType(ClimbTask)
        project.tasks.customEchoTask.dependsOn tasks.withType(EchoTask)
        tasks.getByPath(":customJumpTask").dependsOn tasks.withType(JumpTask)
        """

        when:
        succeeds('customClimbTask', 'customEchoTask', 'customJumpTask')

        then:
        result.executedTasks.containsAll([':customClimbTask', ':climbTask', ':customJumpTask', ':jumpTask', ':customEchoTask', ':echoTask'])
    }

    def "can depend on a rule-source task in a project which has already evaluated"() {
        given:
        settingsFile << 'include "sub1", "sub2"'
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }

        project("sub1") {
            apply type: Rules
        }

        project("sub2") {
            task customTask  {
                dependsOn project(":sub1").tasks.withType(ClimbTask)
            }
        }
        """

        when:
        succeeds('sub2:customTask')

        then:
        result.executedTasks.containsAll([':sub2:customTask', ':sub1:climbTask'])
    }

    def "can depend on a rule-source task after a project has been evaluated"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) { }
            }
        }
        apply type: Rules

        task customTask << { }

        afterEvaluate {
            customTask.dependsOn tasks.withType(ClimbTask)
        }
        """

        when:
        succeeds('customTask')

        then:
        result.executedTasks.containsAll([':customTask', ':climbTask'])
    }

    def "a build failure occurs when depending on a rule task with failing configuration"() {
        given:
        buildFile << """
        ${ruleBasedTasks()}

        class Rules extends RuleSource {
            @Mutate
            void addTasks(ModelMap<Task> tasks) {
                tasks.create("climbTask", ClimbTask) {
                    throw new GradleException("Bang")
                }
            }
        }
        apply type: Rules

        task customTask << { }

        customTask.dependsOn tasks.withType(ClimbTask)
        """

        when:
        fails('customTask')

        then:
        failure.assertHasCause('Bang')
        failure.assertHasDescription('Exception thrown while executing model rule: Rules#addTasks > create(climbTask)')
    }
}
