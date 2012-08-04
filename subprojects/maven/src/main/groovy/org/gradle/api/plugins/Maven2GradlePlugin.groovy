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



package org.gradle.api.plugins

import org.gradle.api.Experimental
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.bootstrap.ConvertMavenToGradle

/**
 * by Szczepan Faber, created at: 8/1/12
 */
@Experimental
class Maven2GradlePlugin implements Plugin<Project>{
    void apply(Project project) {
        project.task("maven2Gradle", type: ConvertMavenToGradle) {
            group = 'Bootstrap experimental'
            description = '[experimental] Attempts to generate gradle builds from maven project.'

            verbose = false
            keepFile = false
        }
    }
}