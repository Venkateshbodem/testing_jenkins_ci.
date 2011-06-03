/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.api.XmlGeneratorTask
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath

/**
 * Generates an Eclipse <code>.classpath</code> file. If you want to fine tune the eclipse configuration
 * <p>
 * Please refer to interesting examples on eclipse configuration in {@link EclipseClasspath}.
 *
 * @author Hans Dockter
 */
class GenerateEclipseClasspath extends XmlGeneratorTask<Classpath> {

    EclipseClasspath classpath

    GenerateEclipseClasspath() {
        xmlTransformer.indentation = "\t"
    }

    @Override protected Classpath create() {
        return new Classpath(xmlTransformer)
    }

    @Override protected void configure(Classpath xmlClasspath) {
        classpath.mergeXmlClasspath(xmlClasspath)
    }

    /**
     * The source sets to be added to the classpath.
     */
    Iterable<SourceSet> getSourceSets() {
        classpath.sourceSets
    }

    /**
     * The source sets to be added to the classpath.
     */
    void setSourceSets(Iterable<SourceSet> sourceSets) {
        classpath.sourceSets = sourceSets
    }

    /**
     * The configurations which files are to be transformed into classpath entries.
     */
    Collection<Configuration> getPlusConfigurations() {
        classpath.plusConfigurations
    }

    void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
        classpath.plusConfigurations = plusConfigurations
    }

    /**
     * The configurations which files are to be excluded from the classpath entries.
     */
    Collection<Configuration> getMinusConfigurations() {
        classpath.minusConfigurations
    }

    void setMinusConfigurations(Collection<Configuration> minusConfigurations) {
        classpath.minusConfigurations = minusConfigurations
    }

    /**
     * Adds path variables to be used for replacing absolute paths in classpath entries.
     *
     * @param pathVariables A map with String->File pairs.
     */
    Map<String, File> getVariables() {
        classpath.pathVariables
    }

    void setVariables(Map<String, File> variables) {
        classpath.pathVariables = variables
    }

    /**
     * Containers to be added to the classpath
     */
    Set<String> getContainers() {
        classpath.containers
    }

    void setContainers(Set<String> containers) {
        classpath.containers = containers
    }

    /**
     * The default output directory for eclipse generated files, eg classes.
     */
    File getDefaultOutputDir() {
        classpath.defaultOutputDir
    }

    void setDefaultOutputDir(File defaultOutputDir) {
        classpath.defaultOutputDir = defaultOutputDir
    }

    /**
     * Whether to download and add sources associated with the dependency jars. Defaults to true.
     */
    boolean getDownloadSources() {
        classpath.downloadSources
    }

    void setDownloadSources(boolean downloadSources) {
        classpath.downloadSources = downloadSources
    }

    /**
     * Whether to download and add javadocs associated with the dependency jars. Defaults to false.
     */
    boolean getDownloadJavadoc() {
        classpath.downloadJavadoc
    }

    void setDownloadJavadoc(boolean downloadJavadoc) {
        classpath.downloadJavadoc = downloadJavadoc
    }

    /**
     * Adds containers to the .classpath.
     *
     * @param containers the container names to be added to the .classpath.
     */
    void containers(String... containers) {
        classpath.containers(containers)
    }

    /**
     * Adds variables to be used for replacing absolute paths in classpath entries.
     *
     * @param variables A map where the keys are the variable names and the values are the variable values.
     */
    void variables(Map<String, File> variables) {
        assert variables != null
        classpath.pathVariables.putAll variables
    }
}
