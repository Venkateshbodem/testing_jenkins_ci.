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

package org.gradle.api.publication.maven.internal

import org.apache.maven.wagon.ResourceDoesNotExistException
import org.apache.maven.wagon.TransferFailedException
import org.apache.maven.wagon.authentication.AuthenticationInfo
import org.apache.maven.wagon.events.SessionListener
import org.apache.maven.wagon.events.TransferListener
import org.apache.maven.wagon.proxy.ProxyInfo
import org.apache.maven.wagon.proxy.ProxyInfoProvider
import org.apache.maven.wagon.repository.Repository
import org.gradle.api.GradleException
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class RepositoryTransportDeployWagonTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider testDirectory = new TestNameTestDirectoryProvider()

    def "wagon connections attempts should set a repository and signal session opening events"() {
        setup:
        SessionListener sessionListener = Mock()
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addSessionListener(sessionListener)

        when:
        wagon."$method"(*args)

        then:
        wagon.getRepository()
        1 * sessionListener.sessionLoggedIn(_)
        1 * sessionListener.sessionOpened(_)

        where:
        method    | args
        'connect' | [Mock(Repository)]
        'connect' | [Mock(Repository), Mock(ProxyInfo)]
        'connect' | [Mock(Repository), Mock(ProxyInfoProvider)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo), Mock(ProxyInfo)]
        'connect' | [Mock(Repository), Mock(AuthenticationInfo), Mock(ProxyInfoProvider)]
    }

    def "waggon disconnect should signal disconnection events"() {
        setup:
        SessionListener sessionListener = Mock()
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addSessionListener(sessionListener)

        when:
        wagon.disconnect()

        then:
        1 * sessionListener.sessionDisconnecting(_)
        1 * sessionListener.sessionLoggedOff(_)
        1 * sessionListener.sessionDisconnected(_);
    }

    def "should throw GradleException for a bunch of unused wagon methods"() {
        setup:
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()

        when:
        wagon."$method"(*args)

        then:
        thrown(GradleException)

        where:
        method           | args
        'getFileList'    | ['s']
        'getIfNewer'     | ['a', Mock(File), 0]
        'putDirectory'   | [Mock(File), 'a']
        'resourceExists' | ['a']
    }

    def "should provide defaults which ignore maven centric stuff"() {
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()

        expect:
        !wagon.supportsDirectoryCopy()
        wagon.getTimeout() == 0
        !wagon.isInteractive()
    }

    def "should create a RepositoryTransportDeployDelegate"() {
        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        MavenArtifactRepository mavenArtifactRepository = Mock()
        RepositoryTransportFactory repositoryTransportFactory = Mock()

        when:
        wagon.createDelegate("scheme", mavenArtifactRepository, repositoryTransportFactory)

        then:
        wagon.delegate.artifactRepository == mavenArtifactRepository
    }

    @Unroll
    def "should signal #expectedProgressCount progress events on a successful upload of #byteSize bytes"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        byte[] b = new byte[byteSize]
        new Random().nextBytes(b)
        file << b

        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate

        when:
        wagon.put(file, resourceName)

        //Order matters
        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        1 * delegate.putFile(file, resourceName)
        then:
        expectedProgressCount * transferListener.transferProgress(*_)
        then:
        1 * transferListener.transferCompleted(_)
        then:
        0 * transferListener._

        where:
        byteSize | expectedProgressCount
        4096     | 1
        4097     | 2
        8192     | 2
        8193     | 3
    }

    def "should signal correct events on a failed upload"() {
        setup:
        SessionListener sessionListener = Mock()
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()
        delegate.putFile(*_) >> { throw new IOException("failed") }

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addSessionListener(sessionListener)
        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate

        when:
        wagon.put(file, resourceName)

        then:
        1 * transferListener.transferInitiated(_)
        1 * transferListener.transferStarted(_)
        1 * transferListener.transferError(_)

        then:
        0 * transferListener._

        then:
        def ex = thrown(GradleException)
        ex.message.startsWith('Could not put file to remote location:')

    }

    def "should signal the correct events on a successful retrieval"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        file << "someText"
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        1 * delegate.getAndWriteFile(file, resourceName) >> true
        then:
        1 * transferListener.transferProgress(*_)
        then:
        1 * transferListener.transferCompleted(*_)
        then:
        0 * transferListener._
    }

    def "should create the destination file if the deployer supplies a file which does not exist"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def resourceName = '/some/resource.jar'

        TestFile file = testDirectory.createFile('target.jar')
        file.delete()

        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate

        when:
        assert !file.exists()
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)
        then:
        1 * delegate.getAndWriteFile(file, resourceName) >> true
        then:
        1 * transferListener.transferCompleted(*_)
        then:
        0 * transferListener._

        and:
        file.exists()
    }

    def "should throw ResourceDoesNotExistException and signal events when the remote resource does not exist"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)

        then:
        delegate.getAndWriteFile(file, resourceName) >> false

        then: "Normally indicates to the deployer that it's a first time snapshot publish"
        thrown(ResourceDoesNotExistException)
    }

    def "should throw TransferFailedException and signal events when failed to download a remote resource"() {
        setup:
        TransferListener transferListener = Mock()
        RepositoryTransportDeployDelegate delegate = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        def file = testDirectory.createFile('target.jar')
        def resourceName = '/some/resource.jar'

        wagon.addTransferListener(transferListener)
        wagon.delegate = delegate
        delegate.getAndWriteFile(*_) >> { throw new IOException("Explode!") }

        when:
        wagon.get(resourceName, file)

        then:
        1 * transferListener.transferInitiated(_)
        then:
        1 * transferListener.transferStarted(_)

        then:
        thrown(TransferFailedException)

        then:
        1 * transferListener.transferError(_)
    }

    def "should add and remove wagon listeners"() {
        TransferListener transferListener = Mock()
        SessionListener sessionListener = Mock()

        RepositoryTransportDeployWagon wagon = new RepositoryTransportDeployWagon()
        wagon.addTransferListener(transferListener)
        wagon.addSessionListener(sessionListener)

        when:
        wagon.removeSessionListener(sessionListener)
        wagon.removeTransferListener(transferListener)

        then:
        !wagon.hasSessionListener(sessionListener)
        !wagon.hasTransferListener(transferListener)
    }
}
