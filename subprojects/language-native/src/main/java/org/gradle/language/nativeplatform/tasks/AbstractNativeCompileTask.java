/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.language.nativeplatform.tasks;

import com.google.common.collect.ImmutableList;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.changes.DiscoveredInputRecorder;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.nativeplatform.internal.incremental.IncrementalCompilerBuilder;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Compiles native source files into object files.
 */
@Incubating
public abstract class AbstractNativeCompileTask extends DefaultTask {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private boolean positionIndependentCode;
    private boolean debug;
    private boolean optimize;
    private final DirectoryVar objectFileDir;
    private final ConfigurableFileCollection includes;
    private final ConfigurableFileCollection source;
    private final Map<String, String> macros = new LinkedHashMap<String, String>();
    private final List<String> compilerArgs = new ArrayList<String>();
    private ImmutableList<String> includePaths;

    public AbstractNativeCompileTask() {
        includes = getProject().files();
        source = getProject().files();
        objectFileDir = newOutputDirectory();
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return NativeToolChainInternal.Identifier.identify(toolChain, targetPlatform);
            }
        });
        dependsOn(includes);
    }

    @Inject
    protected IncrementalCompilerBuilder getIncrementalCompilerBuilder() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void compile(IncrementalTaskInputs inputs) {
        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        NativeCompileSpec spec = createCompileSpec();
        spec.setTargetPlatform(targetPlatform);
        spec.setTempDir(getTemporaryDir());
        spec.setObjectFileDir(getObjectFileDir());
        spec.include(includes);
        spec.source(getSource());
        spec.setMacros(getMacros());
        spec.args(getCompilerArgs());
        spec.setPositionIndependentCode(isPositionIndependentCode());
        spec.setDebuggable(isDebuggable());
        spec.setOptimized(isOptimized());
        spec.setIncrementalCompile(inputs.isIncremental());
        spec.setDiscoveredInputRecorder((DiscoveredInputRecorder) inputs);
        spec.setOperationLogger(operationLogger);

        configureSpec(spec);

        PlatformToolProvider platformToolProvider = toolChain.select(targetPlatform);
        setDidWork(doCompile(spec, platformToolProvider).getDidWork());
    }

    protected void configureSpec(NativeCompileSpec spec) {
    }

    private <T extends NativeCompileSpec> WorkResult doCompile(T spec, PlatformToolProvider platformToolProvider) {
        Class<T> specType = Cast.uncheckedCast(spec.getClass());
        Compiler<T> baseCompiler = platformToolProvider.newCompiler(specType);
        Compiler<T> incrementalCompiler = getIncrementalCompilerBuilder().createIncrementalCompiler(this, baseCompiler, toolChain);
        Compiler<T> loggingCompiler = BuildOperationLoggingCompilerDecorator.wrap(incrementalCompiler);
        return loggingCompiler.execute(spec);
    }

    protected abstract NativeCompileSpec createCompileSpec();

    /**
     * The tool chain used for compilation.
     */
    @Internal
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    /**
     * The platform being targeted.
     */
    @Nested
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
    }

    /**
     * Should the compiler generate position independent code?
     */
    @Input
    public boolean isPositionIndependentCode() {
        return positionIndependentCode;
    }

    public void setPositionIndependentCode(boolean positionIndependentCode) {
        this.positionIndependentCode = positionIndependentCode;
    }

    /**
     * Should the compiler generate debuggable code?
     */
    @Input
    public boolean isDebuggable() {
        return debug;
    }

    public void setDebuggable(boolean debug) {
        this.debug = debug;
    }

    /**
     * Should the compiler generate omptimized code?
     */
    @Input
    public boolean isOptimized() {
        return optimize;
    }

    public void setOptimized(boolean optimize) {
        this.optimize = optimize;
    }

    /**
     * The directory where object files will be generated.
     *
     * @since 4.1
     */
    @OutputDirectory
    public DirectoryVar getObjectFileDirectory() {
        return objectFileDir;
    }

    @Internal
    public File getObjectFileDir() {
        return objectFileDir.getAsFile().getOrNull();
    }

    public void setObjectFileDir(File objectFileDir) {
        this.objectFileDir.set(objectFileDir);
    }

    /**
     * Sets the object file directory to output generated object file by the compilation process via a {@link Provider}.
     *
     * @param objectFileDir the object file directory provider to use
     * @see #setObjectFileDir(File)
     * @since 4.1
     */
    public void setObjectFileDir(Provider<? extends Directory> objectFileDir) {
        this.objectFileDir.set(objectFileDir);
    }

    /**
     * Returns the header directories to be used for compilation.
     */
    @Internal("The paths for include directories are tracked via the includePaths property, the contents are tracked via discovered inputs")
    public FileCollection getIncludes() {
        return includes;
    }

    @Input
    protected Collection<String> getIncludePaths() {
        if (includePaths == null) {
            Set<File> roots = includes.getFiles();
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (File root : roots) {
                builder.add(root.getAbsolutePath());
            }
            includePaths = builder.build();
        }
        return includePaths;
    }

    /**
     * Add directories where the compiler should search for header files.
     */
    public void includes(Object includeRoots) {
        includes.from(includeRoots);
    }

    /**
     * Returns the source files to be compiled.
     */
    @InputFiles
    public FileCollection getSource() {
        return source;
    }

    /**
     * Adds a set of source files to be compiled. The provided sourceFiles object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object sourceFiles) {
        source.from(sourceFiles);
    }

    /**
     * Macros that should be defined for the compiler.
     */
    @Input
    public Map<String, String> getMacros() {
        return macros;
    }

    public void setMacros(Map<String, String> macros) {
        this.macros.clear();
        this.macros.putAll(macros);
    }

    /**
     * Additional arguments to provide to the compiler.
     */
    @Input
    public List<String> getCompilerArgs() {
        return compilerArgs;
    }

    public void setCompilerArgs(List<String> compilerArgs) {
        this.compilerArgs.clear();
        this.compilerArgs.addAll(compilerArgs);
    }
}
