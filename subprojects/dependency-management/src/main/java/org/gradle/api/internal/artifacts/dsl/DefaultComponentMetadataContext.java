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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;

public class DefaultComponentMetadataContext implements ComponentMetadataContext {

    private final ComponentMetadataDetails details;
    private final ModuleComponentResolveMetadata metadata;

    public DefaultComponentMetadataContext(ComponentMetadataDetails details, ModuleComponentResolveMetadata metadata) {
        this.details = details;
        this.metadata = metadata;
    }

    @Override
    public <T> T getDescriptor(Class<T> descriptorClass) {
        if (IvyModuleDescriptor.class.isAssignableFrom(descriptorClass)) {
            if (metadata instanceof IvyModuleResolveMetadata) {
                IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metadata;
                return descriptorClass.cast(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
            }
        }
        return null;
    }

    @Override
    public ComponentMetadataDetails getDetails() {
        return details;
    }
}
