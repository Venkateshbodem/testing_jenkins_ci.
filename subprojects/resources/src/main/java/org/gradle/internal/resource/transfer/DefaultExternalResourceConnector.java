/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.resource.transfer;

import org.gradle.api.Nullable;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class DefaultExternalResourceConnector implements ExternalResourceConnector {
    private final ExternalResourceAccessor accessor;
    private final ExternalResourceLister lister;
    private final ExternalResourceUploader uploader;

    public DefaultExternalResourceConnector(ExternalResourceAccessor accessor, ExternalResourceLister lister, ExternalResourceUploader uploader) {
        this.accessor = accessor;
        this.lister = lister;
        this.uploader = uploader;
    }

    @Nullable
    @Override
    public ExternalResource getResource(URI location) throws IOException {
        return accessor.getResource(location);
    }

    @Nullable
    @Override
    public HashValue getResourceSha1(URI location) {
        return accessor.getResourceSha1(location);
    }

    @Nullable
    @Override
    public ExternalResourceMetaData getMetaData(URI location) throws IOException {
        return accessor.getMetaData(location);
    }

    @Nullable
    @Override
    public List<String> list(URI parent) throws IOException {
        return lister.list(parent);
    }

    @Override
    public void upload(Factory<InputStream> sourceFactory, Long contentLength, URI destination) throws IOException {
        uploader.upload(sourceFactory, contentLength, destination);
    }
}
