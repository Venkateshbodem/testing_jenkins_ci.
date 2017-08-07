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

package org.gradle.caching.internal.tasks;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.apache.tools.tar.TarOutputStream;
import org.apache.tools.zip.UnixStat;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.DefaultDirectoryWalkerFactory;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.specs.Specs;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.ensureDirectoryForProperty;
import static org.gradle.caching.internal.tasks.TaskOutputPackerUtils.makeDirectory;

/**
 * Packages task output to a POSIX TAR file.
 */
@SuppressWarnings("Since15")
public class TarTaskOutputPacker implements TaskOutputPacker {
    private static final String METADATA_PATH = "METADATA";
    private static final Pattern PROPERTY_PATH = Pattern.compile("(missing-)?property-([^/]+)(?:/(.*))?");
    @SuppressWarnings("OctalInteger")
    private static final int FILE_PERMISSION_MASK = 0777;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ThreadLocal<byte[]> COPY_BUFFERS = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[BUFFER_SIZE];
        }
    };

    private final DefaultDirectoryWalkerFactory directoryWalkerFactory;
    private final FileSystem fileSystem;

    public TarTaskOutputPacker(FileSystem fileSystem) {
        this.directoryWalkerFactory = new DefaultDirectoryWalkerFactory(JavaVersion.current(), fileSystem);
        this.fileSystem = fileSystem;
    }

    @Override
    public PackResult pack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, OutputStream output, TaskOutputOriginWriter writeOrigin) {
        TarOutputStream tarOutput = new TarOutputStream(output);
        try {
            tarOutput.setLongFileMode(TarOutputStream.LONGFILE_POSIX);
            tarOutput.setBigNumberMode(TarOutputStream.BIGNUMBER_POSIX);
            tarOutput.setAddPaxHeadersForNonAsciiNames(true);
            packMetadata(writeOrigin, tarOutput);
            return new PackResult(pack(propertySpecs, tarOutput) + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtils.closeQuietly(tarOutput);
        }
    }

    private void packMetadata(TaskOutputOriginWriter writeMetadata, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry(METADATA_PATH);
        entry.setMode(UnixStat.FILE_FLAG | UnixStat.DEFAULT_FILE_PERM);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeMetadata.execute(baos);
        entry.setSize(baos.size());
        outputStream.putNextEntry(entry);
        outputStream.write(baos.toByteArray());
        outputStream.closeEntry();
    }

    private long pack(Collection<ResolvedTaskOutputFilePropertySpec> propertySpecs, TarOutputStream outputStream) {
        long entries = 0;
        for (ResolvedTaskOutputFilePropertySpec spec : propertySpecs) {
            try {
                entries += packProperty(spec, outputStream);
            } catch (Exception ex) {
                throw new GradleException(String.format("Could not pack property '%s': %s", spec.getPropertyName(), ex.getMessage()), ex);
            }
        }
        return entries;
    }

    private long packProperty(CacheableTaskOutputFilePropertySpec propertySpec, TarOutputStream outputStream) throws IOException {
        String propertyName = propertySpec.getPropertyName();
        File outputFile = propertySpec.getOutputFile();
        if (outputFile == null) {
            return 0;
        }
        String propertyPath = "property-" + propertyName;
        if (!outputFile.exists()) {
            storeMissingProperty(propertyPath, outputStream);
            return 1;
        }
        switch (propertySpec.getOutputType()) {
            case DIRECTORY:
                return 1 + storeDirectoryProperty(propertyPath, outputFile, outputStream);
            case FILE:
                storeFileProperty(propertyPath, outputFile, outputStream);
                return 1;
            default:
                throw new AssertionError();
        }
    }

    private long storeDirectoryProperty(String propertyPath, File directory, final TarOutputStream tarOutput) throws IOException {
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a directory", directory));
        }
        final String propertyRoot = propertyPath + "/";
        //noinspection OctalInteger
        createTarEntry(propertyRoot, 0, UnixStat.DIR_FLAG | 0755, tarOutput);
        tarOutput.closeEntry();

        class CountingFileVisitor implements FileVisitor {

            private long entries;

            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                try {
                    ++entries;
                    storeDirectoryEntry(dirDetails, propertyRoot, tarOutput);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                try {
                    ++entries;
                    String path = propertyRoot + fileDetails.getRelativePath().getPathString();
                    storeFileEntry(fileDetails.getFile(), path, fileDetails.getSize(), fileDetails.getMode(), tarOutput);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        CountingFileVisitor visitor = new CountingFileVisitor();
        directoryWalkerFactory.create().walkDir(directory, RelativePath.EMPTY_ROOT, visitor, Specs.satisfyAll(), new AtomicBoolean(), false);
        return visitor.entries;
    }

    private void storeFileProperty(String propertyPath, File file, TarOutputStream tarOutput) throws IOException {
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("Expected '%s' to be a file", file));
        }
        storeFileEntry(file, propertyPath, file.length(), fileSystem.getUnixMode(file), tarOutput);
    }

    private void storeMissingProperty(String propertyPath, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry("missing-" + propertyPath);
        outputStream.putNextEntry(entry);
        outputStream.closeEntry();
    }

    private void storeDirectoryEntry(FileVisitDetails dirDetails, String propertyRoot, TarOutputStream outputStream) throws IOException {
        String path = dirDetails.getRelativePath().getPathString();
        createTarEntry(propertyRoot + path + "/", 0, UnixStat.DIR_FLAG | dirDetails.getMode(), outputStream);
        outputStream.closeEntry();
    }

    private void storeFileEntry(File inputFile, String path, long size, int mode, TarOutputStream tarOutput) throws IOException {
        createTarEntry(path, size, UnixStat.FILE_FLAG | mode, tarOutput);
        InputStream input = Files.newInputStream(inputFile.toPath());
        try {
            IOUtils.copyLarge(input, tarOutput, COPY_BUFFERS.get());
        } finally {
            IOUtils.closeQuietly(input);
        }
        tarOutput.closeEntry();
    }

    private static void createTarEntry(String path, long size, int mode, TarOutputStream outputStream) throws IOException {
        TarEntry entry = new TarEntry(path);
        entry.setSize(size);
        entry.setMode(mode);
        outputStream.putNextEntry(entry);
    }

    @Override
    public UnpackResult unpack(final SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, final InputStream input, final TaskOutputOriginReader readOrigin) {
        return IoActions.withResource(new TarInputStream(input), new Transformer<UnpackResult, TarInputStream>() {
            @Override
            public UnpackResult transform(TarInputStream tarInput) {
                try {
                    return unpack(propertySpecs, tarInput, readOrigin);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private UnpackResult unpack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, TarInputStream tarInput, TaskOutputOriginReader readOriginAction) throws IOException {
        Map<String, ResolvedTaskOutputFilePropertySpec> propertySpecsMap = Maps.uniqueIndex(propertySpecs, new Function<TaskFilePropertySpec, String>() {
            @Override
            public String apply(TaskFilePropertySpec propertySpec) {
                return propertySpec.getPropertyName();
            }
        });
        TarEntry entry;
        TaskOutputOriginMetadata originMetadata = null;

        long entries = 0;
        while ((entry = tarInput.getNextEntry()) != null) {
            ++entries;
            String name = entry.getName();

            if (name.equals(METADATA_PATH)) {
                // handle origin metadata
                originMetadata = readOriginAction.execute(new CloseShieldInputStream(tarInput));
            } else {
                // handle output property
                Matcher matcher = PROPERTY_PATH.matcher(name);
                if (!matcher.matches()) {
                    throw new IllegalStateException("Cached result format error, invalid contents: " + name);
                }

                String propertyName = matcher.group(2);
                ResolvedTaskOutputFilePropertySpec propertySpec = propertySpecsMap.get(propertyName);
                if (propertySpec == null) {
                    throw new IllegalStateException(String.format("No output property '%s' registered", propertyName));
                }

                boolean outputMissing = matcher.group(1) != null;
                String childPath = matcher.group(3);
                unpackPropertyEntry(propertySpec, tarInput, entry, childPath, outputMissing);
            }
        }
        if (originMetadata == null) {
            throw new IllegalStateException("Cached result format error, no origin metadata was found.");
        }

        return new UnpackResult(originMetadata, entries);
    }

    private void unpackPropertyEntry(ResolvedTaskOutputFilePropertySpec propertySpec, InputStream input, TarEntry entry, String childPath, boolean missing) throws IOException {
        File propertyRoot = propertySpec.getOutputFile();
        if (propertyRoot == null) {
            throw new IllegalStateException("Optional property should have a value: " + propertySpec.getPropertyName());
        }

        File outputFile;
        boolean isDirEntry = entry.isDirectory();
        if (Strings.isNullOrEmpty(childPath)) {
            // We are handling the root of the property here
            if (missing) {
                if (!makeDirectory(propertyRoot.getParentFile())) {
                    // Make sure output is removed if it exists already
                    if (propertyRoot.exists()) {
                        FileUtils.forceDelete(propertyRoot);
                    }
                }
                return;
            }

            OutputType outputType = propertySpec.getOutputType();
            if (isDirEntry) {
                if (outputType != OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output directory property: " + propertySpec.getPropertyName());
                }
            } else {
                if (outputType == OutputType.DIRECTORY) {
                    throw new IllegalStateException("Property should be an output file property: " + propertySpec.getPropertyName());
                }
            }
            ensureDirectoryForProperty(outputType, propertyRoot);
            outputFile = propertyRoot;
        } else {
            outputFile = new File(propertyRoot, childPath);
        }

        if (isDirEntry) {
            FileUtils.forceMkdir(outputFile);
        } else {
            OutputStream output = Files.newOutputStream(outputFile.toPath());
            try {
                IOUtils.copyLarge(input, output, COPY_BUFFERS.get());
            } finally {
                IOUtils.closeQuietly(output);
            }
        }

        //noinspection OctalInteger
        fileSystem.chmod(outputFile, entry.getMode() & FILE_PERMISSION_MASK);
    }
}
