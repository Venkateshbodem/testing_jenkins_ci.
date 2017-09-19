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

package org.gradle.launcher.cli.converter

import org.gradle.StartParameter
import org.gradle.api.logging.LogLevel
import org.gradle.initialization.ParallelismBuildOptionFactory
import org.gradle.initialization.StartParameterBuildOptionFactory
import org.gradle.internal.logging.LoggingConfigurationBuildOptionFactory
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PropertiesToStartParameterConverterTest extends Specification {

    @Subject def converter = new PropertiesToStartParameterConverter(new StartParameterBuildOptionFactory(), new ParallelismBuildOptionFactory(), new LoggingConfigurationBuildOptionFactory())

    def "converts"() {
        expect:
        converter.convert([(ParallelismBuildOptionFactory.MaxWorkersOption.GRADLE_PROPERTY): "37"], new StartParameter()).maxWorkerCount == 37
        converter.convert([(ParallelismBuildOptionFactory.ParallelOption.GRADLE_PROPERTY): "true"], new StartParameter()).parallelProjectExecutionEnabled
        converter.convert([(StartParameterBuildOptionFactory.BuildCacheOption.GRADLE_PROPERTY): "true"], new StartParameter()).buildCacheEnabled
        converter.convert([(StartParameterBuildOptionFactory.ConfigureOnDemandOption.GRADLE_PROPERTY): "TRUE"], new StartParameter()).configureOnDemand
        !converter.convert([(StartParameterBuildOptionFactory.ConfigureOnDemandOption.GRADLE_PROPERTY): "xxx"], new StartParameter()).configureOnDemand
    }

    def invalidMaxWorkersProperty() {
        when:
        converter.convert([(ParallelismBuildOptionFactory.MaxWorkersOption.GRADLE_PROPERTY): "invalid"], new StartParameter())
        then:
        thrown(IllegalArgumentException)
    }

    @Unroll
    def "converts log levels"() {
        expect:
        converter.convert([(LoggingConfigurationBuildOptionFactory.LogLevelOption.GRADLE_PROPERTY): level], new StartParameter()).logLevel == logLevel

        where:
        level       | logLevel
        'quiet'     | LogLevel.QUIET
        'warn'      | LogLevel.WARN
        'LifeCycle' | LogLevel.LIFECYCLE
        'Info'      | LogLevel.INFO
        'DEBUG'     | LogLevel.DEBUG
    }

    def "throws exception for invalid log level"() {
        when:
        converter.convert([(LoggingConfigurationBuildOptionFactory.LogLevelOption.GRADLE_PROPERTY): "fakeLevel"], new StartParameter())

        then:
        def ex = thrown(IllegalArgumentException)
        ex.getMessage().contains(LoggingConfigurationBuildOptionFactory.LogLevelOption.GRADLE_PROPERTY)
        LogLevel.values().each { level ->
            if(level != LogLevel.ERROR) {
                ex.getMessage().contains(level.toString())
            }
        }
    }

}
