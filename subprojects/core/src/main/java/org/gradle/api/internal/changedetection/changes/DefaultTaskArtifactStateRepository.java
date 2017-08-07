/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private final TaskHistoryRepository taskHistoryRepository;
    private final FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry;
    private final Instantiator instantiator;
    private final FileCollectionFactory fileCollectionFactory;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final TaskCacheKeyCalculator cacheKeyCalculator;
    private final ValueSnapshotter valueSnapshotter;
    private final TaskOutputFilesRepository taskOutputFilesRepository;

    public DefaultTaskArtifactStateRepository(TaskHistoryRepository taskHistoryRepository, Instantiator instantiator,
                                              FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry,
                                              FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
                                              TaskCacheKeyCalculator cacheKeyCalculator, ValueSnapshotter valueSnapshotter, TaskOutputFilesRepository taskOutputFilesRepository) {
        this.taskHistoryRepository = taskHistoryRepository;
        this.instantiator = instantiator;
        this.fileCollectionSnapshotterRegistry = fileCollectionSnapshotterRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.cacheKeyCalculator = cacheKeyCalculator;
        this.valueSnapshotter = valueSnapshotter;
        this.taskOutputFilesRepository = taskOutputFilesRepository;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task));
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private boolean upToDate;
        private boolean outputsRemoved;
        private TaskUpToDateState states;
        private IncrementalTaskInputsInternal taskInputs;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
        }

        @Override
        public boolean isUpToDate(Collection<String> messages) {
            upToDate = true;
            for (TaskStateChange stateChange : getStates().getAllTaskChanges()) {
                messages.add(stateChange.getMessage());
                upToDate = false;
            }
            return upToDate;
        }

        @Override
        public IncrementalTaskInputs getInputChanges() {
            assert !upToDate : "Should not be here if the task is up-to-date";

            if (!outputsRemoved && canPerformIncrementalBuild()) {
                taskInputs = instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, getStates().getInputFilesChanges());
            } else {
                taskInputs = instantiator.newInstance(RebuildIncrementalTaskInputs.class, task);
            }
            return taskInputs;
        }

        private boolean canPerformIncrementalBuild() {
            return Iterables.isEmpty(getStates().getRebuildChanges());
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return true;
        }

        @Override
        public OverlappingOutputs getOverlappingOutputs() {
            // Ensure that states are created
            getStates();
            return history.getCurrentExecution().getDetectedOverlappingOutputs();
        }

        @Override
        public TaskOutputCachingBuildCacheKey calculateCacheKey() {
            // Ensure that states are created
            getStates();
            return cacheKeyCalculator.calculate(history.getCurrentExecution());
        }

        @Override
        public Set<File> getOutputFiles() {
            TaskExecution previousExecution = history.getPreviousExecution();
            if (previousExecution == null || previousExecution.getOutputFilesSnapshot() == null) {
                return Collections.emptySet();
            }
            ImmutableCollection<FileCollectionSnapshot> outputFilesSnapshot = previousExecution.getOutputFilesSnapshot().values();
            Set<File> outputs = new HashSet<File>();
            for (FileCollectionSnapshot fileCollectionSnapshot : outputFilesSnapshot) {
                outputs.addAll(fileCollectionSnapshot.getElements());
            }
            return outputs;
        }

        @Override
        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        @Nullable
        @Override
        public UniqueId getOriginBuildInvocationId() {
            TaskExecution previousExecution = history.getPreviousExecution();
            if (previousExecution == null) {
                return null;
            } else {
                return previousExecution.getBuildInvocationId();
            }
        }

        @Override
        public void ensureSnapshotBeforeTask() {
            getStates();
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
            outputsRemoved = true;
        }

        @Override
        public void afterTask(Throwable failure) {
            if (upToDate) {
                return;
            }

            TaskUpToDateState taskState = getStates();

            if (taskInputs != null) {
                taskState.newInputs(taskInputs.getDiscoveredInputs());
            }
            taskState.getAllTaskChanges().snapshotAfterTask();

            // Only store new taskState if there was no failure, or some output files have been changed
            if (failure == null || taskState.hasAnyOutputFileChanges()) {
                history.update();
                taskOutputFilesRepository.recordOutputs(history.getCurrentExecution());
            }
        }

        private TaskUpToDateState getStates() {
            if (states == null) {
                // Calculate initial state - note this is potentially expensive
                states = new TaskUpToDateState(task, history, fileCollectionSnapshotterRegistry, fileCollectionFactory, classLoaderHierarchyHasher, valueSnapshotter);
            }
            return states;
        }
    }

}
