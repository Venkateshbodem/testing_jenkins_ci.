/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal;

import com.google.common.collect.Sets;
import org.gradle.internal.UncheckedException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

public class CompositeModelBuilder<T> extends AbstractLongRunningOperation<CompositeModelBuilder<T>> implements ModelBuilder<Set<T>> {

    private final Class<T> modelType;
    private final Set<GradleParticipantBuild> participants;

    protected CompositeModelBuilder(Class<T> modelType, Set<GradleParticipantBuild> participants) {
        // TODO: When this moves, specialize the ConnectionParams?
        super(null);
        this.modelType = modelType;
        this.participants = participants;
    }

    // TODO: Make all configuration methods configure underlying model builders

    @Override
    protected CompositeModelBuilder<T> getThis() {
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(String... tasks) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO: return getThis();
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(Iterable<String> tasks) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO: return getThis();
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        BlockingResultHandler<T> handler = new BlockingResultHandler();
        get(handler);
        return handler.getResult();
    }

    @Override
    public void get(ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        final Set<T> results = Sets.newConcurrentHashSet();
        final AtomicReference<Throwable> firstFailure = new AtomicReference<Throwable>();
        final ResultHandlerVersion1<Set<T>> adaptedHandler = new HierarchialResultAdapter(new ResultHandlerAdapter(handler));

        final CyclicBarrier barrier = new CyclicBarrier(participants.size(), new Runnable() {
            @Override
            public void run() {
                if (firstFailure.get()==null) {
                    adaptedHandler.onComplete(results);
                } else {
                    adaptedHandler.onFailure(firstFailure.get());
                }
            }
        });

        for (GradleParticipantBuild participant : participants) {
            participant.getConnection().getModel(modelType, new ResultHandler<T>() {
                @Override
                public void onComplete(T result) {
                    results.add(result);
                    waitForFinish();
                }

                private void waitForFinish() {
                    try {
                        barrier.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(GradleConnectionException failure) {
                    firstFailure.compareAndSet(null, failure);
                    waitForFinish();
                }
            });
        }
    }

    private class HierarchialResultAdapter<T> implements ResultHandlerVersion1<Set<T>> {
        private final ResultHandlerVersion1<Set<T>> delegate;

        private HierarchialResultAdapter(ResultHandlerVersion1<Set<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onComplete(Set<T> results) {
            if (HierarchicalElement.class.isAssignableFrom(modelType)) {
                Set<T> fullSet = Sets.newLinkedHashSet();
                Collection<? extends HierarchicalElement> hierarchicalSet =
                    CollectionUtils.checkedCast(HierarchicalElement.class, results);

                for (HierarchicalElement element : hierarchicalSet) {
                    accumulate(element, fullSet);
                }
                delegate.onComplete(fullSet);
            } else {
                delegate.onComplete(results);
            }
        }

        @Override
        public void onFailure(Throwable failure) {
            delegate.onFailure(failure);
        }

        private void accumulate(HierarchicalElement element, Set acc) {
            acc.add(element);
            for (HierarchicalElement child : element.getChildren().getAll()) {
                accumulate(child, acc);
            }
        }
    }
    private class ResultHandlerAdapter<T> extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Set<T>> {
        public ResultHandlerAdapter(ResultHandler<Set<T>> handler) {
            super(handler);
        }

        @Override
        protected String connectionFailureMessage(Throwable failure) {
            // TODO: Supply some composite connection info
            String connectionDisplayName = "composite connection";
            String message = String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connectionDisplayName);
            if (!(failure instanceof UnsupportedMethodException) && failure instanceof UnsupportedOperationException) {
                message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
            }
            return message;
        }
    }

    private class BlockingResultHandler<T> implements ResultHandler<Set<T>> {
        private final BlockingQueue<Object> queue = new ArrayBlockingQueue<Object>(1);

        public Set<T> getResult() {
            Object result;
            try {
                result = queue.take();
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }

            if (result instanceof Throwable) {
                throw UncheckedException.throwAsUncheckedException(attachCallerThreadStackTrace((Throwable) result));
            }
            return (Set<T>)result;
        }

        private Throwable attachCallerThreadStackTrace(Throwable failure) {
            List<StackTraceElement> adjusted = new ArrayList<StackTraceElement>();
            adjusted.addAll(Arrays.asList(failure.getStackTrace()));
            List<StackTraceElement> currentThreadStack = Arrays.asList(Thread.currentThread().getStackTrace());
            if (!currentThreadStack.isEmpty()) {
                adjusted.addAll(currentThreadStack.subList(2, currentThreadStack.size()));
            }
            failure.setStackTrace(adjusted.toArray(new StackTraceElement[0]));
            return failure;
        }

        public void onComplete(Set<T> result) {
            queue.add(result);
        }

        public void onFailure(GradleConnectionException failure) {
            queue.add(failure);
        }
    }
}
