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

package org.gradle.caching.local.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.PersistentCache;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class DirectoryBuildCacheCleanup implements Action<PersistentCache> {
    private static final Logger LOGGER = Logging.getLogger(DirectoryBuildCacheCleanup.class);
    private static final Comparator<File> NEWEST_FIRST = new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
            // Sort with the oldest last
            return Ordering.natural().compare(o2.lastModified(), o1.lastModified());
        }
    };

    private static final Pattern CACHE_ENTRY_PATTERN = Pattern.compile("\\p{XDigit}{32}(.part)?");
    private final long targetSizeInMB;

    DirectoryBuildCacheCleanup(long targetSizeInMB) {
        this.targetSizeInMB = targetSizeInMB;
    }

    @Override
    public void execute(PersistentCache persistentCache) {
        File[] filesEligibleForCleanup = findEligibleFiles(persistentCache.getBaseDir());
        List<File> filesForDeletion = findFilesToDelete(filesEligibleForCleanup);
        cleanupFiles(filesForDeletion);
    }

    List<File> findFilesToDelete(File[] filesEligibleForCleanup) {
        Arrays.sort(filesEligibleForCleanup, NEWEST_FIRST);

        // All sizes are in bytes
        long totalSize = 0;
        long targetSize = targetSizeInMB * 1024 * 1024;
        final List<File> filesForDeletion = Lists.newArrayList();

        for (File file : filesEligibleForCleanup) {
            long size = file.length();
            totalSize += size;

            if (totalSize > targetSize) {
                filesForDeletion.add(file);
            }
        }

        LOGGER.info("Build cache consuming {} MB (target: {} MB).", FileUtils.byteCountToDisplaySize(totalSize), targetSizeInMB);

        return filesForDeletion;
    }

    File[] findEligibleFiles(File cacheDir) {
        return cacheDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return canBeDeleted(name);
            }
        });
    }

    void cleanupFiles(final List<File> filesForDeletion) {
        if (!filesForDeletion.isEmpty()) {
            // Need to remove some files
            long removedSize = deleteFile(filesForDeletion);
            LOGGER.info("Build cache removing {} cache entries ({} MB reclaimed).", filesForDeletion.size(), FileUtils.byteCountToDisplaySize(removedSize));
        }
    }

    private long deleteFile(List<File> files) {
        long removedSize = 0;
        for (File file : files) {
            try {
                if (file.delete()) {
                    removedSize += file.length();
                }
            } catch (Exception e) {
                LOGGER.debug("Could not clean up cache entry " + file, e);
            }
        }
        return removedSize;
    }

    static boolean canBeDeleted(String name) {
        return CACHE_ENTRY_PATTERN.matcher(name).matches();
    }
}
