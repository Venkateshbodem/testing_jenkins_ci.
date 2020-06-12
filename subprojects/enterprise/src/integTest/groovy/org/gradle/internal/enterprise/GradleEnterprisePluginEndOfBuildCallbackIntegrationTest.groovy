/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

@Unroll
class GradleEnterprisePluginEndOfBuildCallbackIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        plugin.publishDummyPlugin(executer)
        buildFile << """
            task t
            task f { doLast { throw new RuntimeException("failed") } }
        """
    }

    def "end of build listener is notified on success"() {
        when:
        succeeds "t"

        then:
        plugin.assertEndOfBuildWithFailure(output, null)

        when:
        succeeds "t"

        then:
        plugin.assertEndOfBuildWithFailure(output, null)
    }

    def "end of build listener is notified on failure"() {
        when:
        fails "f"

        then:
        plugin.assertEndOfBuildWithFailure(output, "org.gradle.internal.exceptions.LocationAwareException: Build file")

        when:
        fails "f"

        then:
        // Note: we test less of the exception here because it's different in a build where configuration came from cache
        // In the non cache case, the exception points to the build file. In the from cache case it does not.
        plugin.assertEndOfBuildWithFailure(output, "org.gradle.internal.exceptions.LocationAwareException")
    }

}
