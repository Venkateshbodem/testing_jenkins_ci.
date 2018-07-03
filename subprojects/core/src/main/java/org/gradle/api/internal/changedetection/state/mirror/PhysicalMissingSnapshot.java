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

package org.gradle.api.internal.changedetection.state.mirror;

import org.gradle.api.internal.changedetection.state.MissingFileContentSnapshot;
import org.gradle.internal.file.FileType;

/**
 * Represents a missing file.
 */
public class PhysicalMissingSnapshot extends AbstractPhysicalSnapshot {
    /**
     * A missing file where we don't know where it actually is.
     * This can happen if we try to get a {@link PhysicalSnapshot} for a {@link org.gradle.api.internal.file.FileTreeInternal} which is not a directory tree.
     */
    public static final PhysicalSnapshot INSTANCE = new PhysicalMissingSnapshot("", "") {
        @Override
        public String getPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(PhysicalSnapshotVisitor visitor) {
        }
    };

    public PhysicalMissingSnapshot(String path, String name) {
        super(path, name);
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public void accept(PhysicalSnapshotVisitor visitor) {
        visitor.visit(getPath(), getName(), MissingFileContentSnapshot.INSTANCE);
    }
}
