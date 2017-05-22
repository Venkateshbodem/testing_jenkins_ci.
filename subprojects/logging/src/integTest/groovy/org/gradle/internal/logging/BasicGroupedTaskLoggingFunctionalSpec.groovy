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

package org.gradle.internal.logging

import org.gradle.integtests.fixtures.AbstractConsoleFunctionalSpec

class BasicGroupedTaskLoggingFunctionalSpec extends AbstractConsoleFunctionalSpec {

    def "multi-project build tasks logs are grouped"() {
        given:
        settingsFile << "include '1', '2', '3'"

        buildFile << """
            subprojects {
                task log { doFirst { logger.quiet "Output from " + project.name } }
            }
        """

        when:
        succeeds('log')

        then:
        taskOutputFrom(':1:log') == "Output from 1"
        taskOutputFrom(':2:log') == "Output from 2"
        taskOutputFrom(':3:log') == "Output from 3"
    }

    def "logs at execution time are grouped"() {
        given:
        buildFile << """
            task log { 
                logger.quiet 'Logged during configuration'
                doFirst { 
                    logger.quiet 'First line of text' 
                    logger.quiet 'Second line of text' 
                } 
            }
        """

        when:
        succeeds('log')

        then:
        taskOutputFrom(':log') == "First line of text\nSecond line of text"
    }

    def "system out and err gets grouped"() {
        given:
        buildFile << """
            task log { 
                logger.quiet 'Logged during configuration'
                doFirst { 
                    System.out.println("Standard out")
                    System.err.println("Standard err")
                } 
            }
        """

        when:
        succeeds('log')

        then:
        taskOutputFrom(':log') == "Standard out\nStandard err"
    }


    def "grouped output is displayed for failed tasksd"() {
        given:
        buildFile << """task log { 
                        logger.quiet 'Logged during configuration'
                        doFirst { 
                            logger.quiet 'First line of text' 
                            logger.quiet '' 
                            logger.quiet '' 
                            logger.quiet 'Last line of text' 
                            throw new GradleException('Forced failure')
                        } 
                    }"""
        when:
        executer.withStackTraceChecksDisabled()
        fails('log')

        then:
        taskOutputFrom(':log') == "First line of text\n\n\nLast line of text"
    }
}
