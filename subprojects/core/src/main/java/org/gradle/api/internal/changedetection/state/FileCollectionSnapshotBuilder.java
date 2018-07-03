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

import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalMissingSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;

/**
 * A {@link FileCollectionSnapshotBuilder} is used to visit all snapshots created by the {@link FileSystemSnapshotter} for a {@link org.gradle.api.file.FileCollection}
 * and then create a {@link FileCollectionSnapshot} from them.
 */
public interface FileCollectionSnapshotBuilder {

    /**
     * Visits the root and the descendants of a {@link org.gradle.api.file.FileTree} or a {@link org.gradle.api.internal.file.collections.DirectoryFileTree}.
     */
    void visitFileTreeSnapshot(PhysicalSnapshot tree);

    /**
     * Visits a {@link PhysicalFileSnapshot} in the root of the {@link org.gradle.api.file.FileCollection}.
     */
    void visitFileSnapshot(PhysicalFileSnapshot file);

    /**
     * Visits a {@link PhysicalMissingSnapshot} in the root of the {@link org.gradle.api.file.FileCollection}.
     */
    void visitMissingFileSnapshot(PhysicalMissingSnapshot missingFile);

    /**
     * Creates the {@link FileCollectionSnapshot} containing the visited elements.
     */
    FileCollectionSnapshot build();
}
