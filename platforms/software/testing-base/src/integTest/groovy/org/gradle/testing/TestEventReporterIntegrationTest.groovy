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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestFile

class TestEventReporterIntegrationTest extends AbstractIntegrationSpec {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits build operations for custom test"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                   try (def reporter = testEventReporterFactory.createTestEventReporter("Custom test root")) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is a test output on stderr")
                                 myTest.succeeded(Instant.now())
                            }
                            try (def myTest = mySuite.reportTest("MyTestInternal2", "My failing test :(")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdErr, "Some text on stderr")
                                 myTest.failed(Instant.now(), "Test framework failure")
                            }
                            mySuite.failed(Instant.now())
                       }
                       reporter.failed(Instant.now())
                   }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        fails "customTest"

        then: "threw VerificationException"
        failure.assertHasCause("Test(s) failed.")

        then: "test build operations are emitted in expected hierarchy"
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        def rootTestOpDetails = rootTestOp.details as Map<String, Map<String, ?>>
        (rootTestOpDetails.testDescriptor.name as String).startsWith("Custom test root")
        rootTestOpDetails.testDescriptor.className == null
        rootTestOpDetails.testDescriptor.composite

        def suiteTestOps = operations.children(rootTestOp, ExecuteTestBuildOperationType)
        suiteTestOps.size() == 1
        def suiteTestOpDetails = suiteTestOps[0].details as Map<String, Map<String, ?>>
        (suiteTestOpDetails.testDescriptor.name as String).startsWith("My Suite")
        suiteTestOpDetails.testDescriptor.className == null
        suiteTestOpDetails.testDescriptor.composite

        def firstLevelTestOps = operations.children(suiteTestOps[0], ExecuteTestBuildOperationType).sort {
            (it.details as Map<String, TestDescriptorInternal>).testDescriptor.name
        }
        firstLevelTestOps.size() == 2
        def firstLevelTestOpDetails = firstLevelTestOps*.details as List<Map<String, Map<String, ?>>>
        firstLevelTestOpDetails*.testDescriptor.name == ["MyTestInternal", "MyTestInternal2"]
        firstLevelTestOpDetails*.testDescriptor.displayName == ["My test!", "My failing test :("]
        firstLevelTestOpDetails*.testDescriptor.className == [null, null]
        firstLevelTestOpDetails*.testDescriptor.composite == [false, false]

        def firstTestOutputProgress = firstLevelTestOps[0].progress
        firstTestOutputProgress.size() == 2
        def firstTestOutputs = firstTestOutputProgress*.details.output as List<Map<String, ?>>
        firstTestOutputs[0].destination == "StdOut"
        firstTestOutputs[0].message == "This is a test output on stdout"
        firstTestOutputs[1].destination == "StdErr"
        firstTestOutputs[1].message == "This is a test output on stderr"

        def secondTestOutputProgress = firstLevelTestOps[1].progress
        secondTestOutputProgress.size() == 1
        def secondTestOutputs = secondTestOutputProgress[0].details.output as Map<String, ?>
        secondTestOutputs.destination == "StdErr"
        secondTestOutputs.message == "Some text on stderr"
    }

    def "captures String metadata for custom test"() {
        given:
        singleCustomTestRecordingMetadata("my key", "'my value'")

        when:
        succeeds "customTest"

        then: "metadata is retrievable from build operations"
        List<BuildOperationRecord.Progress> testMetadata = getMetadataForOnlyTest()
        testMetadata.size() == 1
        def firstTestMetadataDetails = testMetadata*.details.metadata as List<Map<String, ?>>
        firstTestMetadataDetails.size() == 1
        firstTestMetadataDetails[0]["key"] == "my key"
        firstTestMetadataDetails[0]["value"] == "my value"
    }

    def "captures List metadata for custom test"() {
        given:
        singleCustomTestRecordingMetadata("my key", "[1, 2, 3]")

        when:
        succeeds "customTest"

        then: "metadata is retrievable from build operations"
        List<BuildOperationRecord.Progress> testMetadata = getMetadataForOnlyTest()
        testMetadata.size() == 1
        def firstTestMetadataDetails = testMetadata*.details.metadata as List<Map<String, ?>>
        firstTestMetadataDetails.size() == 1
        firstTestMetadataDetails[0]["key"] == "my key"
        firstTestMetadataDetails[0]["value"] == [1, 2, 3]
    }

    @SuppressWarnings(['UnnecessaryQualifiedReference', 'GroovyResultOfObjectAllocationIgnored'])
    def "captures custom serializable object metadata for custom test"() {
        given:
        buildFile("""
            class TestType implements org.gradle.internal.operations.trace.CustomOperationTraceSerialization {
                private int field

                TestType(int field) {
                    this.field = field
                }

                @Override
                Object getCustomOperationTraceSerializableModel() {
                    return ["my custom serializable type", "with some values", "in a list", field]
                }
            }
        """)

        singleCustomTestRecordingMetadata("my key", """new TestType(2024)""")

        when:
        succeeds "customTest"

        then: "metadata is retrievable from build operations"
        List<BuildOperationRecord.Progress> testMetadata = getMetadataForOnlyTest()
        testMetadata.size() == 1
        def firstTestMetadataDetails = testMetadata*.details.metadata as List<Map<String, ?>>
        firstTestMetadataDetails.size() == 1
        firstTestMetadataDetails[0]["key"] == "my key"
        firstTestMetadataDetails[0]["value"]["customOperationTraceSerializableModel"] == ["my custom serializable type", "with some values", "in a list", 2024]
    }

    def "captures multiple metadata values for custom test"() {
        given:
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                   try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.metadata(Instant.now(), "key1", "value1")
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.metadata(Instant.now(), "key2", 2)
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       reporter.succeeded(Instant.now())
                   }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)

        when:
        succeeds "customTest"

        then: "metadata is retrievable from build operations"
        List<BuildOperationRecord.Progress> testMetadata = getMetadataForOnlyTest()
        testMetadata.size() == 2
        def firstTestMetadataDetails = testMetadata*.details.metadata as List<Map<String, ?>>
        firstTestMetadataDetails.size() == 2
        firstTestMetadataDetails[0]["key"] == "key1"
        firstTestMetadataDetails[0]["value"] == "value1"
        firstTestMetadataDetails[1]["key"] == "key2"
        firstTestMetadataDetails[1]["value"] == 2
    }

    private TestFile singleCustomTestRecordingMetadata(String key, @GroovyBuildScriptLanguage String valueExpression) {
        buildFile("""
            import java.time.Instant

            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @TaskAction
                void runTests() {
                   try (def reporter = getTestEventReporterFactory().createTestEventReporter("Custom test root")) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("My Suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("MyTestInternal", "My test!")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is a test output on stdout")
                                 myTest.metadata(Instant.now(), "$key", $valueExpression)
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       reporter.succeeded(Instant.now())
                   }
                }
            }

            tasks.register("customTest", CustomTestTask)
        """)
    }

    private List<BuildOperationRecord.Progress> getMetadataForOnlyTest() {
        def rootTestOp = operations.first(ExecuteTestBuildOperationType)
        def rootTestOpDetails = rootTestOp.details as Map<String, Map<String, ?>>
        assert (rootTestOpDetails.testDescriptor.name as String).startsWith("Custom test root")
        assert rootTestOpDetails.testDescriptor.className == null
        assert rootTestOpDetails.testDescriptor.composite

        def suiteTestOps = operations.children(rootTestOp, ExecuteTestBuildOperationType)
        assert suiteTestOps.size() == 1
        def suiteTestOpDetails = suiteTestOps[0].details as Map<String, Map<String, ?>>
        assert (suiteTestOpDetails.testDescriptor.name as String).startsWith("My Suite")
        assert suiteTestOpDetails.testDescriptor.className == null
        assert suiteTestOpDetails.testDescriptor.composite

        def firstLevelTestOps = operations.children(suiteTestOps[0], ExecuteTestBuildOperationType).sort {
            (it.details as Map<String, TestDescriptorInternal>).testDescriptor.name
        }
        assert firstLevelTestOps.size() == 1
        def firstLevelTestOpDetails = firstLevelTestOps*.details as List<Map<String, Map<String, ?>>>
        assert firstLevelTestOpDetails*.testDescriptor.name == ["MyTestInternal"]
        assert firstLevelTestOpDetails*.testDescriptor.displayName == ["My test!"]
        assert firstLevelTestOpDetails*.testDescriptor.className == [null]
        assert firstLevelTestOpDetails*.testDescriptor.composite == [false]

        return firstLevelTestOps[0].metadata
    }
}
