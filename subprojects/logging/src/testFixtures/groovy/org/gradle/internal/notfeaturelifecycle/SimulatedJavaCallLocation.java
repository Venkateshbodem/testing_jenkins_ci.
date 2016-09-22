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

package org.gradle.internal.notfeaturelifecycle;

import org.gradle.internal.featurelifecycle.FeatureUsage;
import org.gradle.internal.featurelifecycle.SimulatedDeprecationMessageLogger;

/**
 * Package is notfeaturelifecycle, i.e. anything but featurelifecycle, because of
 * Groovy call stack workaround in FeatureUsage.createStackTrace()
 */
public class SimulatedJavaCallLocation {

    public static FeatureUsage create() {
        return SimulatedDeprecationMessageLogger.nagUserWith(SimulatedDeprecationMessageLogger.DIRECT_CALL);
    }

    public static FeatureUsage indirectly() {
        return SimulatedDeprecationMessageLogger.indirectly(SimulatedDeprecationMessageLogger.INDIRECT_CALL);
    }

    public static FeatureUsage indirectly2() {
        return SimulatedDeprecationMessageLogger.indirectlySecondLevel(SimulatedDeprecationMessageLogger.INDIRECT_CALL_2);
    }
}
