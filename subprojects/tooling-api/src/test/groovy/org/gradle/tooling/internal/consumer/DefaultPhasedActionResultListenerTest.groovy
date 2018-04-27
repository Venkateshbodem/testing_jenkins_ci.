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

package org.gradle.tooling.internal.consumer

import org.gradle.testing.internal.util.Specification
import org.gradle.tooling.PhasedResultHandler
import org.gradle.tooling.internal.protocol.PhasedActionResult

class DefaultPhasedActionResultListenerTest extends Specification {

    def projectsLoadedHandler = Mock(PhasedResultHandler)
    def projectsEvaluatedHandler = Mock(PhasedResultHandler)
    def buildFinishedHandler = Mock(PhasedResultHandler)
    def listener = new DefaultPhasedActionResultListener(projectsLoadedHandler, projectsEvaluatedHandler, buildFinishedHandler)

    def "right handler is called"() {
        when:
        listener.onResult(new ProjectsLoadedResult<String>('result1'))
        listener.onResult(new ProjectsEvaluatedResult<String>('result2'))
        listener.onResult(new BuildFinishedResult<String>('result3'))

        then:
        1 * projectsLoadedHandler.onComplete('result1')
        1 * projectsEvaluatedHandler.onComplete('result2')
        1 * buildFinishedHandler.onComplete('result3')
    }

    def "null handler is not called"() {
        def emptyListener = new DefaultPhasedActionResultListener(null, null, null)

        when:
        emptyListener.onResult(new ProjectsLoadedResult<String>('result1'))
        emptyListener.onResult(new ProjectsEvaluatedResult<String>('result2'))
        emptyListener.onResult(new BuildFinishedResult<String>('result3'))

        then:
        noExceptionThrown()
    }

    abstract class Result<T> implements PhasedActionResult<T> {
        T result
        PhasedActionResult.Phase phase

        Result(T result, PhasedActionResult.Phase phase) {
            this.result = result
            this.phase = phase
        }

        @Override
        T getResult() {
            return result
        }

        @Override
        PhasedActionResult.Phase getPhase() {
            return phase
        }
    }

    class ProjectsLoadedResult<T> extends Result<T> {
        ProjectsLoadedResult(T result) {
            super(result, PhasedActionResult.Phase.PROJECTS_LOADED)
        }
    }

    class ProjectsEvaluatedResult<T> extends Result<T> {
        ProjectsEvaluatedResult(T result) {
            super(result, PhasedActionResult.Phase.PROJECTS_EVALUATED)
        }
    }

    class BuildFinishedResult<T> extends Result<T> {
        BuildFinishedResult(T result) {
            super(result, PhasedActionResult.Phase.BUILD_FINISHED)
        }
    }
}
