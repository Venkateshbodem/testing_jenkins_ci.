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

package org.gradle.internal.snapshot

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

import java.nio.file.Paths

class SnapshottingServiceIntegrationTest extends AbstractIntegrationSpec {

    def "can inject snapshotting service into plugin"() {
        given:
        def inputFile = file("input.txt") << """
            Some text
        """

        def pluginBuilder = new PluginBuilder(file("buildSrc"))
        pluginBuilder.addPlugin("""
            def input = ($Paths.name).get("$inputFile")
            def projectInternal = (org.gradle.api.internal.project.ProjectInternal)project
            def serviceRegistry = projectInternal.getServices()

            def snapshottingService = serviceRegistry.get($SnapshottingService.name)
            def snapshot = snapshottingService.snapshotFor(input)

            println("Snapshot for input file $inputFile.name is \$snapshot")
        """
        )
        pluginBuilder.generateForBuildSrc()

        buildFile << """
            plugins {
                id 'test-plugin'
            }
        """

        when:
        succeeds "help"

        then:
        outputContains"Snapshot for input file"
    }
}
