/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.InitScriptExecuterFixture
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

class ForkGosuCompileInDaemonModeFixture extends InitScriptExecuterFixture {
    ForkGosuCompileInDaemonModeFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    @Override
    String initScriptContent() {
        return """
allprojects {
    tasks.withType(GosuCompile) {
        gosuCompileOptions.fork = true
        gosuCompileOptions.useAnt = false
        if(!JavaVersion.current().isJava8Compatible()) {
            throw new GradleException("Gosu requires Java 8")
        }
    }
    tasks.withType(GosuDoc) {
        doFirst {
            throw new GradleException("Can't execute gosudoc while testing with the daemon due to permgen exhaustion")
        }
    }
}
"""
    }

    @Override
    Statement apply(Statement base, FrameworkMethod method, Object target) {
        if (GradleContextualExecuter.isDaemon()) {
            return super.apply(base, method, target)
        } else {
            return base;
        }
    }
}
