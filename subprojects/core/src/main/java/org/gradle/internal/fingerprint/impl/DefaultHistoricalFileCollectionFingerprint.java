/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.SnapshotMapSerializer;
import org.gradle.api.internal.changedetection.state.mirror.logical.FingerprintCompareStrategy;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

public class DefaultHistoricalFileCollectionFingerprint implements HistoricalFileCollectionFingerprint {

    private final Map<String, NormalizedFileSnapshot> fingerprints;
    private final FingerprintCompareStrategy compareStrategy;
    private final HashCode hashCode;

    public DefaultHistoricalFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> fingerprints, FingerprintCompareStrategy compareStrategy) {
        this(fingerprints, compareStrategy, null);
    }

    public DefaultHistoricalFileCollectionFingerprint(Map<String, NormalizedFileSnapshot> fingerprints, FingerprintCompareStrategy compareStrategy, @Nullable HashCode hashCode) {
        this.fingerprints = fingerprints;
        this.compareStrategy = compareStrategy;
        this.hashCode = hashCode;
    }

    @Override
    public boolean visitChangesSince(FileCollectionFingerprint oldFingerprint, String title, boolean includeAdded, TaskStateChangeVisitor visitor) {
        return compareStrategy.visitChangesSince(visitor, getSnapshots(), oldFingerprint.getSnapshots(), title, includeAdded);
    }

    @VisibleForTesting
    FingerprintCompareStrategy getCompareStrategy() {
        return compareStrategy;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        return fingerprints;
    }

    @Override
    public HashCode getHash() {
        return hashCode;
    }

    @Override
    public HistoricalFileCollectionFingerprint archive() {
        return this;
    }

    public static class SerializerImpl implements Serializer<DefaultHistoricalFileCollectionFingerprint> {

        private final SnapshotMapSerializer snapshotMapSerializer;
        private final HashCodeSerializer hashCodeSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
            this.hashCodeSerializer = new HashCodeSerializer();
        }

        @Override
        public DefaultHistoricalFileCollectionFingerprint read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            FingerprintCompareStrategy compareStrategy = FingerprintCompareStrategy.values()[type];
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new DefaultHistoricalFileCollectionFingerprint(snapshots, compareStrategy, hash);
        }

        @Override
        public void write(Encoder encoder, DefaultHistoricalFileCollectionFingerprint value) throws Exception {
            encoder.writeSmallInt(value.compareStrategy.ordinal());
            HashCode hash = value.getHash();
            encoder.writeBoolean(hash != null);
            if (hash != null) {
                hashCodeSerializer.write(encoder, hash);
            }
            snapshotMapSerializer.write(encoder, value.getSnapshots());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            DefaultHistoricalFileCollectionFingerprint.SerializerImpl rhs = (DefaultHistoricalFileCollectionFingerprint.SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer);
        }
    }
}
