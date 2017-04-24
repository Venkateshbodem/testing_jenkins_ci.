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

package org.gradle.internal.logging.progress;

import org.gradle.internal.logging.events.OperationIdentifier;

public class OperationIdentifierRegistry {
    private static ThreadLocal<OperationIdentifier> operationIdThreadLocal = new ThreadLocal<OperationIdentifier>();
    private static volatile OperationIdentifier parentOperationId;

    public static OperationIdentifier getCurrentOperationId() {
        OperationIdentifier operationId = operationIdThreadLocal.get();
        if (operationId == null) {
            operationId = parentOperationId;
        }
        return operationId;
    }

    public static void setCurrentOperationId(OperationIdentifier operationId) {
        if (operationId == null) {
            operationIdThreadLocal.remove();
        } else {
            operationIdThreadLocal.set(operationId);
        }
    }

    public static void setParentOperationId(OperationIdentifier operationId) {
        parentOperationId = operationId;
    }
}
