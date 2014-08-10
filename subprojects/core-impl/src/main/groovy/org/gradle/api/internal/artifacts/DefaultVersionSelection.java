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

package org.gradle.api.internal.artifacts;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;

public class DefaultVersionSelection implements VersionSelectionInternal {
    ModuleComponentSelector requested;
    ModuleComponentIdentifier candidate;
    State state = State.NOT_SET;

    public DefaultVersionSelection(ModuleComponentSelector requested, ModuleComponentIdentifier candidate) {
        this.requested = requested;
        this.candidate = candidate;
    }

    public ModuleComponentSelector getRequested() {
        return requested;
    }

    public ModuleComponentIdentifier getCandidate() {
        return candidate;
    }

    public void accept() {
        switch(this.state) {
            case NOT_SET:
            case ACCEPTED:
                this.state = State.ACCEPTED;
                break;
            default:
                throw stateChangeFailure();
        }
    }

    public void reject() {
        switch(this.state) {
            case NOT_SET:
            case REJECTED:
                this.state = State.REJECTED;
                break;
            default:
                throw stateChangeFailure();
        }
    }

    public State getState() {
        return state;
    }

    private RuntimeException stateChangeFailure() {
        return new InvalidUserCodeException("Once a version selection has been accepted or rejected, it cannot be changed.");
    }
}
