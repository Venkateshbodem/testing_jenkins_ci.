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

package org.gradle.caching.internal;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.cache.CacheRepository;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;

import javax.inject.Inject;

public class DefaultDirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    private final CacheRepository cacheRepository;
    private final FileResolver resolver;

    @Inject
    public DefaultDirectoryBuildCacheServiceFactory(CacheRepository cacheRepository, FileResolver resolver) {
        this.cacheRepository = cacheRepository;
        this.resolver = resolver;
    }

    @Override
    public BuildCacheService createBuildCacheService(DirectoryBuildCache configuration) {
        Object cacheDirectory = configuration.getDirectory();
        return cacheDirectory != null
            ? new DirectoryBuildCacheService(cacheRepository, resolver.resolve(cacheDirectory))
            : new DirectoryBuildCacheService(cacheRepository, "build-cache");
    }
}
