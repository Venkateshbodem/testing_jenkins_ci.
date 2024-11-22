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

package org.gradle.api.tasks.diagnostics.internal.artifacttransforms.model;

import java.util.List;

/**
 * Lightweight, immutable model of all the data in a project necessary for Artifact Transform reporting.
 *
 * The intended use is that this data model can be populated with the complete information of a project prior to any
 * report logic running.  This enables the reporting logic to remain completely independent of the actual project classes.
 */
public final class ArtifactTransformReportModel {
    private final String projectName;
    private final List<ReportArtifactTransform> transforms;

    ArtifactTransformReportModel(String projectName, List<ReportArtifactTransform> transforms) {
        this.projectName = projectName;
        this.transforms = transforms;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<ReportArtifactTransform> getTransforms() {
        return transforms;
    }
}
