/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.specs.AndSpec;
import org.gradle.api.specs.Spec;

@NonNullApi
public interface TaskOutputsEnterpriseInternal extends TaskOutputsInternal {

    AndSpec<? super TaskInternal> getStoreInCacheSpec();

    /**
     * Called after the task finishes executing. If the spec returns false, the outputs will not be stored in the cache.
     */
    void storeInCacheWhen(Spec<? super Task> spec);

}
