/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.util.RelativePathUtil;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class GroovyRecompilationSpecProvider extends AbstractRecompilationSpecProvider {
    private final CompilationSourceDirs sourceDirs;
    private final InputChanges inputChanges;
    private final Iterable<FileChange> sourceChanges;
    private final GroovySourceFileClassNameConverter sourceFileClassNameConverter;

    public GroovyRecompilationSpecProvider(FileOperations fileOperations,
                                           FileTreeInternal sources,
                                           InputChanges inputChanges,
                                           Iterable<FileChange> sourceChanges,
                                           GroovySourceFileClassNameConverter sourceFileClassNameConverter) {
        super(fileOperations, sources);
        this.sourceDirs = new CompilationSourceDirs(sources);
        this.inputChanges = inputChanges;
        this.sourceChanges = sourceChanges;
        this.sourceFileClassNameConverter = sourceFileClassNameConverter;
    }

    @Override
    public boolean isIncremental() {
        return inputChanges.isIncremental();
    }

    @Override
    public CompilationSourceDirs getSourceDirs() {
        return sourceDirs;
    }

    @Override
    public RecompilationSpec provideRecompilationSpec(CurrentCompilation current, PreviousCompilation previous) {
        RecompilationSpec spec = new RecompilationSpec();
        if (sourceFileClassNameConverter.isEmpty()) {
            spec.setFullRebuildCause("no source class mapping file found", null);
            return spec;
        }

        processClasspathChanges(current, previous, spec);
        processOtherChanges(previous, spec);

        spec.getClassesToProcess().addAll(previous.getTypesToReprocess());
        return spec;
    }

    @Override
    public void initializeCompilation(JavaCompileSpec spec, RecompilationSpec recompilationSpec) {
        if (!recompilationSpec.isBuildNeeded()) {
            spec.setSourceFiles(Collections.emptySet());
            spec.setClasses(Collections.emptySet());
            return;
        }

        Factory<PatternSet> patternSetFactory = fileOperations.getFileResolver().getPatternSetFactory();
        PatternSet classesToDelete = patternSetFactory.create();
        PatternSet filesToRecompile = patternSetFactory.create();

        prepareFilePatterns(recompilationSpec.getFilesToCompile(), classesToDelete, filesToRecompile);

        spec.setSourceFiles(sourceTree.matching(filesToRecompile));
        includePreviousCompilationOutputOnClasspath(spec);
        addClassesToProcess(spec, recompilationSpec);

        deleteStaleFilesIn(classesToDelete, spec.getDestinationDir());
    }

    private void prepareFilePatterns(Set<File> filesToCompile, PatternSet classesToDelete, PatternSet filesToRecompilePatterns) {
        for (File file : filesToCompile) {
            filesToRecompilePatterns.include(relativize(file));

            Collection<String> classes = sourceFileClassNameConverter.getClassNames(file);
            for (String staleClass : classes) {
                String path = staleClass.replaceAll("\\.", "/");
                classesToDelete.include(path.concat(".class"));
            }
        }
    }

    private String relativize(File sourceFile) {
        List<File> dirs = sourceDirs.getSourceRoots();
        for (File sourceDir : dirs) {
            if (sourceFile.getAbsolutePath().startsWith(sourceDir.getAbsolutePath())) {
                return RelativePathUtil.relativePath(sourceDir, sourceFile);
            }
        }
        throw new IllegalStateException("Not found " + sourceFile + " in source dirs: " + dirs);
    }

    private void processOtherChanges(PreviousCompilation previous, RecompilationSpec spec) {
        if (spec.getFullRebuildCause() != null) {
            return;
        }
        SourceFileChangeProcessor sourceFileChangeProcessor = new SourceFileChangeProcessor(previous, sourceFileClassNameConverter);
        for (FileChange fileChange : sourceChanges) {
            if (spec.getFullRebuildCause() != null) {
                return;
            }
            spec.getFilesToCompile().add(fileChange.getFile());
            sourceFileChangeProcessor.processChange(fileChange.getFile(), spec);
        }

        for (String className : spec.getClassesToCompile()) {
            if (spec.getFullRebuildCause() != null) {
                return;
            }

            Optional<File> sourceFile = sourceFileClassNameConverter.getFile(className);
            if (sourceFile.isPresent()) {
                spec.getFilesToCompile().add(sourceFile.get());
            } else {
                spec.setFullRebuildCause("Can't find source file of class " + className, null);
            }
        }
    }
}
