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
package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.normalization.internal.InputNormalizationHandlerInternal;
import org.gradle.normalization.internal.InputNormalizationStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

public class CacheBackedTaskHistoryRepository implements TaskHistoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBackedTaskHistoryRepository.class);

    private final FileSnapshotRepository snapshotRepository;
    private final PersistentIndexedCache<String, TaskExecutionSnapshot> taskHistoryCache;
    private final StringInterner stringInterner;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionSnapshotterRegistry snapshotterRegistry;
    private final BuildInvocationScopeId buildInvocationScopeId;

    public CacheBackedTaskHistoryRepository(
        TaskHistoryStore cacheAccess,
        FileSnapshotRepository snapshotRepository,
        StringInterner stringInterner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        FileCollectionSnapshotterRegistry snapshotterRegistry,
        BuildInvocationScopeId buildInvocationScopeId) {
        this.snapshotRepository = snapshotRepository;
        this.stringInterner = stringInterner;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.snapshotterRegistry = snapshotterRegistry;
        this.buildInvocationScopeId = buildInvocationScopeId;
        LazyTaskExecution.TaskExecutionSnapshotSerializer serializer = new LazyTaskExecution.TaskExecutionSnapshotSerializer(stringInterner);
        this.taskHistoryCache = cacheAccess.createCache("taskHistory", String.class, serializer, 10000, false);
    }

    @Override
    public History getHistory(final TaskInternal task) {
        final InputNormalizationStrategy normalizationStrategy = ((InputNormalizationHandlerInternal) task.getProject().getNormalization()).buildFinalStrategy();

        final LazyTaskExecution previousExecution = loadPreviousExecution(task);
        final LazyTaskExecution currentExecution = createExecution(task, previousExecution, normalizationStrategy);

        return new History() {
            @Override
            public TaskExecution getPreviousExecution() {
                return previousExecution;
            }

            @Override
            public TaskExecution getCurrentExecution() {
                return currentExecution;
            }

            @Override
            public void updateCurrentExecution() {
                CacheBackedTaskHistoryRepository.this.updateExecutionAfterTask(task, previousExecution, currentExecution, normalizationStrategy);
            }

            @Override
            public void persist() {
                storeSnapshots(currentExecution);
                if (previousExecution != null) {
                    removeUnnecessarySnapshots(previousExecution);
                }
                taskHistoryCache.put(task.getPath(), currentExecution.snapshot());
            }

            private void storeSnapshots(LazyTaskExecution execution) {
                if (execution.inputFilesSnapshotIds == null && execution.inputFilesSnapshot != null) {
                    ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                    for (Map.Entry<String, FileCollectionSnapshot> entry : execution.inputFilesSnapshot.entrySet()) {
                        builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                    }
                    execution.inputFilesSnapshotIds = builder.build();
                }
                if (execution.outputFilesSnapshotIds == null && execution.outputFilesSnapshot != null) {
                    ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                    for (Map.Entry<String, FileCollectionSnapshot> entry : execution.outputFilesSnapshot.entrySet()) {
                        builder.put(entry.getKey(), snapshotRepository.add(entry.getValue()));
                    }
                    execution.outputFilesSnapshotIds = builder.build();
                }
                if (execution.discoveredFilesSnapshotId == null && execution.discoveredFilesSnapshot != null) {
                    execution.discoveredFilesSnapshotId = snapshotRepository.add(execution.discoveredFilesSnapshot);
                }
            }

            private void removeUnnecessarySnapshots(LazyTaskExecution execution) {
                if (execution.inputFilesSnapshotIds != null) {
                    for (Long id : execution.inputFilesSnapshotIds.values()) {
                        snapshotRepository.remove(id);
                    }
                }
                if (execution.outputFilesSnapshotIds != null) {
                    for (Long id : execution.outputFilesSnapshotIds.values()) {
                        snapshotRepository.remove(id);
                    }
                }
                if (execution.discoveredFilesSnapshotId != null) {
                    snapshotRepository.remove(execution.discoveredFilesSnapshotId);
                }
            }
        };
    }

    private LazyTaskExecution createExecution(TaskInternal task, TaskExecution previousExecution, InputNormalizationStrategy normalizationStrategy) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = new ImplementationSnapshot(taskClass.getName(), classLoaderHierarchyHasher.getClassLoaderHash(taskClass.getClassLoader()));
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecution == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : previousExecution.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, previousInputProperties);

        ImmutableSortedSet<String> outputPropertyNames = getOutputPropertyNamesForCacheKey(task);
        ImmutableSet<String> declaredOutputFilePaths = getDeclaredOutputFilePaths(task);

        LazyTaskExecution execution = new LazyTaskExecution(snapshotRepository, buildInvocationScopeId.getId(), taskImplementation, taskActionImplementations, inputProperties, outputPropertyNames, declaredOutputFilePaths);

        ImmutableSortedMap<String, FileCollectionSnapshot> inputFiles = snapshotTaskFiles(task, "Input",  normalizationStrategy, task.getInputs().getFileProperties());
        execution.setInputFilesSnapshot(inputFiles);

        ImmutableSortedMap<String, FileCollectionSnapshot> outputFiles = snapshotTaskFiles(task, "Output",  normalizationStrategy, task.getOutputs().getFileProperties());
        execution.setOutputFilesSnapshot(outputFiles);

        OverlappingOutputs overlappingOutputs = detectOverlappingOutputs(outputFiles, previousExecution);
        execution.setDetectedOverlappingOutputs(overlappingOutputs);

        return execution;
    }

    private void updateExecutionAfterTask(TaskInternal task, final TaskExecution previousExecution, LazyTaskExecution currentExecution, InputNormalizationStrategy normalizationStrategy) {
        currentExecution.setSuccessful(task.getState().getFailure() == null);

        final ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesAfter = snapshotTaskFiles(task, "Output", normalizationStrategy, task.getOutputs().getFileProperties());

        ImmutableSortedMap<String, FileCollectionSnapshot> results;
        if (currentExecution.getDetectedOverlappingOutputs() == null) {
            results = outputFilesAfter;
        } else {
            results = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(currentExecution.getOutputFilesSnapshot(), new Maps.EntryTransformer<String, FileCollectionSnapshot, FileCollectionSnapshot>() {
                @Override
                public FileCollectionSnapshot transformEntry(String propertyName, FileCollectionSnapshot beforeExecution) {
                    FileCollectionSnapshot afterExecution = outputFilesAfter.get(propertyName);
                    FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(previousExecution, propertyName);
                    return filterOutputSnapshot(afterPreviousExecution, beforeExecution, afterExecution);
                }
            }));
        }
        currentExecution.setOutputFilesSnapshot(results);
    }

    /**
     * Returns a new snapshot that filters out entries that should not be considered outputs of the task.
     */
    private static FileCollectionSnapshot filterOutputSnapshot(
        FileCollectionSnapshot afterPreviousExecution,
        FileCollectionSnapshot beforeExecution,
        FileCollectionSnapshot afterExecution
    ) {
        FileCollectionSnapshot filesSnapshot;
        Map<String, NormalizedFileSnapshot> afterSnapshots = afterExecution.getSnapshots();
        if (!beforeExecution.getSnapshots().isEmpty() && !afterSnapshots.isEmpty()) {
            Map<String, NormalizedFileSnapshot> beforeSnapshots = beforeExecution.getSnapshots();
            Map<String, NormalizedFileSnapshot> afterPreviousSnapshots = afterPreviousExecution != null ? afterPreviousExecution.getSnapshots() : new HashMap<String, NormalizedFileSnapshot>();
            int newEntryCount = 0;
            ImmutableMap.Builder<String, NormalizedFileSnapshot> outputEntries = ImmutableMap.builder();

            for (Map.Entry<String, NormalizedFileSnapshot> entry : afterSnapshots.entrySet()) {
                final String path = entry.getKey();
                NormalizedFileSnapshot fileSnapshot = entry.getValue();
                if (isOutputEntry(path, fileSnapshot, beforeSnapshots, afterPreviousSnapshots)) {
                    outputEntries.put(entry.getKey(), fileSnapshot);
                    newEntryCount++;
                }
            }
            // Are all files snapshot after execution accounted for as new entries?
            if (newEntryCount == afterSnapshots.size()) {
                filesSnapshot = afterExecution;
            } else {
                filesSnapshot = new DefaultFileCollectionSnapshot(outputEntries.build(), TaskFilePropertyCompareStrategy.UNORDERED, true);
            }
        } else {
            filesSnapshot = afterExecution;
        }
        return filesSnapshot;
    }

    /**
     * Decide whether an entry should be considered to be part of the output. Entries that are considered outputs are:
     * <ul>
     *     <li>an entry that did not exist before the execution, but exists after the execution</li>
     *     <li>an entry that did exist before the execution, and has been changed during the execution</li>
     *     <li>an entry that did wasn't changed during the execution, but was already considered an output during the previous execution</li>
     * </ul>
     */
    private static boolean isOutputEntry(String path, NormalizedFileSnapshot fileSnapshot, Map<String, NormalizedFileSnapshot> beforeSnapshots, Map<String, NormalizedFileSnapshot> afterPreviousSnapshots) {
        if (fileSnapshot.getSnapshot().getType() == FileType.Missing) {
            return false;
        }
        NormalizedFileSnapshot beforeSnapshot = beforeSnapshots.get(path);
        // Was it created during execution?
        if (beforeSnapshot == null) {
            return true;
        }
        // Was it updated during execution?
        if (!fileSnapshot.getSnapshot().isContentAndMetadataUpToDate(beforeSnapshot.getSnapshot())) {
            return true;
        }
        // Did we already consider it as an output after the previous execution?
        return afterPreviousSnapshots.containsKey(path);
    }

    private static ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImpls = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            String typeName = taskAction.getActionClassName();
            HashCode classLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(taskAction.getClassLoader());
            actionImpls.add(new ImplementationSnapshot(typeName, classLoaderHash));
        }
        return actionImpls.build();
    }

    private ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (Map.Entry<String, Object> entry : task.getInputs().getProperties().entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            try {
                ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    ValueSnapshot newSnapshot = valueSnapshotter.snapshot(value, previousSnapshot);
                    if (newSnapshot == previousSnapshot) {
                        builder.put(propertyName, previousSnapshot);
                    } else {
                        builder.put(propertyName, valueSnapshotter.snapshot(value));
                    }
                }
            } catch (Exception e) {
                throw new GradleException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.", task, propertyName, value), e);
            }
        }

        return builder.build();
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> snapshotTaskFiles(TaskInternal task, String title, InputNormalizationStrategy normalizationStrategy, SortedSet<? extends TaskFilePropertySpec> fileProperties) {
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (TaskFilePropertySpec propertySpec : fileProperties) {
            FileCollectionSnapshot result;
            try {
                FileCollectionSnapshotter snapshotter = snapshotterRegistry.getSnapshotter(propertySpec.getSnapshotter());
                result = snapshotter.snapshot(propertySpec.getPropertyFiles(), propertySpec.getPathNormalizationStrategy(), normalizationStrategy);
            } catch (UncheckedIOException e) {
                throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for %s property '%s' during up-to-date check.", title.toLowerCase(), task, propertySpec.getPropertyName()), e);
            }
            builder.put(propertySpec.getPropertyName(), result);
        }
        return builder.build();
    }

    private static OverlappingOutputs detectOverlappingOutputs(ImmutableSortedMap<String, FileCollectionSnapshot> taskOutputs, TaskExecution previousExecution) {
        for (Map.Entry<String, FileCollectionSnapshot> entry : taskOutputs.entrySet()) {
            String propertyName = entry.getKey();
            FileCollectionSnapshot beforeExecution = entry.getValue();
            FileCollectionSnapshot afterPreviousExecution = getSnapshotAfterPreviousExecution(previousExecution, propertyName);
            OverlappingOutputs overlappingOutputs = OverlappingOutputs.detect(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    private static FileCollectionSnapshot getSnapshotAfterPreviousExecution(TaskExecution previousExecution, String propertyName) {
        if (previousExecution != null) {
            Map<String, FileCollectionSnapshot> previousSnapshots = previousExecution.getOutputFilesSnapshot();
            if (previousSnapshots != null) {
                FileCollectionSnapshot afterPreviousExecution = previousSnapshots.get(propertyName);
                if (afterPreviousExecution != null) {
                    return afterPreviousExecution;
                }
            }
        }
        return FileCollectionSnapshot.EMPTY;
    }

    private LazyTaskExecution loadPreviousExecution(TaskInternal task) {
        TaskExecutionSnapshot taskExecutionSnapshot = taskHistoryCache.get(task.getPath());
        if (taskExecutionSnapshot != null) {
            return new LazyTaskExecution(taskExecutionSnapshot, snapshotRepository);
        } else {
            return null;
        }
    }

    private static ImmutableSortedSet<String> getOutputPropertyNamesForCacheKey(TaskInternal task) {
        ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties = task.getOutputs().getFileProperties();
        List<String> outputPropertyNames = Lists.newArrayListWithCapacity(fileProperties.size());
        for (TaskOutputFilePropertySpec propertySpec : fileProperties) {
            if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                CacheableTaskOutputFilePropertySpec cacheablePropertySpec = (CacheableTaskOutputFilePropertySpec) propertySpec;
                if (cacheablePropertySpec.getOutputFile() != null) {
                    outputPropertyNames.add(propertySpec.getPropertyName());
                }
            }
        }
        return ImmutableSortedSet.copyOf(outputPropertyNames);
    }

    private ImmutableSet<String> getDeclaredOutputFilePaths(TaskInternal task) {
        ImmutableSet.Builder<String> declaredOutputFilePaths = ImmutableSortedSet.naturalOrder();
        for (File file : task.getOutputs().getFiles()) {
            declaredOutputFilePaths.add(stringInterner.intern(file.getAbsolutePath()));
        }
        return declaredOutputFilePaths.build();
    }

    private static class LazyTaskExecution extends TaskExecution {
        private ImmutableSortedMap<String, Long> inputFilesSnapshotIds;
        private ImmutableSortedMap<String, Long> outputFilesSnapshotIds;
        private Long discoveredFilesSnapshotId;
        private final FileSnapshotRepository snapshotRepository;
        private ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot;
        private ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot;
        private FileCollectionSnapshot discoveredFilesSnapshot;

        /**
         * Creates a mutable copy of the given snapshot.
         */
        LazyTaskExecution(TaskExecutionSnapshot taskExecutionSnapshot, FileSnapshotRepository snapshotRepository) {
            this(
                snapshotRepository,
                taskExecutionSnapshot.getBuildInvocationId(),
                taskExecutionSnapshot.getTaskImplementation(),
                taskExecutionSnapshot.getTaskActionsImplementations(),
                taskExecutionSnapshot.getInputProperties(),
                taskExecutionSnapshot.getCacheableOutputProperties(),
                taskExecutionSnapshot.getDeclaredOutputFilePaths()
            );
            setSuccessful(taskExecutionSnapshot.isSuccessful());
            this.inputFilesSnapshotIds = taskExecutionSnapshot.getInputFilesSnapshotIds();
            this.outputFilesSnapshotIds = taskExecutionSnapshot.getOutputFilesSnapshotIds();
            this.discoveredFilesSnapshotId = taskExecutionSnapshot.getDiscoveredFilesSnapshotId();
        }

        public LazyTaskExecution(
            FileSnapshotRepository snapshotRepository,
            UniqueId buildInvocationId,
            ImplementationSnapshot taskImplementation,
            ImmutableList<ImplementationSnapshot> taskActionsImplementations,
            ImmutableSortedMap<String, ValueSnapshot> inputProperties,
            ImmutableSortedSet<String> outputPropertyNames,
            ImmutableSet<String> declaredOutputFilePaths
        ) {
            super(buildInvocationId, taskImplementation, taskActionsImplementations, inputProperties, outputPropertyNames, declaredOutputFilePaths);
            this.snapshotRepository = snapshotRepository;
        }

        @Override
        public ImmutableSortedMap<String, FileCollectionSnapshot> getInputFilesSnapshot() {
            if (inputFilesSnapshot == null) {
                ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                for (Map.Entry<String, Long> entry : inputFilesSnapshotIds.entrySet()) {
                    builder.put(entry.getKey(), snapshotRepository.get(entry.getValue()));
                }
                inputFilesSnapshot = builder.build();
            }
            return inputFilesSnapshot;
        }

        @Override
        public void setInputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshot) {
            this.inputFilesSnapshot = inputFilesSnapshot;
            this.inputFilesSnapshotIds = null;
        }

        @Override
        public FileCollectionSnapshot getDiscoveredInputFilesSnapshot() {
            if (discoveredFilesSnapshot == null) {
                discoveredFilesSnapshot = snapshotRepository.get(discoveredFilesSnapshotId);
            }
            return discoveredFilesSnapshot;
        }

        @Override
        public void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot discoveredFilesSnapshot) {
            this.discoveredFilesSnapshot = discoveredFilesSnapshot;
            this.discoveredFilesSnapshotId = null;
        }

        @Override
        public ImmutableSortedMap<String, FileCollectionSnapshot> getOutputFilesSnapshot() {
            if (outputFilesSnapshot == null) {
                ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
                for (Map.Entry<String, Long> entry : outputFilesSnapshotIds.entrySet()) {
                    String propertyName = entry.getKey();
                    builder.put(propertyName, snapshotRepository.get(entry.getValue()));
                }
                outputFilesSnapshot = builder.build();
            }
            return outputFilesSnapshot;
        }

        @Override
        public void setOutputFilesSnapshot(ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshot) {
            this.outputFilesSnapshot = outputFilesSnapshot;
            outputFilesSnapshotIds = null;
        }

        public TaskExecutionSnapshot snapshot() {
            return new TaskExecutionSnapshot(
                isSuccessful(),
                getBuildInvocationId(),
                getTaskImplementation(),
                getTaskActionImplementations(),
                getOutputPropertyNamesForCacheKey(),
                getDeclaredOutputFilePaths(),
                getInputProperties(),
                inputFilesSnapshotIds,
                discoveredFilesSnapshotId,
                outputFilesSnapshotIds
            );
        }

        static class TaskExecutionSnapshotSerializer extends AbstractSerializer<TaskExecutionSnapshot> {
            private final InputPropertiesSerializer inputPropertiesSerializer;
            private final StringInterner stringInterner;

            TaskExecutionSnapshotSerializer(StringInterner stringInterner) {
                this.inputPropertiesSerializer = new InputPropertiesSerializer();
                this.stringInterner = stringInterner;
            }

            public TaskExecutionSnapshot read(Decoder decoder) throws Exception {
                boolean successful = decoder.readBoolean();

                UniqueId buildId = UniqueId.from(decoder.readString());

                ImmutableSortedMap<String, Long> inputFilesSnapshotIds = readSnapshotIds(decoder);
                ImmutableSortedMap<String, Long> outputFilesSnapshotIds = readSnapshotIds(decoder);
                Long discoveredFilesSnapshotId = decoder.readLong();

                ImplementationSnapshot taskImplementation = readImplementation(decoder);

                // We can't use an immutable list here because some hashes can be null
                int taskActionsCount = decoder.readSmallInt();
                ImmutableList.Builder<ImplementationSnapshot> taskActionImplementationsBuilder = ImmutableList.builder();
                for (int j = 0; j < taskActionsCount; j++) {
                    ImplementationSnapshot actionImpl = readImplementation(decoder);
                    taskActionImplementationsBuilder.add(actionImpl);
                }
                ImmutableList<ImplementationSnapshot> taskActionImplementations = taskActionImplementationsBuilder.build();

                int cacheableOutputPropertiesCount = decoder.readSmallInt();
                ImmutableSortedSet.Builder<String> cacheableOutputPropertiesBuilder = ImmutableSortedSet.naturalOrder();
                for (int j = 0; j < cacheableOutputPropertiesCount; j++) {
                    cacheableOutputPropertiesBuilder.add(decoder.readString());
                }
                ImmutableSortedSet<String> cacheableOutputProperties = cacheableOutputPropertiesBuilder.build();

                int outputFilesCount = decoder.readSmallInt();
                ImmutableSet.Builder<String> declaredOutputFilePathsBuilder = ImmutableSet.builder();
                for (int j = 0; j < outputFilesCount; j++) {
                    declaredOutputFilePathsBuilder.add(stringInterner.intern(decoder.readString()));
                }
                ImmutableSet<String> declaredOutputFilePaths = declaredOutputFilePathsBuilder.build();

                ImmutableSortedMap<String, ValueSnapshot> inputProperties = inputPropertiesSerializer.read(decoder);

                return new TaskExecutionSnapshot(
                    successful,
                    buildId,
                    taskImplementation,
                    taskActionImplementations,
                    cacheableOutputProperties,
                    declaredOutputFilePaths,
                    inputProperties,
                    inputFilesSnapshotIds,
                    discoveredFilesSnapshotId,
                    outputFilesSnapshotIds
                );
            }

            public void write(Encoder encoder, TaskExecutionSnapshot execution) throws Exception {
                encoder.writeBoolean(execution.isSuccessful());
                encoder.writeString(execution.getBuildInvocationId().asString());
                writeSnapshotIds(encoder, execution.getInputFilesSnapshotIds());
                writeSnapshotIds(encoder, execution.getOutputFilesSnapshotIds());
                encoder.writeLong(execution.getDiscoveredFilesSnapshotId());
                writeImplementation(encoder, execution.getTaskImplementation());
                encoder.writeSmallInt(execution.getTaskActionsImplementations().size());
                for (ImplementationSnapshot actionImpl : execution.getTaskActionsImplementations()) {
                    writeImplementation(encoder, actionImpl);
                }
                encoder.writeSmallInt(execution.getCacheableOutputProperties().size());
                for (String outputFile : execution.getCacheableOutputProperties()) {
                    encoder.writeString(outputFile);
                }
                encoder.writeSmallInt(execution.getDeclaredOutputFilePaths().size());
                for (String outputFile : execution.getDeclaredOutputFilePaths()) {
                    encoder.writeString(outputFile);
                }
                inputPropertiesSerializer.write(encoder, execution.getInputProperties());
            }

            private static ImplementationSnapshot readImplementation(Decoder decoder) throws IOException {
                String typeName = decoder.readString();
                HashCode classLoaderHash = decoder.readBoolean() ? HashCode.fromBytes(decoder.readBinary()) : null;
                return new ImplementationSnapshot(typeName, classLoaderHash);
            }

            private static void writeImplementation(Encoder encoder, ImplementationSnapshot implementation) throws IOException {
                encoder.writeString(implementation.getTypeName());
                if (implementation.hasUnknownClassLoader()) {
                    encoder.writeBoolean(false);
                } else {
                    encoder.writeBoolean(true);
                    encoder.writeBinary(implementation.getClassLoaderHash().asBytes());
                }
            }

            private static ImmutableSortedMap<String, Long> readSnapshotIds(Decoder decoder) throws IOException {
                int count = decoder.readSmallInt();
                ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
                for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
                    String property = decoder.readString();
                    long id = decoder.readLong();
                    builder.put(property, id);
                }
                return builder.build();
            }

            private static void writeSnapshotIds(Encoder encoder, Map<String, Long> ids) throws IOException {
                encoder.writeSmallInt(ids.size());
                for (Map.Entry<String, Long> entry : ids.entrySet()) {
                    encoder.writeString(entry.getKey());
                    encoder.writeLong(entry.getValue());
                }
            }
        }
    }
}
