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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;

public class DefaultCompositeBuildContext implements CompositeBuildContext {
    private final Multimap<ModuleIdentifier, String> replacementProjects = ArrayListMultimap.create();
    private final Map<String, LocalComponentMetaData> projectMetadata = Maps.newHashMap();
    private final Map<String, File> projectDirectories = Maps.newHashMap();

    @Override
    public String getReplacementProject(ModuleComponentSelector selector) {
        ModuleIdentifier candidateId = DefaultModuleIdentifier.newId(selector.getGroup(), selector.getModule());
        Collection<String> providingProjects = replacementProjects.get(candidateId);
        if (providingProjects.isEmpty()) {
            return null;
        }
        if (providingProjects.size() == 1) {
            return providingProjects.iterator().next();
        }
        SortedSet<String> sortedProjects = Sets.newTreeSet(providingProjects);
        String failureMessage = String.format("Module version '%s' is not unique in composite: can be provided by projects %s.", selector.getDisplayName(), sortedProjects);
        throw new ModuleVersionResolveException(selector, failureMessage);
    }

    @Override
    public LocalComponentMetaData getProject(String projectPath) {
        LocalComponentMetaData localComponentMetaData = projectMetadata.get(projectPath);
        checkNotNull(projectPath, localComponentMetaData);
        return localComponentMetaData;
    }

    @Override
    public File getProjectDirectory(String projectPath) {
        File file = projectDirectories.get(projectPath);
        checkNotNull(projectPath, file);
        return file;
    }

    public void checkNotNull(String projectPath, Object requested) {
        // This should not happen, but is a failsafe to prevent NPE's in the future.
        if (requested == null) {
            throw new IllegalStateException(String.format("Requested project path %s which was never registered", projectPath));
        }
    }

    @Override
    public void register(ModuleIdentifier moduleId, String projectPath, LocalComponentMetaData localComponentMetaData, File projectDirectory) {
        replacementProjects.put(moduleId, projectPath);
        if (projectMetadata.containsKey(projectPath)) {
            String failureMessage = String.format("Project path '%s' is not unique in composite.", projectPath);
            throw new ReportedException(new GradleException(failureMessage));
        }
        projectMetadata.put(projectPath, localComponentMetaData);
        projectDirectories.put(projectPath, projectDirectory);
    }
}
