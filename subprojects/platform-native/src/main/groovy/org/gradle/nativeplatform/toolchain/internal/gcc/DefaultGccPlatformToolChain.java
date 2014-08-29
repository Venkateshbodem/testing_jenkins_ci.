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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.nativeplatform.platform.Platform;
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolRegistry;

import java.util.HashMap;
import java.util.Map;

public class DefaultGccPlatformToolChain implements GccPlatformToolChain, ToolRegistry {
    private final Platform platform;
    private final Map<ToolType, GccCommandLineToolConfigurationInternal> tools = new HashMap<ToolType, GccCommandLineToolConfigurationInternal>();

    public DefaultGccPlatformToolChain(Platform platform) {
        this.platform = platform;
    }

    public GccCommandLineToolConfigurationInternal getTool(ToolType toolType) {
        return tools.get(toolType);
    }

    public Map<ToolType, GccCommandLineToolConfigurationInternal> getTools() {
        return tools;
    }

    public void add(DefaultGccCommandLineToolConfiguration tool) {
        tools.put(tool.getToolType(), tool);
    }

    public Platform getPlatform() {
        return platform;
    }

    public GccCommandLineToolConfigurationInternal getcCompiler() {
        return tools.get(ToolType.C_COMPILER);
    }

    public GccCommandLineToolConfigurationInternal getCppCompiler() {
        return tools.get(ToolType.CPP_COMPILER);
    }

    public GccCommandLineToolConfigurationInternal getObjcCompiler() {
        return tools.get(ToolType.OBJECTIVEC_COMPILER);
    }

    public GccCommandLineToolConfigurationInternal getObjcppCompiler() {
        return tools.get(ToolType.OBJECTIVECPP_COMPILER);
    }

    public GccCommandLineToolConfigurationInternal getAssembler() {
        return tools.get(ToolType.ASSEMBLER);
    }

    public GccCommandLineToolConfigurationInternal getLinker() {
        return tools.get(ToolType.LINKER);
    }

    public GccCommandLineToolConfigurationInternal getStaticLibArchiver() {
        return tools.get(ToolType.STATIC_LIB_ARCHIVER);
    }
}