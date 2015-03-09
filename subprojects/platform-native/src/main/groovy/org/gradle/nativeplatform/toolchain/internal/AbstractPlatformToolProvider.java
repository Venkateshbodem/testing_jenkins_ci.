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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.compilespec.*;
import org.gradle.util.TreeVisitor;

public class AbstractPlatformToolProvider implements PlatformToolProvider {
    protected final OperatingSystemInternal targetOperatingSystem;
    protected final BuildOperationProcessor buildOperationProcessor;
    private final ObjectFileExtensionCalculator objectFileExtensionCalculator;

    public AbstractPlatformToolProvider(BuildOperationProcessor buildOperationProcessor, OperatingSystemInternal targetOperatingSystem) {
        this.targetOperatingSystem = targetOperatingSystem;
        this.buildOperationProcessor = buildOperationProcessor;
        this.objectFileExtensionCalculator = new DefaultObjectFileExtensionCalculator(targetOperatingSystem);
    }

    public boolean isAvailable() {
        return true;
    }

    public void explain(TreeVisitor<? super String> visitor) {
    }

    public ObjectFileExtensionCalculator getObjectFileExtensionCalculator() {
        return objectFileExtensionCalculator;
    }

    public String getExecutableName(String executablePath) {
        return targetOperatingSystem.getInternalOs().getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getSharedLibraryLinkFileName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getSharedLibraryName(libraryPath);
    }

    public String getStaticLibraryName(String libraryPath) {
        return targetOperatingSystem.getInternalOs().getStaticLibraryName(libraryPath);
    }

    @Override
    public <T> T get(Class<T> toolType) {
        throw new IllegalArgumentException(String.format("Don't know how to provide tool of type %s.", toolType.getSimpleName()));
    }

    public <T extends CompileSpec> org.gradle.language.base.internal.compile.Compiler<T> newCompiler(Class<T> spec) {
        if (CppCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCppCompiler());
        }
        if (CCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createCCompiler());
        }
        if (ObjectiveCppCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCppCompiler());
        }
        if (ObjectiveCCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createObjectiveCCompiler());
        }
        if (WindowsResourceCompileSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createWindowsResourceCompiler());
        }
        if (AssembleSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createAssembler());
        }
        if (LinkerSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createLinker());
        }
        if (StaticLibraryArchiverSpec.class.isAssignableFrom(spec)) {
            return CompilerUtil.castCompiler(createStaticLibraryArchiver());
        }
        throw new IllegalArgumentException(String.format("Don't know how to compile from a spec of type %s.", spec.getClass().getSimpleName()));
    }

    protected final RuntimeException unavailableTool(String message) {
        return new RuntimeException(message);
    }

    protected Compiler<?> createCppCompiler() {
        throw unavailableTool("C++ compiler is not available");
    }

    protected Compiler<?> createCCompiler() {
        throw unavailableTool("C compiler is not available");
    }

    protected Compiler<?> createObjectiveCppCompiler() {
        throw unavailableTool("Obj-C++ compiler is not available");
    }

    protected Compiler<?> createObjectiveCCompiler() {
        throw unavailableTool("Obj-C compiler is not available");
    }

    protected Compiler<?> createWindowsResourceCompiler() {
        throw unavailableTool("Windows resource compiler is not available");
    }

    protected Compiler<?> createAssembler() {
        throw unavailableTool("Assembler is not available");
    }

    protected Compiler<?> createLinker() {
        throw unavailableTool("Linker is not available");
    }

    protected Compiler<?> createStaticLibraryArchiver() {
        throw unavailableTool("Static library archiver is not available");
    }
}
