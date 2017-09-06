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
package org.gradle.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileVar;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.logging.BuildOperationLogger;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.tasks.SimpleStaleClassCleaner;
import org.gradle.nativeplatform.internal.BuildOperationLoggingCompilerDecorator;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Base task for linking a native binary from object files and libraries.
 */
@Incubating
public abstract class AbstractLinkTask extends DefaultTask implements ObjectFilesToBinary {
    private NativeToolChainInternal toolChain;
    private NativePlatformInternal targetPlatform;
    private boolean debuggable;
    private final RegularFileVar outputFile;
    private List<String> linkerArgs = new ArrayList<String>();
    private final ConfigurableFileCollection source;
    private final ConfigurableFileCollection libs;

    public AbstractLinkTask() {
        libs = getProject().files();
        source = getProject().files();
        outputFile = newOutputFile();
        getInputs().property("outputType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return NativeToolChainInternal.Identifier.identify(toolChain, targetPlatform);
            }
        });
    }

    /**
     * The tool chain used for linking.
     */
    @Internal
    public NativeToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(NativeToolChain toolChain) {
        this.toolChain = (NativeToolChainInternal) toolChain;
    }

    /**
     * The platform that the linked binary will run on.
     */
    @Nested
    public NativePlatform getTargetPlatform() {
        return targetPlatform;
    }

    public void setTargetPlatform(NativePlatform targetPlatform) {
        this.targetPlatform = (NativePlatformInternal) targetPlatform;
    }

    /**
     * Include the destination directory as an output, to pick up auxiliary files produced alongside the main output file
     */
    @OutputDirectory
    public File getDestinationDir() {
        return getOutputFile().getParentFile();
    }

    /**
     * The file where the linked binary will be located.
     *
     * @since 4.1
     */
    @OutputFile
    public RegularFileVar getBinaryFile() {
        return outputFile;
    }

    @Internal
    public File getOutputFile() {
        return outputFile.getAsFile().getOrNull();
    }

    public void setOutputFile(File outputFile) {
        this.outputFile.set(outputFile);
    }

    /**
     * Sets the output file generated by the linking process via a {@link Provider}.
     *
     * @param outputFile the output file provider to use
     * @see #setOutputFile(File)
     * @since 4.1
     */
    public void setOutputFile(Provider<? extends RegularFile> outputFile) {
        this.outputFile.set(outputFile);
    }

    /**
     * Additional arguments passed to the linker.
     */
    @Input
    public List<String> getLinkerArgs() {
        return linkerArgs;
    }

    public void setLinkerArgs(List<String> linkerArgs) {
        this.linkerArgs = linkerArgs;
    }

    /**
     * Create a debuggable binary?
     */
    @Input
    public boolean isDebuggable() {
        return debuggable;
    }

    public void setDebuggable(boolean debuggable) {
        this.debuggable = debuggable;
    }

    /**
     * The source object files to be passed to the linker.
     */
    @InputFiles
    public FileCollection getSource() {
        return source;
    }

    public void setSource(FileCollection source) {
        this.source.setFrom(source);
    }

    /**
     * The library files to be passed to the linker.
     */
    @InputFiles
    public FileCollection getLibs() {
        return libs;
    }

    public void setLibs(FileCollection libs) {
        this.libs.setFrom(libs);
    }

    /**
     * Adds a set of object files to be linked. The provided source object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void source(Object source) {
        this.source.from(source);
    }

    /**
     * Adds a set of library files to be linked. The provided libs object is evaluated as per {@link org.gradle.api.Project#files(Object...)}.
     */
    public void lib(Object libs) {
        this.libs.from(libs);
    }

    @Inject
    public BuildOperationLoggerFactory getOperationLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void link() {
        SimpleStaleClassCleaner cleaner = new SimpleStaleClassCleaner(getOutputs());
        cleaner.setDestinationDir(getDestinationDir());
        cleaner.execute();

        if (getSource().isEmpty()) {
            setDidWork(false);
            return;
        }

        LinkerSpec spec = createLinkerSpec();
        spec.setTargetPlatform(getTargetPlatform());
        spec.setTempDir(getTemporaryDir());
        spec.setOutputFile(getOutputFile());

        spec.objectFiles(getSource());
        spec.libraries(getLibs());
        spec.args(getLinkerArgs());
        spec.setDebuggable(isDebuggable());

        BuildOperationLogger operationLogger = getOperationLoggerFactory().newOperationLogger(getName(), getTemporaryDir());
        spec.setOperationLogger(operationLogger);

        Compiler<LinkerSpec> compiler = Cast.uncheckedCast(toolChain.select(targetPlatform).newCompiler(spec.getClass()));
        compiler = BuildOperationLoggingCompilerDecorator.wrap(compiler);
        WorkResult result = compiler.execute(spec);
        setDidWork(result.getDidWork());
    }

    protected abstract LinkerSpec createLinkerSpec();

}
