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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.internal.file.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

public class TaskExecutionSnapshotSerializer extends AbstractSerializer<HistoricalTaskExecution> {
    private final InputPropertiesSerializer inputPropertiesSerializer;
    private final Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer;

    TaskExecutionSnapshotSerializer(Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer) {
        this.fileCollectionFingerprintSerializer = fileCollectionFingerprintSerializer;
        this.inputPropertiesSerializer = new InputPropertiesSerializer();
    }

    public HistoricalTaskExecution read(Decoder decoder) throws Exception {
        boolean successful = decoder.readBoolean();

        OriginTaskExecutionMetadata originExecutionMetadata = new OriginTaskExecutionMetadata(
            UniqueId.from(decoder.readString()),
            decoder.readLong()
        );

        ImmutableSortedMap<String, FileCollectionFingerprint> inputFilesSnapshots = readSnapshots(decoder);
        ImmutableSortedMap<String, FileCollectionFingerprint> outputFilesSnapshots = readSnapshots(decoder);

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

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = inputPropertiesSerializer.read(decoder);

        return new HistoricalTaskExecution(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            cacheableOutputProperties,
            inputFilesSnapshots,
            outputFilesSnapshots,
            successful,
            originExecutionMetadata
        );
    }

    public void write(Encoder encoder, HistoricalTaskExecution execution) throws Exception {
        encoder.writeBoolean(execution.isSuccessful());
        encoder.writeString(execution.getOriginExecutionMetadata().getBuildInvocationId().asString());
        encoder.writeLong(execution.getOriginExecutionMetadata().getExecutionTime());
        writeSnapshots(encoder, execution.getInputFilesSnapshot());
        writeSnapshots(encoder, execution.getOutputFilesSnapshot());
        writeImplementation(encoder, execution.getTaskImplementation());
        encoder.writeSmallInt(execution.getTaskActionImplementations().size());
        for (ImplementationSnapshot actionImpl : execution.getTaskActionImplementations()) {
            writeImplementation(encoder, actionImpl);
        }
        encoder.writeSmallInt(execution.getOutputPropertyNamesForCacheKey().size());
        for (String outputFile : execution.getOutputPropertyNamesForCacheKey()) {
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
            encoder.writeBinary(implementation.getClassLoaderHash().toByteArray());
        }
    }

    private ImmutableSortedMap<String, FileCollectionFingerprint> readSnapshots(Decoder decoder) throws Exception {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, FileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
            String property = decoder.readString();
            FileCollectionFingerprint fingerprint = fileCollectionFingerprintSerializer.read(decoder);
            builder.put(property, fingerprint);
        }
        return builder.build();
    }

    private void writeSnapshots(Encoder encoder, Map<String, FileCollectionFingerprint> ids) throws Exception {
        encoder.writeSmallInt(ids.size());
        for (Map.Entry<String, FileCollectionFingerprint> entry : ids.entrySet()) {
            encoder.writeString(entry.getKey());
            fileCollectionFingerprintSerializer.write(encoder, entry.getValue());
        }
    }
}
