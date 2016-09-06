/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util

import org.gradle.internal.Factory
import org.gradle.internal.featurelifecycle.CollectingLocationReporter
import org.gradle.internal.notfeaturelifecycle.GroovySingleMessageSource
import org.gradle.internal.notfeaturelifecycle.JavaSingleMessageSource
import org.gradle.internal.notfeaturelifecycle.NaggingSource
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Subject
import spock.lang.Unroll

@Subject(SingleMessageLogger)
class SingleMessageLoggerTest extends ConcurrentSpec {
    def reporter = new CollectingLocationReporter()

    def setup() {
        SingleMessageLogger.useLocationReporter(reporter)
    }

    def cleanup() {
        SingleMessageLogger.reset()
    }

    def 'nagUserWith nags more than once'() {
        when:
        SingleMessageLogger.nagUserWith('feature1')
        SingleMessageLogger.nagUserWith('feature2')
        SingleMessageLogger.nagUserWith('feature2')

        then:
        def usages = reporter.usages
        usages.size() == 3

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
        usages[2].message == 'feature2'
    }

    def 'nagUserOnceWith nags only once'() {
        when:
        SingleMessageLogger.nagUserOnceWith('feature1')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.nagUserOnceWith('feature2')

        then:
        def usages = reporter.usages
        usages.size() == 2

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
    }

    def 'nagUserOnceWith nags only once, unless reset is called.'() {
        when:
        SingleMessageLogger.nagUserOnceWith('feature1')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.reset()
        SingleMessageLogger.nagUserOnceWith('feature1')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.reset()
        SingleMessageLogger.nagUserOnceWith('feature1')
        SingleMessageLogger.nagUserOnceWith('feature2')
        SingleMessageLogger.nagUserOnceWith('feature2')

        then:
        def usages = reporter.usages
        println usages
        usages.size() == 6

        and:
        usages[0].message == 'feature1'
        usages[1].message == 'feature2'
        usages[2].message == 'feature1'
        usages[3].message == 'feature2'
        usages[4].message == 'feature1'
        usages[5].message == 'feature2'
    }

    def 'does not nag from factory while disabled'() {
        given:
        Factory<String> factory = Mock(Factory)

        when:
        def result = SingleMessageLogger.whileDisabled(factory)

        then:
        result == 'result'

        and:
        1 * factory.create() >> {
            SingleMessageLogger.nagUserWith('nag')
            return 'result'
        }
        0 * _

        then:
        reporter.usages.isEmpty()
    }

    def 'does not nag from action while disabled'() {
        given:
        def action = Mock(Runnable)

        when:
        SingleMessageLogger.whileDisabled(action)

        then:
        _ * action.run() >> {
            SingleMessageLogger.nagUserWith('nag')
        }
        0 * _

        then:
        reporter.usages.isEmpty()
    }

    def 'deprecation message has next major version'() {
        given:
        def major = GradleVersion.current().nextMajor

        when:
        SingleMessageLogger.nagUserOfDeprecated('foo', 'bar')

        then:
        def usages = reporter.usages

        usages.size() == 1
        usages[0].message == "foo has been deprecated and is scheduled to be removed in Gradle ${major.version}. bar" as String
        println usages[0].stack[0]
    }

    def 'warnings are disabled for the current thread only'() {
        when:
        async {
            start {
                thread.blockUntil.disabled
                SingleMessageLogger.nagUserWith('nag')
                instant.logged
            }
            start {
                SingleMessageLogger.whileDisabled {
                    instant.disabled
                    SingleMessageLogger.nagUserWith('ignored')
                    thread.blockUntil.logged
                }
            }
        }

        then:
        def usages = reporter.usages

        usages.size() == 1
        usages[0].message == 'nag'
    }

    @Unroll
    def 'Example #simpleSourceClassName results in expected call stack.'() {
        when:
        source.execute()

        then:
        def usages = reporter.usages

        usages.size() == 3
        usages[0].message == NaggingSource.DIRECT_CALL
        usages[1].message == NaggingSource.INDIRECT_CALL
        usages[2].message == NaggingSource.INDIRECT_CALL_2

        and:
        usages[0].stack[0].className == sourceClassName
        usages[1].stack[0].className == sourceClassName
        usages[2].stack[0].className == sourceClassName

        and:
        usages[0].stack[0].methodName == 'create'

        usages[1].stack[0].methodName == 'create'
        usages[1].stack[1].methodName == 'indirectly'

        usages[2].stack[0].methodName == 'create'
        usages[2].stack[1].methodName == 'indirectly'
        usages[2].stack[2].methodName == 'indirectly2'

        where:
        source << [new JavaSingleMessageSource(), new GroovySingleMessageSource()]
        sourceClassName = source.class.name
        simpleSourceClassName = source.class.simpleName
    }
}
