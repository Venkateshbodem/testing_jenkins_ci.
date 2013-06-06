/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativecode.language.cpp.internal;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.nativecode.base.NativeDependencySet;
import org.gradle.nativecode.base.internal.ConfigurationBasedNativeDependencySet;
import org.gradle.nativecode.base.internal.ResolvableNativeDependencySet;
import org.gradle.nativecode.language.cpp.CppSourceSet;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;
import java.util.Map;

public class DefaultCppSourceSet implements CppSourceSet {

    private final String name;

    private final DefaultSourceDirectorySet exportedHeaders;
    private final DefaultSourceDirectorySet source;
    private final ResolvableNativeDependencySet libs;
    private final ConfigurationBasedNativeDependencySet configurationDependencySet;

    public DefaultCppSourceSet(String name, ProjectInternal project) {
        this.name = name;

        this.exportedHeaders = new DefaultSourceDirectorySet("exported headers", project.getFileResolver());
        this.source = new DefaultSourceDirectorySet("source", project.getFileResolver());
        this.libs = new ResolvableNativeDependencySet();
        this.configurationDependencySet = new ConfigurationBasedNativeDependencySet(project, name);
        
        libs.add(configurationDependencySet);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("C++ source '%s'", name);
    }

    public SourceDirectorySet getExportedHeaders() {
        return exportedHeaders;
    }

    public DefaultCppSourceSet exportedHeaders(Closure closure) {
        ConfigureUtil.configure(closure, exportedHeaders);
        return this;
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public DefaultCppSourceSet source(Closure closure) {
        ConfigureUtil.configure(closure, source);
        return this;
    }

    public Collection<NativeDependencySet> getLibs() {
        return libs.resolve();
    }

    public void lib(Object library) {
        libs.add(library);
    }

    public void dependency(Map<?, ?> dep) {
        configurationDependencySet.add(dep);
    }
}