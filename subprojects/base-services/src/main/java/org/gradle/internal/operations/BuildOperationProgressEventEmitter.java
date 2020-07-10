/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;

/**
 * Specialised event emitter for cross cutting type progress events not tied more deeply to operation execution.
 */
@ServiceScope(Scopes.Global)
public class BuildOperationProgressEventEmitter {

    private final Clock clock;
    private final CurrentBuildOperationRef current;
    private final BuildOperationListener listener;

    public BuildOperationProgressEventEmitter(Clock clock, CurrentBuildOperationRef current, BuildOperationListener listener) {
        this.clock = clock;
        this.current = current;
        this.listener = listener;
    }

    public void emit(OperationIdentifier operationIdentifier, long timestamp, @Nullable Object details) {
        // Explicit check in case of unsafe CurrentBuildOperationRef usage
        if (operationIdentifier == null) {
            throw new IllegalArgumentException("operationIdentifier is null");
        }
        listener.progress(operationIdentifier, new OperationProgressEvent(timestamp, details));
    }

    public void emitNowIfCurrent(Object details) {
        emitIfCurrent(clock.getCurrentTime(), details);
    }

    public void emitIfCurrent(long time, Object details) {
        OperationIdentifier currentOperationIdentifier = current.getId();
        if (currentOperationIdentifier != null) {
            emit(currentOperationIdentifier, time, details);
        }
    }

    public void emitNowForCurrent(Object details) {
        emitForCurrent(clock.getCurrentTime(), details);
    }

    private void emitForCurrent(long time, Object details) {
        OperationIdentifier currentOperationIdentifier = current.getId();
        if (currentOperationIdentifier == null) {
            throw new IllegalStateException("No current build operation");
        } else {
            emit(currentOperationIdentifier, time, details);
        }
    }
}
