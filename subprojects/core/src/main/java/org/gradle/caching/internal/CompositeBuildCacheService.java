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

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;

public class CompositeBuildCacheService implements BuildCacheService {
    private final BuildCacheService local;
    private final BuildCacheService remote;
    private final boolean pushToRemote;

    CompositeBuildCacheService(BuildCacheService local, BuildCacheService remote, boolean pushToRemote) {
        this.local = local;
        this.remote = remote;
        this.pushToRemote = pushToRemote;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        if (!local.load(key, reader)) {
            return remote.load(key, reader);
        }
        return true;
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushToRemote) {
            remote.store(key, writer);
        } else {
            local.store(key, writer);
        }
    }

    @Override
    public String getDescription() {
        if (pushToRemote) {
            return local.getDescription() + " and " + remote.getDescription() + " (pushing enabled)";
        } else {
            return local.getDescription() + " (pushing enabled)" + " and " + remote.getDescription();
        }
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(local, remote).stop();
    }
}
