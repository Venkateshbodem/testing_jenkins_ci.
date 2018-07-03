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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IndexedNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class NameOnlyFingerprintingStrategy implements FingerprintingStrategy {

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(List<PhysicalSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (PhysicalSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {
                private boolean root = true;

                @Override
                public boolean preVisitDirectory(String path, String name) {
                    if (processedEntries.add(path)) {
                        NormalizedFileSnapshot snapshot = isRoot() ? new IgnoredPathFileSnapshot(DirContentSnapshot.INSTANCE) : new IndexedNormalizedFileSnapshot(path, path.length() - name.length(), DirContentSnapshot.INSTANCE);
                        builder.put(path, snapshot);
                    }
                    root = false;
                    return true;
                }

                @Override
                public void visit(String path, String name, FileContentSnapshot content) {
                    if (processedEntries.add(path)) {
                        builder.put(
                            path,
                            new IndexedNormalizedFileSnapshot(path, path.length() - name.length(), content));
                    }
                }

                private boolean isRoot() {
                    return root;
                }

                @Override
                public void postVisitDirectory() {
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.NORMALIZED;
    }
}
