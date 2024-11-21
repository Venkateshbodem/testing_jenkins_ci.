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

package org.gradle.caching.configuration;

import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;
import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.SETTER;

/**
 * Configuration object for a build cache.
 *
 * @since 3.5
 */
public interface BuildCache {

    /**
     * Controls whether the build cache is enabled.
     */
    @ReplacesEagerProperty(originalType = boolean.class, replacedAccessors = {
        @ReplacedAccessor(value = GETTER, name = "isEnabled", originalType = boolean.class),
        @ReplacedAccessor(value = SETTER, name = "setEnabled", originalType = boolean.class)
    })
    Property<Boolean> getEnabled();

    /**
     * Controls whether the build cache is enabled.
     */
    // kotlin source compatibility
    @Deprecated
    @ReplacedBy("getEnabled()")
    Property<Boolean> getIsEnabled();

    /**
     * Controls whether a given build can store outputs in the build cache.
     */
    @ReplacesEagerProperty(originalType = boolean.class, replacedAccessors = {
        @ReplacedAccessor(value = GETTER, name = "isPush", originalType = boolean.class),
        @ReplacedAccessor(value = SETTER, name = "setPush", originalType = boolean.class)
    })
    Property<Boolean> getPush();

    /**
     * Controls whether a given build can store outputs in the build cache.
     */
    // kotlin source compatibility
    @Deprecated
    @ReplacedBy("getPush()")
    Property<Boolean> getIsPush();
}
