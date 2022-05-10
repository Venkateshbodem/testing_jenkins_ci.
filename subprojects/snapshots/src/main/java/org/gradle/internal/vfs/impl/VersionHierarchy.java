/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;

import javax.annotation.CheckReturnValue;

public class VersionHierarchy {
    private final long version;
    private final long unrelatedToAnyChildVersion;
    private final ChildMap<VersionHierarchy> children;

    public static VersionHierarchy empty(long rootVersion) {
        return new VersionHierarchy(EmptyChildMap.getInstance(), rootVersion, rootVersion);
    }

    private VersionHierarchy(ChildMap<VersionHierarchy> children, long version, long unrelatedToAnyChildVersion) {
        this.children = children;
        this.version = version;
        this.unrelatedToAnyChildVersion = unrelatedToAnyChildVersion;
    }

   public long getVersion(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return children.withNode(relativePath, caseSensitivity, new ChildMap.NodeHandler<VersionHierarchy, Long>() {
            @Override
            public Long handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.getVersion(pathInChild, caseSensitivity);
            }

            @Override
            public Long handleAsAncestorOfChild(String childPath, VersionHierarchy child) {
                return child.getVersion();
            }

            @Override
            public Long handleExactMatchWithChild(VersionHierarchy child) {
                return child.getVersion();
            }

            @Override
            public Long handleUnrelatedToAnyChild() {
                return unrelatedToAnyChildVersion;
            }
        });
    }

    @CheckReturnValue
    public VersionHierarchy updateVersion(VfsRelativePath relativePath, long newVersion, CaseSensitivity caseSensitivity) {
        ChildMap<VersionHierarchy> newChildren = children.store(relativePath, caseSensitivity, new ChildMap.StoreHandler<VersionHierarchy>() {
            @Override
            public VersionHierarchy handleAsDescendantOfChild(VfsRelativePath pathInChild, VersionHierarchy child) {
                return child.updateVersion(pathInChild, newVersion, caseSensitivity);
            }

            @Override
            public VersionHierarchy handleAsAncestorOfChild(String childPath, VersionHierarchy child) {
                return createChild();
            }

            @Override
            public VersionHierarchy mergeWithExisting(VersionHierarchy child) {
                return createChild();
            }

            @Override
            public VersionHierarchy createChild() {
                return VersionHierarchy.empty(newVersion);
            }

            @Override
            public VersionHierarchy createNodeFromChildren(ChildMap<VersionHierarchy> children) {
                return new VersionHierarchy(children, newVersion, unrelatedToAnyChildVersion);
            }
        });
        return new VersionHierarchy(newChildren, newVersion, unrelatedToAnyChildVersion);
    }

    public long getVersion() {
        return version;
    }
}
