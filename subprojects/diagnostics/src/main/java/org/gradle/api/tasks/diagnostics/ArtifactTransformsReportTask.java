/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Incubating;
import org.gradle.work.DisableCachingByDefault;

/**
 * A task which reports all the Artifact Transforms of a project.
 *
 * This is useful for investigating ambiguous transformation scenarios.  The output can help predict which ATs will need
 * to be modified to remove ambiguity.
 *
 * @since 8.12
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output by examining configurations at execution time")
public abstract class ArtifactTransformsReportTask extends AbstractArtifactTransformReportTask {
    /**
     * Create new instance of the task.
     * @since 8.12
     */
     public ArtifactTransformsReportTask() {}
}
