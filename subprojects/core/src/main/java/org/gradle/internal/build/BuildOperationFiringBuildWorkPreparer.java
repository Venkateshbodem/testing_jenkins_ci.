/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.execution.plan.ToPlannedNodeConverter;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.TaskIdentity;
import org.gradle.internal.taskgraph.NodeIdentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.newSetFromMap;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.Details;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedNode;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.PlannedTask;
import static org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType.Result;

public class BuildOperationFiringBuildWorkPreparer implements BuildWorkPreparer {
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildWorkPreparer delegate;
    private final ConverterRegistry converterRegistry;

    public BuildOperationFiringBuildWorkPreparer(BuildOperationExecutor buildOperationExecutor, BuildWorkPreparer delegate, List<ToPlannedNodeConverter> converters) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        this.converterRegistry = new ConverterRegistry(converters);
    }

    @Override
    public ExecutionPlan newExecutionPlan() {
        return delegate.newExecutionPlan();
    }

    @Override
    public void populateWorkGraph(GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action) {
        buildOperationExecutor.run(new PopulateWorkGraph(delegate, gradle, plan, action, converterRegistry));
    }

    @Override
    public FinalizedExecutionPlan finalizeWorkGraph(GradleInternal gradle, ExecutionPlan plan) {
        return delegate.finalizeWorkGraph(gradle, plan);
    }

    private static class PopulateWorkGraph implements RunnableBuildOperation {
        private final BuildWorkPreparer delegate;
        private final GradleInternal gradle;
        private final ExecutionPlan plan;
        private final Consumer<? super ExecutionPlan> action;
        private final ConverterRegistry converterRegistry;
        private final NodeDependencyLookup dependencyLookup;

        public PopulateWorkGraph(BuildWorkPreparer delegate, GradleInternal gradle, ExecutionPlan plan, Consumer<? super ExecutionPlan> action, ConverterRegistry converterRegistry) {
            this.delegate = delegate;
            this.gradle = gradle;
            this.plan = plan;
            this.action = action;
            this.converterRegistry = converterRegistry;
            this.dependencyLookup = new NodeDependencyLookup(converterRegistry);
        }

        @Override
        public void run(BuildOperationContext buildOperationContext) {
            populateTaskGraph();

            // create copy now - https://github.com/gradle/gradle/issues/12527
            QueryableExecutionPlan contents = plan.getContents();
            Set<Task> requestedTasks = contents.getRequestedTasks();
            Set<Task> filteredTasks = contents.getFilteredTasks();
            QueryableExecutionPlan.ScheduledNodes scheduledWork = contents.getScheduledNodes();

            List<PlannedNode> plannedNodes = toPlannedNodes(scheduledWork);

            buildOperationContext.setResult(new Result() {

                @Override
                public List<String> getRequestedTaskPaths() {
                    return toSortedTaskPaths(requestedTasks);
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toSortedTaskPaths(filteredTasks);
                }

                @Override
                public List<PlannedTask> getTaskPlan() {
                    return plannedNodes.stream()
                        .filter(PlannedTask.class::isInstance)
                        .map(PlannedTask.class::cast)
                        .collect(Collectors.toList());
                }

                @Override
                public List<PlannedNode> getExecutionPlan(Set<NodeIdentity.NodeType> types) {
                    if (EnumSet.allOf(NodeIdentity.NodeType.class).equals(types)) {
                        return plannedNodes;
                    }
                    return plannedNodes.stream()
                        .filter(node -> types.contains(node.getNodeIdentity().getNodeType()))
                        .collect(Collectors.toList());
                }
            });
        }

        void populateTaskGraph() {
            delegate.populateWorkGraph(gradle, plan, action);
        }

        @Nonnull
        @Override
        public BuildOperationDescriptor.Builder description() {
            //noinspection Convert2Lambda
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                .details(new Details() {
                    @Override
                    public String getBuildPath() {
                        return gradle.getIdentityPath().getPath();
                    }
                });
        }

        private List<PlannedNode> toPlannedNodes(QueryableExecutionPlan.ScheduledNodes scheduledWork) {
            List<PlannedNode> plannedNodes = new ArrayList<>();
            scheduledWork.visitNodes(nodes -> {
                for (Node node : nodes) {
                    ToPlannedNodeConverter converter = converterRegistry.getConverter(node);
                    if (converter != null && converter.isInSamePlan(node)) {
                        PlannedNode plannedNode = converter.convert(node, dependencyLookup);
                        plannedNodes.add(plannedNode);
                    }
                }
            });
            return plannedNodes;
        }


    }

    private static List<String> toSortedTaskPaths(Set<Task> tasks) {
        return tasks.stream().map(Task::getPath).distinct().sorted().collect(Collectors.toList());
    }

    private static class NodeDependencyLookup implements ToPlannedNodeConverter.DependencyLookup {

        private final ConverterRegistry converterRegistry;

        private NodeDependencyLookup(ConverterRegistry converterRegistry) {
            this.converterRegistry = converterRegistry;
        }

        @Override
        public List<? extends NodeIdentity> findNodeDependencies(Node node) {
            return findDependencies(node, Node::getDependencySuccessors, it -> true).collect(Collectors.toList());
        }

        @Override
        public List<? extends TaskIdentity> findTaskDependencies(Node node) {
            return findDependencies(node, Node::getDependencySuccessors, it -> it instanceof TaskIdentity)
                .map(TaskIdentity.class::cast)
                .collect(Collectors.toList());
        }

        private Stream<? extends NodeIdentity> findDependencies(
            Node node,
            Function<? super Node, ? extends Collection<Node>> traverser,
            Predicate<NodeIdentity> isDependencyNode
        ) {
            return findIdentifiedNodes(node, traverser, isDependencyNode, newSetFromMap(new IdentityHashMap<>()));
        }

        private Stream<NodeIdentity> findIdentifiedNodes(
            Node curNode,
            Function<? super Node, ? extends Collection<Node>> traverser,
            Predicate<NodeIdentity> isDependencyNode,
            Set<Node> seen
        ) {
            Collection<Node> nodes = traverser.apply(curNode);
            if (nodes.isEmpty()) {
                return Stream.empty();
            }

            return nodes.stream()
                .filter(seen::add)
                .flatMap(node -> {
                    NodeIdentity nodeIdentity = identifyAsDependencyNode(node, isDependencyNode);
                    return nodeIdentity != null
                        ? Stream.of(nodeIdentity)
                        : findIdentifiedNodes(node, traverser, isDependencyNode, seen);
                });
        }

        private NodeIdentity identifyAsDependencyNode(Node node, Predicate<NodeIdentity> isDependencyNode) {
            ToPlannedNodeConverter converter = converterRegistry.getConverter(node);
            if (converter == null) {
                return null;
            }
            NodeIdentity nodeIdentity = converter.getNodeIdentity(node);
            return isDependencyNode.test(nodeIdentity) ? nodeIdentity : null;
        }
    }

    private static class ConverterRegistry {

        private final List<ToPlannedNodeConverter> converters;

        private final Map<Class<?>, ToPlannedNodeConverter> convertersByNodeType = new HashMap<>();
        private final Set<Class<?>> unsupportedNodeTypes = new HashSet<>();

        private ConverterRegistry(List<ToPlannedNodeConverter> converters) {
            validateConverters(converters);
            this.converters = ImmutableList.copyOf(converters);

            for (ToPlannedNodeConverter converter : this.converters) {
                convertersByNodeType.put(converter.getSupportedNodeType(), converter);
            }
        }

        /**
         * Returns a converter for the given node, or null if there is no converter for the node.
         */
        @Nullable
        public ToPlannedNodeConverter getConverter(Node node) {
            Class<? extends Node> nodeType = node.getClass();
            ToPlannedNodeConverter converter = convertersByNodeType.get(nodeType);
            if (converter != null) {
                return converter;
            }

            if (unsupportedNodeTypes.contains(nodeType)) {
                return null;
            }

            for (ToPlannedNodeConverter converterCandidate : converters) {
                Class<? extends Node> supportedNodeType = converterCandidate.getSupportedNodeType();
                if (supportedNodeType.isAssignableFrom(nodeType)) {
                    converter = converterCandidate;
                    break;
                }
            }

            if (converter != null) {
                convertersByNodeType.put(nodeType, converter);
            } else {
                unsupportedNodeTypes.add(nodeType);
            }

            return converter;
        }

        private static void validateConverters(List<ToPlannedNodeConverter> converters) {
            for (ToPlannedNodeConverter converter1 : converters) {
                Class<? extends Node> supportedNodeType1 = converter1.getSupportedNodeType();
                for (ToPlannedNodeConverter converter2 : converters) {
                    if (converter1 == converter2) {
                        continue;
                    }

                    Class<? extends Node> supportedNodeType2 = converter2.getSupportedNodeType();
                    if (supportedNodeType1.isAssignableFrom(supportedNodeType2) || supportedNodeType2.isAssignableFrom(supportedNodeType1)) {
                        throw new IllegalStateException("Converter " + converter1 + " is not compatible with converter " + converter2);
                    }
                }
            }
        }
    }
}
