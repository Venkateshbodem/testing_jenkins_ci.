/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.plugins.quality;

import org.gradle.api.reporting.ReportContainer;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.Internal;

/**
 * The reporting configuration for the {@link Pmd} task.
 */
public interface PmdReports extends ReportContainer<SingleFileReport> {

    /**
     * The pmd (single file) HTML report
     *
     * @return The pmd (single file) HTML report
     */
    @Internal
    SingleFileReport getHtml();

    /**
     * The pmd (single file) XML report
     *
     * @return The pmd (single file) XML report
     */
    @Internal
    SingleFileReport getXml();

    /**
     * The pmd (single file) CSV report
     *
     * @return The pmd (single file) CSV report
     */
    @Internal
    SingleFileReport getCsv();

    /**
     * The pmd (single file) Code Climate JSON report
     *
     * @return The pmd (single file) Code Climate JSON report
     */
    @Internal
    SingleFileReport getCodeClimate();

    /**
     * The pmd (single file) sarif JSON report
     *
     * @return The pmd (single file) sarif JSON report
     */
    @Internal
    SingleFileReport getSarif();
}
