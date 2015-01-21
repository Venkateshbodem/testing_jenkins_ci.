/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resource.transport.aws.s3;
import org.gradle.internal.resource.AbstractExternalResource;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.jets3t.service.ServiceException;
import org.jets3t.service.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;

public class S3Resource extends AbstractExternalResource {

    private final S3Object s3Object;
    private final URI uri;

    public S3Resource(S3Object s3Object, URI uri) {
        this.s3Object = s3Object;
        this.s3Object.getContentLength();
        this.uri = uri;
    }

    @Override
    protected InputStream openStream() throws IOException {
        try{
            return s3Object.getDataInputStream();
        }catch (ServiceException e){
            throw new IOException(e.getMessage());
        }
    }

    public URI getURI() {
        return uri;
    }

    public long getContentLength() {
        return s3Object.getContentLength();
    }

    public boolean isLocal() {
        return false;
    }

    public ExternalResourceMetaData getMetaData() {
        Date lastModified = s3Object.getLastModifiedDate();
        DefaultExternalResourceMetaData defaultExternalResourceMetaData = new DefaultExternalResourceMetaData(uri,
                lastModified.getTime(),
                getContentLength(),
                s3Object.getETag(),
                null); // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
        return defaultExternalResourceMetaData;
    }
}
