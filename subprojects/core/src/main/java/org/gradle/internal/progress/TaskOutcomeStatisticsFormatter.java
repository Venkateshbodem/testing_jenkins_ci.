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
package org.gradle.internal.progress;

import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskOutcomeStatisticsFormatter {
    private AtomicInteger avoidedTasksCount = new AtomicInteger(0);
    private AtomicInteger executedTasksCount = new AtomicInteger(0);

    public String incrementAndGetProgress(final TaskState state) {
        recordTaskOutcome(state);

        final int numAvoidedTasks = avoidedTasksCount.get();
        final int allTasksCount = numAvoidedTasks + executedTasksCount.get();
        if (allTasksCount > 0) {
            final long avoidedPercentage = Math.round(numAvoidedTasks * 100.0 / allTasksCount);
            return " [" + avoidedPercentage + "% AVOIDED, " + (100 - avoidedPercentage) + "% DONE]";
        } else {
            return "";
        }
    }

    private void recordTaskOutcome(final TaskState state) {
        TaskStateInternal stateInternal = (TaskStateInternal) state;

        if (stateInternal.getOutcome() == TaskExecutionOutcome.UP_TO_DATE || stateInternal.getOutcome() == TaskExecutionOutcome.FROM_CACHE) {
            avoidedTasksCount.getAndIncrement();
        } else if (stateInternal.getOutcome() == TaskExecutionOutcome.EXECUTED && stateInternal.isHasActions()) {
            executedTasksCount.getAndIncrement();
        }
    }
}
