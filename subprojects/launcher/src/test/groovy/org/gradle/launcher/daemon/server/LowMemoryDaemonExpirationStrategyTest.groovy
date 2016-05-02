/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.launcher.daemon.server

import com.google.common.base.Strings
import org.gradle.launcher.daemon.server.health.MemoryInfo
import spock.lang.Specification

class LowMemoryDaemonExpirationStrategyTest extends Specification {
    private final Daemon daemon = Mock(Daemon)
    private final MemoryInfo mockMemoryInfo = Mock(MemoryInfo)

    def "daemon should expire when memory falls below threshold"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, 5)

        when:
        1 * mockMemoryInfo.getFreePhysicalMemory() >> { 2 }

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration(daemon)
        result.isExpired()
        result.reason == "Free system memory (2 bytes) is below threshold of 5 bytes"
    }

    def "daemon should not expire when memory is above threshold"() {
        given:
        LowMemoryDaemonExpirationStrategy expirationStrategy = new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, 5)

        when:
        1 * mockMemoryInfo.getFreePhysicalMemory() >> { 10 }

        then:
        DaemonExpirationResult result = expirationStrategy.checkExpiration(daemon)
        !result.isExpired()
        Strings.isNullOrEmpty(result.reason)
    }

    def "strategy computes total memory percentage"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { 10 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(0.2, mockMemoryInfo)
        expirationStrategy.minFreeMemoryBytes == 2
    }

    def "strategy computes total memory percentage of zero"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { 10 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(0, mockMemoryInfo)
        expirationStrategy.minFreeMemoryBytes == 0
    }

    def "strategy computes total memory percentage of one"() {
        when:
        1 * mockMemoryInfo.getTotalPhysicalMemory() >> { 10 }

        then:
        LowMemoryDaemonExpirationStrategy expirationStrategy = LowMemoryDaemonExpirationStrategy.belowFreePercentage(1, mockMemoryInfo)
        expirationStrategy.minFreeMemoryBytes == 10
    }

    def "strategy does not accept negative threshold"() {
        when:
        new LowMemoryDaemonExpirationStrategy(mockMemoryInfo, -1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept percentage less than 0"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(-0.1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept percentage greater than 1"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(1.1)

        then:
        thrown IllegalArgumentException
    }

    def "strategy does not accept NaN percentage"() {
        when:
        LowMemoryDaemonExpirationStrategy.belowFreePercentage(Double.NaN)

        then:
        thrown IllegalArgumentException
    }
}
