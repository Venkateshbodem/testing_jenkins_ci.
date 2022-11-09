/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.daemon;

import org.gradle.workers.internal.BuildOperationAwareWorker;
import org.gradle.workers.internal.WorkerFactory;
import org.gradle.workers.internal.WorkerRequirement;

abstract public class AbstractDelegatingDaemonCompilerWorkerFactory implements DaemonCompilerWorkerFactory {
    private final WorkerFactory delegate;

    public AbstractDelegatingDaemonCompilerWorkerFactory(WorkerFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public BuildOperationAwareWorker getWorker(WorkerRequirement workerRequirement) {
        return delegate.getWorker(workerRequirement);
    }
}
