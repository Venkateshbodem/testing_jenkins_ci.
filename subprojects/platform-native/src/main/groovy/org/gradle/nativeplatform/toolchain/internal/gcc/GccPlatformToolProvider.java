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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.process.internal.ExecActionFactory;

class GccPlatformToolProvider extends AbstractPlatformToolProvider {
    private final ToolSearchPath toolSearchPath;
    private final ToolRegistry toolRegistry;
    private final ExecActionFactory execActionFactory;
    private final boolean useCommandFile;

    GccPlatformToolProvider(BuildOperationProcessor buildOperationProcessor, OperatingSystemInternal targetOperatingSystem, ToolSearchPath toolSearchPath, ToolRegistry toolRegistry, ExecActionFactory execActionFactory, boolean useCommandFile) {
        super(buildOperationProcessor, targetOperatingSystem);
        this.toolRegistry = toolRegistry;
        this.toolSearchPath = toolSearchPath;
        this.useCommandFile = useCommandFile;
        this.execActionFactory = execActionFactory;
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        GccCommandLineToolConfigurationInternal cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER);
        CppCompiler cppCompiler = new CppCompiler(buildOperationProcessor, commandLineTool(cppCompilerTool), context(cppCompilerTool), getObjectFileExtensionCalculator(), useCommandFile);
        return new OutputCleaningCompiler<CppCompileSpec>(cppCompiler, getObjectFileExtensionCalculator());
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        GccCommandLineToolConfigurationInternal cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER);
        CCompiler cCompiler = new CCompiler(buildOperationProcessor, commandLineTool(cCompilerTool), context(cCompilerTool), getObjectFileExtensionCalculator(), useCommandFile);
        return new OutputCleaningCompiler<CCompileSpec>(cCompiler, getObjectFileExtensionCalculator());
    }

    @Override
    protected Compiler<ObjectiveCppCompileSpec> createObjectiveCppCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCppCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVECPP_COMPILER);
        ObjectiveCppCompiler objectiveCppCompiler = new ObjectiveCppCompiler(buildOperationProcessor, commandLineTool(objectiveCppCompilerTool), context(objectiveCppCompilerTool), getObjectFileExtensionCalculator(), useCommandFile);
        return new OutputCleaningCompiler<ObjectiveCppCompileSpec>(objectiveCppCompiler, getObjectFileExtensionCalculator());
    }

    @Override
    protected Compiler<ObjectiveCCompileSpec> createObjectiveCCompiler() {
        GccCommandLineToolConfigurationInternal objectiveCCompilerTool = toolRegistry.getTool(ToolType.OBJECTIVEC_COMPILER);
        ObjectiveCCompiler objectiveCCompiler = new ObjectiveCCompiler(buildOperationProcessor, commandLineTool(objectiveCCompilerTool), context(objectiveCCompilerTool), getObjectFileExtensionCalculator(), useCommandFile);
        return new OutputCleaningCompiler<ObjectiveCCompileSpec>(objectiveCCompiler, getObjectFileExtensionCalculator());
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        GccCommandLineToolConfigurationInternal assemblerTool = toolRegistry.getTool(ToolType.ASSEMBLER);
        return new Assembler(buildOperationProcessor, commandLineTool(assemblerTool), context(assemblerTool), getObjectFileExtensionCalculator(), useCommandFile);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        GccCommandLineToolConfigurationInternal linkerTool = toolRegistry.getTool(ToolType.LINKER);
        return new GccLinker(buildOperationProcessor, commandLineTool(linkerTool), context(linkerTool), useCommandFile);
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        GccCommandLineToolConfigurationInternal staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER);
        return new ArStaticLibraryArchiver(buildOperationProcessor, commandLineTool(staticLibArchiverTool), context(staticLibArchiverTool));
    }

    private CommandLineToolInvocationWorker commandLineTool(GccCommandLineToolConfigurationInternal tool) {
        ToolType key = tool.getToolType();
        String exeName = tool.getExecutable();
        return new DefaultCommandLineToolInvocationWorker(key.getToolName(), toolSearchPath.locate(key, exeName).getTool(), execActionFactory);
    }

    private CommandLineToolContext context(GccCommandLineToolConfigurationInternal toolConfiguration) {
        MutableCommandLineToolContext baseInvocation = new DefaultMutableCommandLineToolContext();
        // MinGW requires the path to be set
        baseInvocation.addPath(toolSearchPath.getPath());
        baseInvocation.addEnvironmentVar("CYGWIN", "nodosfilewarning");
        baseInvocation.setArgAction(toolConfiguration.getArgAction());
        return baseInvocation;
    }
}
