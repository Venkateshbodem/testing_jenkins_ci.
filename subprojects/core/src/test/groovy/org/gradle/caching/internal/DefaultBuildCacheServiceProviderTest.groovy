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

package org.gradle.caching.internal

import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.caching.BuildCacheService
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.AbstractBuildCache
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal
import org.gradle.caching.local.LocalBuildCache
import spock.lang.Specification

class DefaultBuildCacheServiceProviderTest extends Specification {

    private localCacheService = Stub(BuildCacheService) {
        getDescription() >> 'a local build cache'
    }
    private remoteCacheService = Stub(BuildCacheService) {
        getDescription() >> 'a remote build cache'
    }
    private LocalBuildCache localBuildCache
    private RemoteBuildCache remoteBuildCache
    private buildCacheConfiguration = Stub(BuildCacheConfigurationInternal) {
        getLocal() >> { localBuildCache }
        getRemote() >> { remoteBuildCache }
    }
    private buildCacheEnabled = true
    private startParameter = Stub(StartParameter) {
        isTaskOutputCacheEnabled() >> { buildCacheEnabled }
        getSystemPropertiesArgs() >> [:]
    }
    private instantiator = Mock(BuildCacheServiceInstantiator)
    private DefaultBuildCacheServiceProvider provider = new DefaultBuildCacheServiceProvider(buildCacheConfiguration, startParameter, instantiator)

    def 'local cache service is created'() {
        localBuildCache = new LocalBuildCache()
        this.remoteBuildCache = remoteBuildCache

        when:
        def buildCacheService = provider.create()

        then:
        1 * instantiator.createBuildCacheService(localBuildCache, false, false) >> localCacheService
        0 * _

        buildCacheService == localCacheService

        where:
        remoteBuildCache << [null, new RemoteBuildCache(enabled: false)]
    }

    def 'remote cache service is created'() {
        this.localBuildCache = localBuildCache
        remoteBuildCache = new RemoteBuildCache()

        when:
        def buildCacheService = provider.create()

        then:
        1 * instantiator.createBuildCacheService(remoteBuildCache, false, false) >> remoteCacheService
        0 * _

        buildCacheService == remoteCacheService

        where:
        localBuildCache << [null, new LocalBuildCache(enabled: false)]
    }

    def 'can push to local'() {
        localBuildCache = new LocalBuildCache(push: true)
        remoteBuildCache = new RemoteBuildCache(push: false)

        when:
        def buildCacheService = provider.create()

        then:
        1 * instantiator.createBuildCacheService(localBuildCache, false, false) >> localCacheService
        1 * instantiator.createBuildCacheService(remoteBuildCache, false, true) >> remoteCacheService
        0 * _

        buildCacheService instanceof CompositeBuildCacheService
        buildCacheService.description == 'a local build cache(pushing enabled) and a remote build cache'
    }

    def 'can push to remote'() {
        localBuildCache = new LocalBuildCache(push: false)
        remoteBuildCache = new RemoteBuildCache(push: true)

        when:
        def buildCacheService = provider.create()

        then:
        1 * instantiator.createBuildCacheService(localBuildCache, false, true) >> localCacheService
        1 * instantiator.createBuildCacheService(remoteBuildCache, false, false) >> remoteCacheService
        0 * _

        buildCacheService instanceof CompositeBuildCacheService
        buildCacheService.description == 'a local build cache and a remote build cache(pushing enabled)'
    }

    def 'can pull from local and remote'() {
        localBuildCache = new LocalBuildCache(push: false)
        remoteBuildCache = new RemoteBuildCache(push: false)

        when:
        def buildCacheService = provider.create()

        then:
        1 * instantiator.createBuildCacheService(localBuildCache, false, true) >> localCacheService
        1 * instantiator.createBuildCacheService(remoteBuildCache, false, true) >> remoteCacheService
        0 * _

        buildCacheService instanceof CompositeBuildCacheService
        buildCacheService.description == 'a local build cache and a remote build cache'
    }

    def 'when caching is disabled no services are created'() {
        buildCacheEnabled = false

        when:
        def buildCacheService = provider.create()

        then:
        0 * _

        buildCacheService.description == 'NO-OP build cache'
    }

    def 'fails when local and remote have push enabled'() {
        localBuildCache = new LocalBuildCache(push: true)
        remoteBuildCache = new RemoteBuildCache(push: true)

        when:
        provider.create()

        then:
        1 * instantiator.createBuildCacheService(localBuildCache, false, false) >> localCacheService
        1 * instantiator.createBuildCacheService(remoteBuildCache, false, false) >> remoteCacheService
        0 * _

        def e = thrown(GradleException)
        e.message == 'It is only allowed to push to a remote or a local build cache, not to both. Disable push for one of the caches.'
    }

    private static class RemoteBuildCache extends AbstractBuildCache {}
    private static class RemoteBuildCacheServiceFactory implements BuildCacheServiceFactory<RemoteBuildCache> {
        @Override
        BuildCacheService build(RemoteBuildCache configuration) {
            return null
        }
    }
}
