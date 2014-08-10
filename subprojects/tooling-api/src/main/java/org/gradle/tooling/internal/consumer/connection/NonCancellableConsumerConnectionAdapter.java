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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonCancellableConsumerConnectionAdapter implements ConsumerConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonCancellableConsumerConnectionAdapter.class);

    private final ConsumerConnection delegate;

    public NonCancellableConsumerConnectionAdapter(ConsumerConnection delegate) {
        this.delegate = delegate;
    }

    public void stop() {
        delegate.stop();
    }

    public String getDisplayName() {
        return delegate.getDisplayName() + " (non-cancellable)";
    }

    public <T> T run(BuildAction<T> action, CancellationToken cancellationToken, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        Runnable callback = handleCancellationPreOperation(cancellationToken);
        try {
            return delegate.run(action, cancellationToken, operationParameters);
        } finally {
            handleCancellationPostOperation(cancellationToken, callback);
        }
    }

    public <T> T run(Class<T> type, CancellationToken cancellationToken, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        Runnable callback = handleCancellationPreOperation(cancellationToken);
        try {
            return delegate.run(type, cancellationToken, operationParameters);
        } finally {
            handleCancellationPostOperation(cancellationToken, callback);
        }
    }

    private Runnable handleCancellationPreOperation(CancellationToken cancellationToken) {
        Runnable callback = new Runnable() {
            public void run() {
                LOGGER.info("Note: Version of Gradle provider does not support cancellation. Upgrade your Gradle build.");
            }
        };
        cancellationToken.addCallback(callback);
        return callback;
    }

    private void handleCancellationPostOperation(CancellationToken cancellationToken, Runnable callback) {
        cancellationToken.removeCallback(callback);
    }
}
