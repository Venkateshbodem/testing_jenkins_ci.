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

package org.gradle.api.publication.maven.internal;


import org.apache.commons.io.IOUtils;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.*;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.proxy.ProxyInfoProvider;
import org.apache.maven.wagon.repository.Repository;
import org.apache.maven.wagon.resource.Resource;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.apache.maven.wagon.events.SessionEvent.*;
import static org.apache.maven.wagon.events.TransferEvent.*;

/**
 * A maven wagon intended to work with {@link org.apache.maven.artifact.manager.DefaultWagonManager} Maven uses reflection to initialize instances of this wagon see: {@link
 * org.codehaus.plexus.component.factory.java.JavaComponentFactory#newInstance(org.codehaus.plexus.component.repository.ComponentDescriptor, org.codehaus.classworlds.ClassRealm,
 * org.codehaus.plexus.PlexusContainer)}
 */
public class RepositoryTransportDeployWagon implements Wagon {

    private SessionEventSupport sessionEventSupport = new SessionEventSupport();
    private TransferEventSupport transferEventSupport = new TransferEventSupport();
    private Repository mutatingRepository;
    protected RepositoryTransportDeployDelegate delegate;
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryTransportDeployWagon.class);

    public void createDelegate(String scheme, MavenArtifactRepository artifactRepository, RepositoryTransportFactory repositoryTransportFactory) {
        delegate = new RepositoryTransportDeployDelegate(scheme, artifactRepository, repositoryTransportFactory);
    }

    @Override
    public final void get(String resourceName, File destination) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        Resource resource = new Resource(resourceName);
        this.transferEventSupport.fireTransferInitiated(transferEvent(resource, TRANSFER_INITIATED, REQUEST_GET));
        this.transferEventSupport.fireTransferStarted(transferEvent(resource, TRANSFER_STARTED, REQUEST_GET));
        try {
            if (!destination.exists()) {
                LOGGER.debug("Wagon deployment supplied a file [{}] which does not exist, forcing create.", destination.getAbsolutePath());
                destination.getParentFile().mkdirs();
                destination.createNewFile();
            }
            if (!delegate.getAndWriteFile(destination, resourceName)) {
                throw new ResourceDoesNotExistException(String.format("'%s' does not exist", resourceName));
            }
            signalMavenToGenerateChecksums(destination, resource, REQUEST_GET);
            this.transferEventSupport.fireTransferCompleted(transferEvent(resource, TRANSFER_COMPLETED, REQUEST_GET));
        } catch (IOException e) {
            this.transferEventSupport.fireTransferError(transferEvent(resource, TRANSFER_ERROR, REQUEST_GET));
            throw new TransferFailedException(String.format("Cannot get and write file '%s'", resourceName), e);
        }
    }

    @Override
    public final void put(File file, String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        Resource resource = new Resource(resourceName);
        this.transferEventSupport.fireTransferInitiated(transferEvent(resource, TRANSFER_INITIATED, REQUEST_PUT));
        this.transferEventSupport.fireTransferStarted(transferEvent(resource, TRANSFER_STARTED, REQUEST_PUT));
        try {
            delegate.putFile(file, resourceName);
            signalMavenToGenerateChecksums(file, resource, REQUEST_PUT);
        } catch (IOException e) {
            this.transferEventSupport.fireTransferError(transferEvent(resource, e, REQUEST_PUT));
            throw new GradleException(String.format("Could not put file to remote location: %s", resourceName), e);
        }
        this.transferEventSupport.fireTransferCompleted(transferEvent(resource, TRANSFER_COMPLETED, REQUEST_PUT));
    }

    @Override
    public final boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {
        throwNotImplemented("getIfNewer(String resourceName, File file, long timestamp)");
        return false;
    }

    @Override
    public final boolean getIfNewer(String resourceName, File file, long timestamp) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("getIfNewer(String resourceName, File file, long timestamp)");
        return false;
    }

    @Override
    public final void putDirectory(File file, String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("putDirectory(File file, String resourceName)");
    }

    @Override
    public final List getFileList(String resourceName) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        throwNotImplemented("getFileList(String resourceName)");
        return null;
    }

    @Override
    public final boolean supportsDirectoryCopy() {
        return false;
    }

    @Override
    public final Repository getRepository() {
        return this.mutatingRepository;
    }

    @Override
    public final void openConnection() throws ConnectionException, AuthenticationException {
    }

    @Override
    public final void connect(Repository repository) throws ConnectionException, AuthenticationException {
        this.mutatingRepository = repository;
        this.sessionEventSupport.fireSessionLoggedIn(sessionEvent(SESSION_LOGGED_IN));
        this.sessionEventSupport.fireSessionOpened(sessionEvent(SESSION_OPENED));
    }

    @Override
    public final void connect(Repository repository, ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, ProxyInfoProvider proxyInfoProvider) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo, ProxyInfo proxyInfo) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void connect(Repository repository, AuthenticationInfo authenticationInfo, ProxyInfoProvider proxyInfoProvider) throws ConnectionException, AuthenticationException {
        connect(repository);
    }

    @Override
    public final void disconnect() throws ConnectionException {
        this.sessionEventSupport.fireSessionDisconnecting(sessionEvent(SESSION_DISCONNECTING));
        this.sessionEventSupport.fireSessionLoggedOff(sessionEvent(SESSION_LOGGED_OFF));
        this.sessionEventSupport.fireSessionDisconnected(sessionEvent(SESSION_LOGGED_OFF));
    }

    @Override
    public final void addSessionListener(SessionListener sessionListener) {
        this.sessionEventSupport.addSessionListener(sessionListener);
    }

    @Override
    public final void removeSessionListener(SessionListener sessionListener) {
        this.sessionEventSupport.removeSessionListener(sessionListener);
    }

    @Override
    public final boolean hasSessionListener(SessionListener sessionListener) {
        return this.sessionEventSupport.hasSessionListener(sessionListener);
    }

    @Override
    public final void addTransferListener(TransferListener transferListener) {
        this.transferEventSupport.addTransferListener(transferListener);
    }

    @Override
    public final void removeTransferListener(TransferListener transferListener) {
        this.transferEventSupport.removeTransferListener(transferListener);
    }

    @Override
    public final boolean hasTransferListener(TransferListener transferListener) {
        return this.transferEventSupport.hasTransferListener(transferListener);
    }

    @Override
    public final boolean isInteractive() {
        return false;
    }

    @Override
    public final void setInteractive(boolean b) {

    }

    @Override
    public final void setTimeout(int i) {

    }

    @Override
    public final int getTimeout() {
        return 0;
    }

    private SessionEvent sessionEvent(int e) {
        return new SessionEvent(this, e);
    }

    private void throwNotImplemented(String s) {
        throw new GradleException("This wagon does not yet support the method:" + s);
    }

    private TransferEvent transferEvent(Resource resource, int eventType, int requestType) {
        TransferEvent transferEvent = new TransferEvent(this, resource, eventType, requestType);
        transferEvent.setTimestamp(new Date().getTime());
        return transferEvent;
    }

    private TransferEvent transferEvent(Resource resource, Exception e, int requestType) {
        return new TransferEvent(this, resource, e, requestType);
    }

    /**
     * Required to signal to maven that a file has been successfully uploaded (put) or retrieved (get)
     * Without this event, incorrect checksums are generated (usually a sha1 or m5d of an empty string)
     *
     * e.g Artifactory: Sending HTTP error code 409: Checksum error
     * for 'org/group/name/publish/2.0/publish-2.0.jar.md5': received 'd41d8cd98f00b204e9800998ecf8427e' but actual is '2414d662325e5b4f912b68f9766d344a'.
     *
     * @param file - the file which has been uploaded
     * @param resource - the maven resource
     */
    private void signalMavenToGenerateChecksums(File file, Resource resource, int requestMethod) {
        TransferEvent transferEvent = transferEvent(resource, TransferEvent.TRANSFER_PROGRESS, requestMethod);
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            while (true) {
                int nread = in.read(buffer);
                if (nread < 0) {
                    break;
                }
                this.transferEventSupport.fireTransferProgress(transferEvent, buffer, nread);
            }
            in.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
