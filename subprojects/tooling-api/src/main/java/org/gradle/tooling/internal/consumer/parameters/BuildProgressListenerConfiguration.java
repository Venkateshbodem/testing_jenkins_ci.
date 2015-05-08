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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.tooling.events.build.BuildProgressListener;
import org.gradle.tooling.events.task.TaskProgressListener;
import org.gradle.tooling.events.test.TestProgressListener;

import java.util.List;

public class BuildProgressListenerConfiguration {

    private final List<TestProgressListener> testListeners;
    private final List<TaskProgressListener> taskListeners;
    private final List<BuildProgressListener> buildListeners;

    public BuildProgressListenerConfiguration(
        List<TestProgressListener> testListeners,
        List<TaskProgressListener> taskListeners,
        List<BuildProgressListener> buildListeners) {
        this.testListeners = testListeners;
        this.taskListeners = taskListeners;
        this.buildListeners = buildListeners;
    }

    public List<TestProgressListener> getTestListeners() {
        return testListeners;
    }

    public List<TaskProgressListener> getTaskListeners() {
        return taskListeners;
    }

    public List<BuildProgressListener> getBuildListeners() {
        return buildListeners;
    }

}
