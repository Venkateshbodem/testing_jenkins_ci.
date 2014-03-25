/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.externalresource.transport.sftp;

import org.apache.sshd.client.SftpClient;
import org.gradle.api.Transformer;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceLister;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.util.CollectionUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class SftpResourceLister implements ExternalResourceLister {
    private final SftpClientFactory sftpClientFactory;

    public SftpResourceLister(SftpClientFactory sftpClientFactory) {
        this.sftpClientFactory = sftpClientFactory;
    }

    private URI toUri(String location) {
        URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            throw new ResourceException(String.format("Unable to create URI from string '%s' ", location), e);
        }
        return uri;
    }

    public List<String> list(final String parent) throws IOException {
        URI uri = toUri(parent);
        String path = uri.getPath();
        SftpClient client = sftpClientFactory.createSftpClient(uri);

        try {
            if (client.lstat(path) != null) {
                return CollectionUtils.collect(client.readDir(path), new Transformer<String, SftpClient.DirEntry>() {
                    public String transform(SftpClient.DirEntry dirEntry) {
                        return parent + dirEntry.filename;
                    }
                });
            } else {
                return null;
            }
        } finally {
            client.close();
        }
    }
}
