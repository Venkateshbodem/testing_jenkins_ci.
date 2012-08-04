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

package org.gradle.execution.taskgraph;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.changedetection.TaskArtifactStateCacheAccess;
import org.gradle.api.specs.Spec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ParallelTaskExecutor extends DefaultTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParallelTaskExecutor.class);
    private static final int EXECUTOR_COUNT = 2;

    private final List<Thread> executorThreads = new ArrayList<Thread>();
    private final TaskArtifactStateCacheAccess stateCacheAccess;

    public ParallelTaskExecutor(TaskArtifactStateCacheAccess cacheAccess) {
        this.stateCacheAccess = cacheAccess;
    }

    public void process(final TaskExecutionPlan taskExecutionPlan, final TaskExecutionListener taskListener) {
        stateCacheAccess.longRunningOperation("Executing all tasks", new Runnable() {
            public void run() {
                doProcess(taskExecutionPlan, taskListener);
                // TODO This needs to wait until all tasks have been executed, not just started....
                taskExecutionPlan.awaitCompletion();
            }
        });
    }

    private void doProcess(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
        List<Project> projects = getAllProjects(taskExecutionPlan);
        int numExecutors = Math.min(EXECUTOR_COUNT, projects.size());

        for (int i = 0; i < numExecutors; i++) {
            TaskExecutorWorker worker = new TaskExecutorWorker(taskExecutionPlan, taskListener);

            for (int j = i; j < projects.size(); j += numExecutors) {
                worker.addProject(projects.get(j));
            }

            executorThreads.add(new Thread(worker));
        }

        for (Thread executorThread : executorThreads) {
            // TODO A bunch more stuff to contextualise the thread
            executorThread.start();
        }
    }

    private List<Project> getAllProjects(TaskExecutionPlan taskExecutionPlan) {
        final Set<Project> uniqueProjects = new LinkedHashSet<Project>();
        for (Task task : taskExecutionPlan.getTasks()) {
            uniqueProjects.add(task.getProject());
        }
        return new ArrayList<Project>(uniqueProjects);
    }

    private class TaskExecutorWorker implements Runnable {
        private final TaskExecutionPlan taskExecutionPlan;
        private final TaskExecutionListener taskListener;

        private final List<Project> projects = new ArrayList<Project>();

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, TaskExecutionListener taskListener) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskListener = taskListener;
        }

        public void run() {
            TaskInfo taskInfo;
            while ((taskInfo = taskExecutionPlan.getTaskToExecute(getTaskSpec())) != null) {
                LOGGER.warn("Got task to execute: " + taskInfo.getTask().getPath());
                executeTaskWithCacheLock(taskInfo);
                LOGGER.warn("Executed: " + taskInfo.getTask().getPath());
            }
        }

        private void executeTaskWithCacheLock(final TaskInfo taskInfo) {
            final String taskPath = taskInfo.getTask().getPath();
            LOGGER.warn(taskPath + " (" + Thread.currentThread() + " - start");
            stateCacheAccess.useCache("Executing " + taskPath, new Runnable() {
                public void run() {
                    LOGGER.warn(taskPath + " (" + Thread.currentThread() + ") - have cache: executing");
                    executeTask(taskInfo, taskExecutionPlan, taskListener);
                    LOGGER.warn(taskPath + " (" + Thread.currentThread() + ") - execute done: releasing cache");
                }
            });
            LOGGER.warn(taskPath + " (" + Thread.currentThread() + ") - complete");
        }

        public void addProject(Project project) {
            projects.add(project);
        }

        private Spec<TaskInfo> getTaskSpec() {
            return new Spec<TaskInfo>() {
                public boolean isSatisfiedBy(TaskInfo element) {
                    return projects.contains(element.getTask().getProject());
                }
            };
        }
    }
}
