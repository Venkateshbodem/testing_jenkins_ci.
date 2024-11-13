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

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Base implementation for build cache service configuration.
 *
 * @since 3.5
 */
public abstract class AbstractBuildCache implements BuildCache {
    private final Property<Boolean> enabled = getObjectFactory().property(Boolean.class).convention(false);
    private final Property<Boolean> push = getObjectFactory().property(Boolean.class).convention(false);

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Property<Boolean> getEnabled() {
        return enabled;
    }

    @Override
    @Deprecated
    public Property<Boolean> getIsEnabled() {
        return getEnabled();
    }

    @Override
    public Property<Boolean> getPush() {
        return push;
    }

    @Override
    @Deprecated
    public Property<Boolean> getIsPush() {
        return getPush();
    }
}
