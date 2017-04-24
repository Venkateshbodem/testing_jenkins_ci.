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

package org.gradle.internal.logging.serializer;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class LogEventSerializer implements Serializer<LogEvent> {
    private final Serializer<Throwable> throwableSerializer;
    private final Serializer<LogLevel> logLevelSerializer;
    private final Serializer<OperationIdentifier> operationIdSerializer;

    // FIXME(daniel): The coverage for this class and StyledTextOutputEventSerializer is over in the unit test for DaemonMessageSerializer and didn't move when the serializer code did.
    //      Adam recommends adding unit test for this stuff
    public LogEventSerializer(Serializer<LogLevel> logLevelSerializer, Serializer<Throwable> throwableSerializer, Serializer<OperationIdentifier> operationIdSerializer) {
        this.logLevelSerializer = logLevelSerializer;
        this.throwableSerializer = throwableSerializer;
        this.operationIdSerializer = operationIdSerializer;
    }

    @Override
    public void write(Encoder encoder, LogEvent event) throws Exception {
        // FIXME(ADAM): The coverage is over in the unit test for DaemonMessageSerializer and for some reason didn't move when the serializer code did.
        // TODO(ew): Write test coverage in DaemonMessageSerializer and consider moving
        encoder.writeLong(event.getTimestamp());
        encoder.writeString(event.getCategory());
        logLevelSerializer.write(encoder, event.getLogLevel());
        operationIdSerializer.write(encoder, event.getOperationId());
        encoder.writeString(event.getMessage());
        throwableSerializer.write(encoder, event.getThrowable());
    }

    @Override
    public LogEvent read(Decoder decoder) throws Exception {
        long timestamp = decoder.readLong();
        String category = decoder.readString();
        LogLevel logLevel = logLevelSerializer.read(decoder);
        OperationIdentifier operationId = operationIdSerializer.read(decoder);
        String message = decoder.readString();
        Throwable throwable = throwableSerializer.read(decoder);
        return new LogEvent(timestamp, category, logLevel, operationId, message, throwable);
    }
}
