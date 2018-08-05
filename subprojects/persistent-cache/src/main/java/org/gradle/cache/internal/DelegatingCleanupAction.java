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

package org.gradle.cache.internal;

import org.gradle.cache.CleanableStore;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.CleanupProgressMonitor;

public class DelegatingCleanupAction implements CleanupAction {

    private final CleanupAction delegate;
    private final CacheCleanupFileAccessTimeProvider provider;

    public DelegatingCleanupAction(CleanupAction delegate, CacheCleanupFileAccessTimeProvider provider) {
        this.delegate = delegate;
        this.provider = provider;
    }

    @Override
    public void clean(CleanableStore cleanableStore, CleanupProgressMonitor progressMonitor) {
        try {
            delegate.clean(cleanableStore, progressMonitor);
        } finally {
            provider.close();
        }
    }

}
