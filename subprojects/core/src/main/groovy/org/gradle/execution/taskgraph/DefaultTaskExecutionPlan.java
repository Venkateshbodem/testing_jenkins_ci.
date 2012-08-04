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

import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.internal.UncheckedException;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A reusable implementation of TaskExecutionPlan. The {@link #addToTaskGraph(java.util.Collection)} and {@link #clear()} methods are NOT threadsafe, and callers must synchronize
 * access to these methods.
 */
public class DefaultTaskExecutionPlan implements TaskExecutionPlan {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final LinkedHashMap<Task, TaskInfo> executionPlan = new LinkedHashMap<Task, TaskInfo>();
    private Spec<? super Task> filter = Specs.satisfyAll();
    private Exception failure;

    private TaskFailureHandler failureHandler = new RethrowingFailureHandler();

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        List<Task> queue = new ArrayList<Task>(tasks);
        Collections.sort(queue);

        Set<Task> visiting = new HashSet<Task>();
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            Task task = queue.get(0);
            if (!filter.isSatisfiedBy(task)) {
                // Filtered - skip
                queue.remove(0);
                continue;
            }
            if (executionPlan.containsKey(task)) {
                // Already in plan - skip
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                Set<Task> dependsOnTasks = new TreeSet<Task>(Collections.reverseOrder());
                dependsOnTasks.addAll(context.getDependencies(task));
                for (Task dependsOnTask : dependsOnTasks) {
                    if (visiting.contains(dependsOnTask)) {
                        throw new CircularReferenceException(String.format(
                                "Circular dependency between tasks. Cycle includes [%s, %s].", task, dependsOnTask));
                    }
                    queue.add(0, dependsOnTask);
                }
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                queue.remove(0);
                visiting.remove(task);
                Set<TaskInfo> dependencies = new HashSet<TaskInfo>();
                for (Task dependency : context.getDependencies(task)) {
                    TaskInfo dependencyInfo = executionPlan.get(dependency);
                    if (dependencyInfo != null) {
                        dependencies.add(dependencyInfo);
                    }
                    // else - the dependency has been filtered, so ignore it
                }
                executionPlan.put(task, new TaskInfo((TaskInternal) task, dependencies));
            }
        }
    }

    public void clear() {
        executionPlan.clear();
    }

    public List<Task> getTasks() {
        return new ArrayList<Task>(executionPlan.keySet());
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        this.failureHandler = handler;
    }

    public TaskInfo getTaskToExecute(Spec<TaskInfo> criteria) {
        lock.lock();
        try {

            TaskInfo nextMatching;
            while ((nextMatching = getNextReadyAndMatching(criteria)) != null) {
                while (!nextMatching.dependenciesExecuted()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                nextMatching.startExecution();

                if (nextMatching.dependenciesFailed()) {
                    abortTask(nextMatching);
                } else {
                    return nextMatching;
                }
            }

            return null;

        } finally {
            lock.unlock();
        }

    }

    private TaskInfo getNextReadyAndMatching(Spec<TaskInfo> criteria) {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (taskInfo.isReady() && criteria.isSatisfiedBy(taskInfo)) {
                return taskInfo;
            }
        }
        return null;
    }

    private void abortTask(TaskInfo taskInfo) {
        taskInfo.executionFailed();
        condition.signalAll();
    }

    public void taskComplete(TaskInfo task) {
        lock.lock();
        try {
            task.executionSucceeded();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void taskFailed(TaskInfo taskInfo) {
        lock.lock();
        try {
            taskInfo.executionFailed();
            try {
                failureHandler.onTaskFailure(taskInfo.getTask());
            } catch (Exception e) {
                abortExecution(e);
            }
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void abortExecution(Exception e) {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (taskInfo.isReady()) {
                taskInfo.startExecution();
                taskInfo.executionFailed();
            }
        }
        this.failure = e;
    }

    public void awaitCompletion() {
        lock.lock();
        try {
            while (!allTasksComplete()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (failure != null) {
                UncheckedException.throwAsUncheckedException(failure);
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean allTasksComplete() {
        for (TaskInfo taskInfo : executionPlan.values()) {
            if (!taskInfo.isComplete()) {
                return false;
            }
        }
        return true;
    }

    private static class RethrowingFailureHandler implements TaskFailureHandler {
        public void onTaskFailure(Task task) {
            task.getState().rethrowFailure();
        }
    }
}
