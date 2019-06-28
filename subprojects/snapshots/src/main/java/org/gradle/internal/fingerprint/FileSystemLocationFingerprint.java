/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.fingerprint;

import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashable;
import org.gradle.internal.hash.Hashing;

/**
 * An immutable fingerprint of some aspects of a file's metadata and content.
 *
 * Should implement {@link #equals(Object)} and {@link #hashCode()} to compare these aspects.
 * Comparisons are very frequent, so these methods need to be fast.
 *
 * File fingerprints are cached between builds, so their memory footprint should be kept to a minimum.
 */
public interface FileSystemLocationFingerprint extends Comparable<FileSystemLocationFingerprint>, Hashable {
    HashCode DIR_SIGNATURE = Hashing.signature("DIR");
    HashCode MISSING_FILE_SIGNATURE = Hashing.signature("MISSING");
    HashCode UNAVAILABLE_FILE_SIGNATURE = Hashing.signature("UNAVAILABLE");

    String getNormalizedPath();
    HashCode getNormalizedContentHash();
    FileType getType();
}
