/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

import javax.annotation.Nullable;

public interface ResolvedCopySpec {
    @Input
    String getDestinationPath();

    @Internal
    RelativePath getDestPath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    FileTree getSource();

    @Input
    boolean isCaseSensitive();

    @Input
    boolean isIncludeEmptyDirs();

    @Input
    DuplicatesStrategy getDuplicatesStrategy();

    @Nullable
    @Optional
    @Input
    Integer getFileMode();

    @Nullable
    @Optional
    @Input
    Integer getDirMode();

    @Input
    String getFilteringCharset();

    @Internal
    Iterable<Action<? super FileCopyDetails>> getCopyActions();

    @Nested
    Iterable<ResolvedCopySpec> getChildren();

    void walk(Action<? super ResolvedCopySpec> action);
}
