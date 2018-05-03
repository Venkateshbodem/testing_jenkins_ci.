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

package org.gradle.api.internal.tasks.compile.incremental;

import java.io.File;
import java.util.List;

import static java.lang.String.format;

public class SourceToNameConverter {

    private CompilationSourceDirs sourceDirs;

    public SourceToNameConverter(CompilationSourceDirs sourceDirs) {
        this.sourceDirs = sourceDirs;
    }

    public String getClassName(File javaSourceClass) {
        List<String> dirs = sourceDirs.getSourceRoots();
        for (String sourceDirAbsolutePath : dirs) {
            String javaSourceClassAbsolutePath = javaSourceClass.getAbsolutePath();
            if (javaSourceClassAbsolutePath.startsWith(sourceDirAbsolutePath)) {
                int endIndex = javaSourceClassAbsolutePath.endsWith(".java") ? javaSourceClassAbsolutePath.length() - 5 : javaSourceClassAbsolutePath.length();
                String relativePath = javaSourceClassAbsolutePath.substring(sourceDirAbsolutePath.length(), endIndex);
                return relativePath.replace(File.separatorChar, '.');
            }
        }
        throw new IllegalArgumentException(format("Unable to find source java class: '%s' because it does not belong to any of the source dirs: '%s'",
                javaSourceClass, dirs));

    }
}
