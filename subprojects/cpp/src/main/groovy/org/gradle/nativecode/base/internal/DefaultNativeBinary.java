/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.internal.BinaryNamingScheme;
import org.gradle.language.base.internal.TaskNamerForBinaries;
import org.gradle.nativecode.base.NativeComponent;
import org.gradle.nativecode.base.NativeDependencySet;
import org.gradle.nativecode.base.SourceSet;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class DefaultNativeBinary implements NativeBinaryInternal {
    private final ResolvableNativeDependencySet libs = new ResolvableNativeDependencySet();
    private final DomainObjectSet<SourceSet> source = new DefaultDomainObjectSet<SourceSet>(SourceSet.class);
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final ArrayList<Object> compilerArgs = new ArrayList<Object>();
    private final ArrayList<Object> linkerArgs = new ArrayList<Object>();
    private final ArrayList<Object> defines = new ArrayList<Object>();
    private final TaskNamerForBinaries namer;
    private final String name;
    private final ToolChainInternal toolChain;
    private File outputFile;

    protected DefaultNativeBinary(NativeComponent owner, String name, ToolChainInternal toolChain) {
        this.name = name;
        this.toolChain = toolChain;
        namer = new TaskNamerForBinaries(name);
        owner.getSource().all(new Action<SourceSet>() {
            public void execute(SourceSet sourceSet) {
                source.add(sourceSet);
            }
        });
    }

    public String getName() {
        return name;
    }

    public ToolChainInternal getToolChain() {
        return toolChain;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public DomainObjectSet<SourceSet> getSource() {
        return source;
    }

    public void source(SourceSet sourceSet) {
        source.add(sourceSet);
    }

    public List<Object> getMacros() {
        return defines;
    }

    public void define(Object... defines) {
        Collections.addAll(this.defines, defines);
    }

    public List<Object> getCompilerArgs() {
        return compilerArgs;
    }

    public void compilerArgs(Object... args) {
        Collections.addAll(compilerArgs, args);
    }

    public List<Object> getLinkerArgs() {
        return linkerArgs;
    }

    public void linkerArgs(Object... args) {
        Collections.addAll(linkerArgs, args);
    }

    public BinaryNamingScheme getNamingScheme() {
        return namer;
    }

    public Collection<NativeDependencySet> getLibs() {
        return libs.resolve();
    }

    public void lib(Object notation) {
        libs.add(notation);
    }

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    public abstract String getOutputFileName();

    protected abstract NativeComponent getComponent();

}
