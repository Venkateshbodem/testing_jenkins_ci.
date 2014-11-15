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

package org.gradle.play.internal.run;

import org.gradle.api.Action;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.process.internal.WorkerProcessContext;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class PlayWorkerServer implements Action<WorkerProcessContext>, PlayRunWorkerServerProtocol, Serializable {

    private VersionedPlayRunSpec spec;
    private final Iterable<File> docsClasspath;

    private volatile CountDownLatch stop;

    public PlayWorkerServer(VersionedPlayRunSpec spec, Iterable<File> docsClasspath) {
        this.spec = spec;
        this.docsClasspath = docsClasspath;
    }

    public void execute(WorkerProcessContext context) {
        stop = new CountDownLatch(1);
        final PlayRunWorkerClientProtocol clientProtocol = context.getServerConnection().addOutgoing(PlayRunWorkerClientProtocol.class);
        context.getServerConnection().addIncoming(PlayRunWorkerServerProtocol.class, this);
        context.getServerConnection().connect();
        final PlayAppLifecycleUpdate result = execute();
        try {
            stop.await();
            clientProtocol.executed(result);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public PlayAppLifecycleUpdate execute() {
        try {
            PlayExecuter playExcutor = new PlayExecuter();
            playExcutor.run(spec, docsClasspath);
            return new PlayAppLifecycleUpdate(true);
        } catch (Exception e) {
            Logging.getLogger(this.getClass()).error("Failed to run Play", e);
            return new PlayAppLifecycleUpdate(e);
        }
    }

    public void stop() {
        stop.countDown();
    }
}
