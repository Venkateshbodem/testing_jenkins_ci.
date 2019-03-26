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

package org.gradle.internal.execution;

import org.gradle.caching.BuildCacheKey;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface CacheHandler {
    <T> Optional<T> load(Function<BuildCacheKey, Optional<T>> loader);
    void store(Consumer<BuildCacheKey> storer);

    CacheHandler NO_OP = new CacheHandler() {
        @Override
        public <T> Optional<T> load(Function<BuildCacheKey, Optional<T>> loader) {
            return Optional.empty();
        }

        @Override
        public void store(Consumer<BuildCacheKey> storer) {
        }
    };
}
