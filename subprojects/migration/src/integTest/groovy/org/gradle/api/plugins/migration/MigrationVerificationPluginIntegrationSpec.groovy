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

package org.gradle.api.plugins.migration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.util.TextUtil

class MigrationVerificationPluginIntegrationSpec extends AbstractIntegrationSpec {
    @Rule TestResources testResources

    def compareArchives() {
        executer.withForkingExecuter()

        when:
        run("compare")

        then:
        looksLike output, """
Comparing build 'source build' with 'target build'
Comparing outputs of project ':'
Comparing archive 'testBuild.jar'
Archive entry 'org/gradle/ChangedClass.class': Size changed from 409 to 486
Archive entry 'org/gradle/DifferentCrcClass.class': CRC changed from \\d+ to \\d+
Archive entry 'org/gradle/SourceBuildOnlyClass.class' only exists in build 'source build'
Archive entry 'org/gradle/TargetBuildOnlyClass.class' only exists in build 'target build'
Finished comparing archive 'testBuild.jar'
Finished comparing outputs of project ':'
Finished comparing build 'source build' with 'target build'
        """
    }

    private void looksLike(text, pattern) {
        assert text =~ TextUtil.toPlatformLineSeparators(pattern.trim())
    }
}
