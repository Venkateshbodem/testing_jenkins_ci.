/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.fingerprint

import org.gradle.api.Describable
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.hash.HashCode
import java.io.File


internal
typealias InvalidationReason = String


internal
class InstantExecutionFingerprintChecker(private val host: Host) {

    interface Host {
        fun hashCodeOf(inputFile: File): HashCode?
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
    }

    object FingerprintEncoder {
        suspend fun WriteContext.encode(fingerprint: InstantExecutionCacheFingerprint) {
            fingerprint.run {
                writeCollection(inputFiles)
                writeCollection(obtainedValues)
            }
        }
    }

    suspend fun ReadContext.checkFingerprint(): InvalidationReason? =
        checkFingerprintOfInputFiles() ?: checkFingerprintOfObtainedValues()

    private
    suspend fun ReadContext.checkFingerprintOfInputFiles(): InvalidationReason? {
        readCollection {
            val (inputFile, hashCode) = readNonNull<InstantExecutionCacheFingerprint.InputFile>()
            if (host.hashCodeOf(inputFile) != hashCode) {
                // TODO: log some debug info
                return "a configuration file has changed"
            }
        }
        return null
    }

    private
    suspend fun ReadContext.checkFingerprintOfObtainedValues(): InvalidationReason? {
        readCollection {
            val obtainedValue = readNonNull<ObtainedValue>()
            checkFingerprintValueIsUpToDate(obtainedValue)?.let { reason ->
                return reason
            }
        }
        return null
    }

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? = obtainedValue.run {
        when (valueSourceType) {
            SystemPropertyValueSource::class.java -> {
                // Special case system properties to get them from the host because
                // this check happens too early in the process, before the system properties
                // passed in the command line have been propagated.
                val propertyName = valueSourceParameters
                    .uncheckedCast<SystemPropertyValueSource.Parameters>()
                    .propertyName
                    .get()
                if (value.get() != System.getProperty(propertyName)) {
                    "system property '$propertyName' has changed"
                } else {
                    null
                }
            }
            else -> {
                val valueSource = host.instantiateValueSourceOf(this)
                if (value.get() != valueSource.obtain()) {
                    (valueSource as? Describable)?.let {
                        it.displayName + " has changed"
                    } ?: "a build logic input has changed"
                } else {
                    null
                }
            }
        }
    }
}
