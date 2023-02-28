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

package org.gradle.integtests.resolve.transform

import groovy.transform.EqualsAndHashCode
import org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode
import org.gradle.internal.taskgraph.NodeIdentity

import java.util.function.Predicate

class ArtifactTransformBuildOperationIntegrationTest extends AbstractIntegrationSpec implements ArtifactTransformTestFixture {

    @EqualsAndHashCode
    static class TypedNodeId {
        String nodeType
        String nodeIdInType
    }

    static class NodeMatcher {
        // An ephemeral id provided by the test author only to match this id with other nodes via `dependencyNodeIds`
        String nodeId
        String nodeType
        Predicate<NodeIdentity> identityPredicate
        List<String> dependencyNodeIds = []

        def matchNode(plannedNode) {
            plannedNode.nodeIdentity.nodeType.toString() == nodeType && identityPredicate.test(plannedNode.nodeIdentity)
        }
    }

    static class TransformationIdentityWithoutId {
        String buildPath
        String projectPath
        Map<String, String> componentId
        Map<String, String> targetAttributes
        List<Map<String, String>> capabilities
        String artifactName
        Map<String, String> dependenciesConfigurationIdentity
    }

    static final Set<String> KNOWN_NODE_TYPES = NodeIdentity.NodeType.values()*.name() as Set<String>
    static final String TASK = NodeIdentity.NodeType.TASK.name()
    static final String ARTIFACT_TRANSFORM = NodeIdentity.NodeType.ARTIFACT_TRANSFORM.name()

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def setup() {
        requireOwnGradleUserHomeDir()

        buildFile << """
            allprojects {
                group = "colored"
            }
        """
    }

    def setupExternalDependency() {
        def m1 = mavenRepo.module("test", "test", "4.2").publish()
        m1.artifactFile.text = "test-test"

        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }

                dependencies {
                    implementation 'test:test:4.2'
                }
            }
        """
    }

    def "single transform operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformImplementation()
        setupExternalDependency()

        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }

            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        List<PlannedNode> plannedNodes = getPlannedNodes(1)

        def expectedTransformId = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId, ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        List<BuildOperationRecord> executeTransformationOps = getExecuteTransformOperations(1)

        with(executeTransformationOps[0].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId)
            transformType == "MakeGreen"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }


    def "chained transform operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithChainedColorTransform()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [producer.jar]")
            .assertOutputContains("processing [producer.jar.red]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId1, ["node1"]),
                transformMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId1)
            transformType == "MakeColor"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId2)
            transformType == "MakeColor"
            sourceAttributes == [color: "red", artifactType: "jar"]

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "transform output used as another transform input operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformWithAnotherTransformOutputAsInput()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                    transform project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar using [producer.jar.red, test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId1, ["node1"]),
                transformMatcher("node3", expectedTransformId2, ["node1", "node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)

        with(executeTransformationOps[0].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId1)
            transformType == "MakeRed"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId2)
            transformType == "MakeGreen"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "single transform with upstream dependencies operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        setupExternalDependency()

        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeGreen) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'green')
                    }
                }
            }

            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar using [test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(1)

        def expectedTransformId1 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: [buildPath: ":", projectPath: ":consumer", name: "resolver"],
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId1, ["node1"]),
                taskMatcher("node3", ":consumer:resolve", ["node2"]),
            ]
        )

        with(buildOperations.only(ExecuteScheduledTransformationStepBuildOperationType).details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId1)
            transformType == "MakeGreen"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "chained transform with upstream dependencies operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithChainedColorTransformThatTakesUpstreamArtifacts()
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeRed")
            .assertOutputContains("processing [producer.jar]")

        result.groupedOutput.transform("MakeGreen")
            .assertOutputContains("processing producer.jar.red using [test-4.2.jar]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red.green, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: [buildPath: ":", projectPath: ":consumer", name: "resolver"],
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId1, ["node1"]),
                transformMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId1)
            transformType == "MakeRed"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeRed"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId2)
            transformType == "MakeGreen"
            sourceAttributes == [color: "red", artifactType: "jar"]

            transformerName == "MakeGreen"
            subjectName == "producer.jar (project :producer)"
        }
    }

    def "chained transform producing multiple artifacts operations are captured"() {
        settingsFile << """
            include 'producer', 'consumer'
        """

        setupBuildWithColorAttributes()
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform(MakeColor) {
                        from.attribute(color, 'blue')
                        to.attribute(color, 'red')
                        parameters.targetColor.set('red')
                        parameters.multiplier.set(2)
                    }
                    registerTransform(MakeColor) {
                        from.attribute(color, 'red')
                        to.attribute(color, 'green')
                        parameters.targetColor.set('green')
                        parameters.multiplier.set(1)
                    }
                }
            }

            interface TargetColor extends TransformParameters {
                @Input
                Property<String> getTargetColor()
                @Input
                Property<Integer> getMultiplier()
            }

            abstract class MakeColor implements TransformAction<TargetColor> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    println "processing [\${input.name}]"
                    assert input.file
                    for (def i : 1..parameters.multiplier.get()) {
                        def output = outputs.file(input.name + "." + parameters.targetColor.get() + "-" + i)
                        output.text = input.text + "-" + parameters.targetColor.get() + "-" + i
                    }
                }
            }
        """
        setupExternalDependency()

        buildFile << """
            project(":consumer") {
                dependencies {
                    implementation project(":producer")
                }
            }
        """

        when:
        run ":consumer:resolve"

        then:
        executedAndNotSkipped(":consumer:resolve")

        result.groupedOutput.transform("MakeColor")
            .assertOutputContains("processing [producer.jar]")
            .assertOutputContains("processing [producer.jar.red-1]")
            .assertOutputContains("processing [producer.jar.red-2]")

        result.groupedOutput.task(":consumer:resolve")
            .assertOutputContains("result = [producer.jar.red-1.green-1, producer.jar.red-2.green-1, test-4.2.jar]")

        def plannedNodes = getPlannedNodes(2)

        def expectedTransformId1 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "red", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        def expectedTransformId2 = new TransformationIdentityWithoutId([
            buildPath: ":",
            projectPath: ":consumer",
            componentId: [buildPath: ":", projectPath: ":producer"],
            targetAttributes: [color: "green", artifactType: "jar"],
            capabilities: [[group: "colored", name: "producer", version: "unspecified"]],
            artifactName: "producer.jar",
            dependenciesConfigurationIdentity: null,
        ])

        checkExecutionPlanMatchingDependencies(
            plannedNodes,
            [
                taskMatcher("node1", ":producer:producer", []),
                transformMatcher("node2", expectedTransformId1, ["node1"]),
                transformMatcher("node3", expectedTransformId2, ["node2"]),
                taskMatcher("node4", ":consumer:resolve", ["node3"]),
            ]
        )

        def executeTransformationOps = getExecuteTransformOperations(2)
        with(executeTransformationOps[0].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId1)
            transformType == "MakeColor"
            sourceAttributes == [color: "blue", artifactType: "jar"]

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }

        with(executeTransformationOps[1].details) {
            matchTransformationIdentity(transformationIdentity, expectedTransformId2)
            transformType == "MakeColor"
            sourceAttributes == [color: "red", artifactType: "jar"]

            transformerName == "MakeColor"
            subjectName == "producer.jar (project :producer)"
        }
    }

    void checkExecutionPlanMatchingDependencies(List<PlannedNode> plannedNodes, List<NodeMatcher> nodeMatchers) {
        Map<TypedNodeId, List<String>> expectedDependencyNodeIdsByTypedNodeId = [:]
        Map<TypedNodeId, String> nodeIdByTypedNodeId = [:]

        for (def plannedNode : plannedNodes) {
            def matchers = nodeMatchers.findAll { it.matchNode(plannedNode) }
            assert matchers.size() == 1, "Expected exactly one matcher for node ${plannedNode.nodeIdentity}, but found ${matchers.size()}"
            def nodeMatcher = matchers[0]
            def nodeId = getTypedNodeId(plannedNode.nodeIdentity)
            nodeIdByTypedNodeId[nodeId] = nodeMatcher.nodeId
            expectedDependencyNodeIdsByTypedNodeId[nodeId] = nodeMatcher.dependencyNodeIds
        }

        for (def plannedNode : plannedNodes) {
            def typedNodeId = getTypedNodeId(plannedNode.nodeIdentity)
            def expectedDependencyNodeIds = expectedDependencyNodeIdsByTypedNodeId[typedNodeId]
            assert expectedDependencyNodeIds != null, "No expected dependencies for node $plannedNode"

            def actualDependencyNodeIds = plannedNode.nodeDependencies.collect { depIdentity ->
                def depTypedNodeId = getTypedNodeId(depIdentity)
                def depNodeId = nodeIdByTypedNodeId[depTypedNodeId]
                assert depNodeId != null, "No node id for node $depIdentity"
                depNodeId
            }

            assert actualDependencyNodeIds ==~ expectedDependencyNodeIds
        }
    }

    def taskMatcher(String nodeId, String taskIdentityPath, List<String> dependencyNodeIds) {
        new NodeMatcher(
            nodeId: nodeId,
            nodeType: TASK,
            identityPredicate: { joinPaths(it.buildPath, it.taskPath) == taskIdentityPath },
            dependencyNodeIds: dependencyNodeIds
        )
    }

    def transformMatcher(
        String nodeId,
        TransformationIdentityWithoutId transformationIdentity,
        List<String> dependencyNodeIds
    ) {
        new NodeMatcher(
            nodeId: nodeId,
            nodeType: ARTIFACT_TRANSFORM,
            identityPredicate: { matchTransformationIdentity(it, transformationIdentity) },
            dependencyNodeIds: dependencyNodeIds
        )
    }

    def getTypedNodeId(nodeIdentity) {
        switch (nodeIdentity.nodeType) {
            case TASK:
                return new TypedNodeId(nodeType: TASK, nodeIdInType: nodeIdentity.taskId.toString())
            case ARTIFACT_TRANSFORM:
                return new TypedNodeId(nodeType: ARTIFACT_TRANSFORM, nodeIdInType: nodeIdentity.transformationNodeId)
            default:
                throw new IllegalArgumentException("Unknown node type: ${nodeIdentity.nodeType}")
        }
    }

    def joinPaths(String... paths) {
        paths.inject("") { acc, path ->
            acc + (acc.endsWith(":") && path.startsWith(":") ? path.substring(1) : path)
        }
    }

    boolean matchTransformationIdentity(actual, TransformationIdentityWithoutId expected) {
        actual.nodeType.toString() == ARTIFACT_TRANSFORM &&
            actual.buildPath == expected.buildPath &&
            actual.projectPath == expected.projectPath &&
            actual.componentId == expected.componentId &&
            actual.targetAttributes == expected.targetAttributes &&
            actual.capabilities == expected.capabilities &&
            actual.artifactName == expected.artifactName &&
            actual.dependenciesConfigurationIdentity == expected.dependenciesConfigurationIdentity
    }

    def getPlannedNodes(int transformNodeCount) {
        def plannedNodes = buildOperations.only(CalculateTaskGraphBuildOperationType).result.executionPlan as List<PlannedNode>
        assert plannedNodes.every { KNOWN_NODE_TYPES.contains(it.nodeIdentity.nodeType) }
        assert plannedNodes.count { it.nodeIdentity.nodeType.toString() == ARTIFACT_TRANSFORM } == transformNodeCount
        return plannedNodes
    }

    def getExecuteTransformOperations(int expectOperationsCount) {
        def operations = buildOperations.all(ExecuteScheduledTransformationStepBuildOperationType)
        assert operations.every { it.failure == null }
        assert operations.size() == expectOperationsCount
        return operations
    }
}
