/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.junit.AbstractJUnitIntegrationTest

import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ JUnitCoverage.JUNIT_JUPITER })
class JUnitJupiterJUnitIntegrationTest extends AbstractJUnitIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${dependencyVersion}\", org.junit.jupiter.api.Test.class.getPackage().getImplementationVersion());"
    }

    @Override
    String getTestFrameworkSuiteImports() {
        return """
            import org.junit.platform.suite.api.SelectClasses;
            import org.junit.platform.suite.api.Suite;
        """.stripIndent()
    }

    @Override
    String getTestFrameworkSuiteAnnotations(String classes) {
        return """
            @SelectClasses({ ${classes} })
            @Suite
        """.stripIndent()
    }

    @Override
    String getTestFrameworkSuiteDependencies() {
        return """
            testCompileOnly 'org.junit.platform:junit-platform-suite-api:${JUnitCoverage.LATEST_PLATFORM_VERSION}'
            testRuntimeOnly 'org.junit.platform:junit-platform-suite-engine:${JUnitCoverage.LATEST_PLATFORM_VERSION}'
        """.stripIndent()
    }

    @Override
    String getAssertionError() {
        return "org.opentest4j.AssertionFailedError"
    }

    @Override
    TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName) {
        return testResult.testClassStartsWith('Gradle Test Executor')
            .assertTestFailed("failed to execute tests", containsString("Could not execute test class '${testClassName}'"))
    }
}
