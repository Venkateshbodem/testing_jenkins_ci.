/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.util.Clock;

public class BlockingTriggerable implements TriggerListener {
    private final static long WAIT_TIMEOUT_MS = 30000;
    private final static long WAIT_INTERVAL_MS = WAIT_TIMEOUT_MS / 100;
    private final static Logger LOGGER = Logging.getLogger(BlockingTriggerable.class);
    private final Object lock;
    private TriggerDetails currentTrigger;

    public BlockingTriggerable() {
        this.lock = new Object();
        this.currentTrigger = null;
    }

    /**
     * Blocks until triggered() is called with a reason.
     */
    public TriggerDetails waitForTrigger() {
        try {
            synchronized (lock) {
                LOGGER.debug("Waiting for a trigger");
                Clock waitStart = new Clock();
                while (currentTrigger == null) {
                    lock.wait(WAIT_INTERVAL_MS);
                    assert waitStart.getTimeInMs() < WAIT_TIMEOUT_MS : "run-away test for " + waitStart.getTime();
                }
                LOGGER.debug("woke up for trigger " + currentTrigger);
                TriggerDetails lastReason = currentTrigger;
                currentTrigger = null;
                return lastReason;
            }
        } catch (InterruptedException e) {
            UncheckedException.throwAsUncheckedException(e);
            return null; // unreachable
        }
    }

    @Override
    public void triggered(TriggerDetails triggerInfo) {
        synchronized (lock) {
            LOGGER.debug("triggered with " + triggerInfo);
            currentTrigger = triggerInfo;
            lock.notify();
        }
    }
}
