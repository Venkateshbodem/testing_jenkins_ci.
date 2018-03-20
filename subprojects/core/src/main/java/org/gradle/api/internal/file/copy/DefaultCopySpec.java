/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NonExtensible;
import org.gradle.api.Transformer;
import org.gradle.api.file.CopyProcessingSpec;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.ChainingTransformer;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.pattern.PatternMatcherFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FilterReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@NonExtensible
public class DefaultCopySpec implements CopySpecInternal {
    private static final NotationParser<Object, String> PATH_NOTATION_PARSER = PathNotationConverter.parser();
    protected final FileResolver fileResolver;
    private final Set<Object> sourcePaths = new LinkedHashSet<Object>();
    private Object destDir;
    private final PatternSet patternSet;
    private final List<CopySpecInternal> parentSpecs = Lists.newLinkedList();
    private final List<CopySpecInternal> childSpecs = new LinkedList<CopySpecInternal>();
    protected final Instantiator instantiator;
    private final List<Action<? super FileCopyDetails>> copyActions = new LinkedList<Action<? super FileCopyDetails>>();
    private boolean hasCustomActions;
    protected Integer dirMode;
    protected Integer fileMode;
    protected Boolean caseSensitive;
    protected Boolean includeEmptyDirs;
    protected DuplicatesStrategy duplicatesStrategy;
    protected String filteringCharset;

    public DefaultCopySpec(FileResolver resolver, Instantiator instantiator) {
        this.fileResolver = resolver;
        this.instantiator = instantiator;
        PatternSet patternSet = resolver.getPatternSetFactory().create();
        assert patternSet != null;
        this.patternSet = patternSet;
    }

    @Override
    public boolean hasCustomActions() {
        if (hasCustomActions) {
            return true;
        }
        for (CopySpecInternal childSpec : childSpecs) {
            if (childSpec.hasCustomActions()) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    List<Action<? super FileCopyDetails>> getCopyActions() {
        return copyActions;
    }

    @Override
    public CopySpec with(CopySpec... copySpecs) {
        for (CopySpec copySpec : copySpecs) {
            CopySpecInternal copySpecInternal;
            if (copySpec instanceof CopySpecSource) {
                CopySpecSource copySpecSource = (CopySpecSource) copySpec;
                copySpecInternal = copySpecSource.getRootSpec();
            } else {
                copySpecInternal = (CopySpecInternal) copySpec;
            }
            addChildSpec(copySpecInternal);
        }
        return this;
    }

    @Override
    public CopySpec from(Object... sourcePaths) {
        Collections.addAll(this.sourcePaths, sourcePaths);
        return this;
    }

    @Override
    public CopySpec from(Object sourcePath, final Closure c) {
        return from(sourcePath, new ClosureBackedAction<CopySpec>(c));
    }

    @Override
    public CopySpec from(Object sourcePath, Action<? super CopySpec> configureAction) {
        //noinspection ConstantConditions
        if (configureAction == null) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour("Gradle does not allow passing null for the configuration action for CopySpec.from()");
            from(sourcePath);
            return this;
        } else {
            CopySpecInternal child = addChild();
            child.from(sourcePath);
            CopySpecWrapper wrapper = instantiator.newInstance(CopySpecWrapper.class, child);
            configureAction.execute(wrapper);
            return wrapper;
        }
    }

    @Override
    public CopySpecInternal addFirst() {
        return addChildAtPosition(0);
    }

    protected CopySpecInternal addChildAtPosition(int position) {
        DefaultCopySpec child = instantiator.newInstance(SingleParentCopySpec.class, fileResolver, instantiator, this);
        addChildSpec(position, child);
        return child;
    }

    @Override
    public CopySpecInternal addChild() {
        return addChildAtPosition(childSpecs.size());
    }

    @Override
    public CopySpecInternal addChildBeforeSpec(CopySpecInternal childSpec) {
        int position = childSpecs.indexOf(childSpec);
        return position != -1 ? addChildAtPosition(position) : addChild();
    }

    private void addChildSpec(CopySpecInternal childSpec) {
        addChildSpec(childSpecs.size(), childSpec);
    }

    private void addChildSpec(int index, CopySpecInternal childSpec) {
        childSpecs.add(index, childSpec);
        childSpec.addedToParent(this);
        descendantAdded(childSpec);
    }

    @Override
    public void addedToParent(CopySpecInternal parent) {
        parentSpecs.add(parent);
    }

    @Override
    public void descendantAdded(CopySpecInternal descendantSpec) {
        for (CopySpecInternal parent : parentSpecs) {
            parent.descendantAdded(descendantSpec);
        }
    }

    @VisibleForTesting
    public Set<Object> getSourcePaths() {
        return sourcePaths;
    }


    @Override
    public CopySpec into(Object destDir) {
        this.destDir = destDir;
        return this;
    }

    @Override
    public CopySpec into(Object destPath, Closure configureClosure) {
        return into(destPath, new ClosureBackedAction<CopySpec>(configureClosure));
    }

    @Override
    public CopySpec into(Object destPath, Action<? super CopySpec> copySpec) {
        //noinspection ConstantConditions
        if (copySpec == null) {
            DeprecationLogger.nagUserOfDeprecatedBehaviour("Gradle does not allow passing null for the configuration action for CopySpec.into()");
            into(destPath);
            return this;
        } else {
            CopySpecInternal child = addChild();
            child.into(destPath);
            CopySpecWrapper wrapper = instantiator.newInstance(CopySpecWrapper.class, child);
            copySpec.execute(wrapper);
            return wrapper;
        }
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive != null ? caseSensitive : true;
    }

    @Override
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        this.patternSet.setCaseSensitive(caseSensitive);
    }

    @Override
    public boolean getIncludeEmptyDirs() {
        return includeEmptyDirs != null ? includeEmptyDirs : true;
    }

    @Override
    public void setIncludeEmptyDirs(boolean includeEmptyDirs) {
        this.includeEmptyDirs = includeEmptyDirs;
    }

    @Override
    public DuplicatesStrategy getDuplicatesStrategy() {
        return duplicatesStrategy != null ? duplicatesStrategy : DuplicatesStrategy.INCLUDE;
    }

    @Override
    public void setDuplicatesStrategy(@Nullable DuplicatesStrategy strategy) {
        this.duplicatesStrategy = strategy;
    }

    @Override
    public CopySpec filesMatching(String pattern, Action<? super FileCopyDetails> action) {
        Spec<RelativePath> matcher = PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern);
        return eachFile(new MatchingCopyAction(matcher, action));
    }

    @Override
    public CopySpec filesMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        if (!patterns.iterator().hasNext()) {
            throw new InvalidUserDataException("must provide at least one pattern to match");
        }
        List<Spec<RelativePath>> matchers = new ArrayList<Spec<RelativePath>>();
        for (String pattern : patterns) {
            matchers.add(PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern));
        }
        return eachFile(new MatchingCopyAction(Specs.union(matchers), action));
    }

    @Override
    public CopySpec filesNotMatching(String pattern, Action<? super FileCopyDetails> action) {
        Spec<RelativePath> matcher = PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern);
        return eachFile(new MatchingCopyAction(Specs.negate(matcher), action));
    }

    @Override
    public CopySpec filesNotMatching(Iterable<String> patterns, Action<? super FileCopyDetails> action) {
        if (!patterns.iterator().hasNext()) {
            throw new InvalidUserDataException("must provide at least one pattern to not match");
        }
        List<Spec<RelativePath>> matchers = new ArrayList<Spec<RelativePath>>();
        for (String pattern : patterns) {
            matchers.add(PatternMatcherFactory.getPatternMatcher(true, isCaseSensitive(), pattern));
        }
        return eachFile(new MatchingCopyAction(Specs.negate(Specs.union(matchers)), action));
    }

    @Override
    public CopySpec include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public CopySpec include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    @Override
    public CopySpec include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public CopySpec include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    @Override
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    @Override
    public CopySpec setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    @Override
    public CopySpec exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public CopySpec exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    @Override
    public CopySpec exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public CopySpec exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    @Override
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    @Override
    public CopySpec setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    @Override
    public CopySpec rename(String sourceRegEx, String replaceWith) {
        appendCopyAction(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    @Override
    public CopySpec rename(Pattern sourceRegEx, String replaceWith) {
        appendCopyAction(new RenamingCopyAction(new RegExpNameMapper(sourceRegEx, replaceWith)));
        return this;
    }

    @Override
    public CopySpec filter(final Class<? extends FilterReader> filterType) {
        appendCopyAction(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(filterType);
            }
        });
        return this;
    }

    @Override
    public CopySpec filter(final Closure closure) {
        return filter(new ClosureBackedTransformer(closure));
    }

    @Override
    public CopySpec filter(final Transformer<String, String> transformer) {
        appendCopyAction(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(transformer);
            }
        });
        return this;
    }

    @Override
    public CopySpec filter(final Map<String, ?> properties, final Class<? extends FilterReader> filterType) {
        appendCopyAction(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.filter(properties, filterType);
            }
        });
        return this;
    }

    @Override
    public CopySpec expand(final Map<String, ?> properties) {
        appendCopyAction(new Action<FileCopyDetails>() {
            public void execute(FileCopyDetails fileCopyDetails) {
                fileCopyDetails.expand(properties);
            }
        });
        return this;
    }

    @Override
    public CopySpec rename(Closure closure) {
        return rename(new ClosureBackedTransformer(closure));
    }

    @Override
    public CopySpec rename(Transformer<String, String> renamer) {
        ChainingTransformer<String> transformer = new ChainingTransformer<String>(String.class);
        transformer.add(renamer);
        appendCopyAction(new RenamingCopyAction(transformer));
        return this;
    }

    @Override
    public Integer getDirMode() {
        return dirMode;
    }

    @Override
    public Integer getFileMode() {
        return fileMode;
    }

    @Override
    public CopyProcessingSpec setDirMode(@Nullable Integer mode) {
        dirMode = mode;
        return this;
    }

    @Override
    public CopyProcessingSpec setFileMode(@Nullable Integer mode) {
        fileMode = mode;
        return this;
    }

    @Override
    public CopySpec eachFile(Action<? super FileCopyDetails> action) {
        appendCopyAction(action);
        return this;
    }

    private void appendCopyAction(Action<? super FileCopyDetails> action) {
        hasCustomActions = true;
        copyActions.add(action);
    }

    @Override
    public void appendCachingSafeCopyAction(Action<? super FileCopyDetails> action) {
        copyActions.add(action);
    }

    @Override
    public CopySpec eachFile(Closure closure) {
        appendCopyAction(ConfigureUtil.configureUsing(closure));
        return this;
    }

    @Override
    public Iterable<CopySpecInternal> getChildren() {
        return childSpecs;
    }

    @Override
    public String getFilteringCharset() {
        return filteringCharset != null ? filteringCharset : Charset.defaultCharset().name();
    }

    @Override
    public void setFilteringCharset(String charset) {
        Preconditions.checkNotNull(charset, "filteringCharset must not be null");
        if (!Charset.isSupported(charset)) {
            throw new InvalidUserDataException(String.format("filteringCharset %s is not supported by your JVM", charset));
        }
        this.filteringCharset = charset;
    }

    private static final Iterable<Action<? super FileCopyDetails>> EMPTY_ACTIONS = Collections.emptyList();

    private Iterable<Action<? super FileCopyDetails>> internEmptyCopyActions() {
        return copyActions.isEmpty() ? EMPTY_ACTIONS : copyActions;
    }

    @Override
    public ResolvedCopySpec resolveAsRoot() {
        boolean caseSensitive = isCaseSensitive();
        PatternSet resolvedPatternSet = copyPatternSetIfNecessary(fileResolver, caseSensitive, patternSet);
        return resolve(
            RelativePath.EMPTY_ROOT,
            resolvedPatternSet,
            caseSensitive,
            getIncludeEmptyDirs(),
            getDuplicatesStrategy(),
            getFileMode(),
            getDirMode(),
            getFilteringCharset(),
            internEmptyCopyActions()
        );
    }

    @Override
    public ResolvedCopySpec resolveAsChild(
        PatternSet parentPatternSet,
        Iterable<Action<? super FileCopyDetails>> parentCopyActions,
        ResolvedCopySpec parent
    ) {
        boolean caseSensitive = this.caseSensitive != null
            ? this.caseSensitive
            : parent.isCaseSensitive();
        PatternSet resolvedPatternSet = mergePatternSetIfNecessary(fileResolver, caseSensitive, parentPatternSet, patternSet);
        Iterable<Action<? super FileCopyDetails>> resolvedCopyActions = mergeCopyActionsIfNecessary(parentCopyActions, internEmptyCopyActions());
        return resolve(
            parent.getDestPath(),
            resolvedPatternSet,
            caseSensitive,
            withDefault(includeEmptyDirs, parent.isIncludeEmptyDirs()),
            withDefault(duplicatesStrategy, parent.getDuplicatesStrategy()),
            withDefault(fileMode, parent.getFileMode()),
            withDefault(dirMode, parent.getDirMode()),
            withDefault(filteringCharset, parent.getFilteringCharset()),
            resolvedCopyActions
        );
    }

    protected static boolean withDefault(@Nullable Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    @Nullable
    protected static <T> T withDefault(@Nullable T value, @Nullable T defaultValue) {
        return value != null ? value : defaultValue;
    }

    private ResolvedCopySpec resolve(
        RelativePath parentPath,
        PatternSet patternSet,
        boolean caseSensitive,
        boolean includeEmptyDirs,
        DuplicatesStrategy duplicatesStrategy,
        @Nullable Integer fileMode,
        @Nullable Integer dirMode,
        String filteringCharset,
        Iterable<Action<? super FileCopyDetails>> copyActions
    ) {
        RelativePath resolvedPath = resolveDestPath(parentPath);

        FileTree tree = fileResolver.resolveFilesAsTree(sourcePaths);
        FileTree source;
        if (patternSet.isEmpty()) {
            source = tree;
        } else {
            source = tree.matching(patternSet);
        }
        List<ResolvedCopySpec> children = Lists.newArrayListWithCapacity(childSpecs.size());
        ResolvedCopySpec resolvedSpec = new DefaultResolvedCopySpec(
            resolvedPath,
            source,
            caseSensitive,
            includeEmptyDirs,
            duplicatesStrategy,
            fileMode,
            dirMode,
            filteringCharset,
            copyActions,
            children
        );
        for (CopySpecInternal childSpec : childSpecs) {
            children.add(childSpec.resolveAsChild(patternSet, copyActions, resolvedSpec));
        }
        return resolvedSpec;
    }

    private RelativePath resolveDestPath(RelativePath parentPath) {
        if (destDir == null) {
            return parentPath;
        }

        String path = PATH_NOTATION_PARSER.parseNotation(destDir);
        if (path.startsWith("/") || path.startsWith(File.separator)) {
            return RelativePath.parse(false, path);
        }

        return RelativePath.parse(false, parentPath, path);
    }

    @VisibleForTesting
    static PatternSet mergePatternSetIfNecessary(FileResolver fileResolver, boolean caseSensitive, PatternSet parentPatternSet, PatternSet patternSet) {
        if (patternSet.isEmpty()) {
            if (parentPatternSet.isCaseSensitive() == caseSensitive) {
                return parentPatternSet;
            }
        } else {
            if (parentPatternSet.isEmpty() && patternSet.isCaseSensitive() == caseSensitive) {
                return patternSet;
            }
        }
        PatternSet patterns = fileResolver.getPatternSetFactory().create();
        assert patterns != null;
        patterns.setCaseSensitive(caseSensitive);
        copyPatterns(parentPatternSet, patterns);
        copyPatterns(patternSet, patterns);
        return patterns;
    }

    private static PatternSet copyPatternSetIfNecessary(FileResolver fileResolver, boolean caseSensitive, PatternSet patternSet) {
        if (patternSet.isCaseSensitive() == caseSensitive) {
            return patternSet;
        }
        PatternSet patterns = fileResolver.getPatternSetFactory().create();
        assert patterns != null;
        patterns.setCaseSensitive(caseSensitive);
        copyPatterns(patternSet, patterns);
        return patterns;
    }

    private static void copyPatterns(PatternSet source, PatternSet target) {
        target.include(source.getIncludes());
        target.includeSpecs(source.getIncludeSpecs());
        target.exclude(source.getExcludes());
        target.excludeSpecs(source.getExcludeSpecs());
    }

    private static Iterable<Action<? super FileCopyDetails>> mergeCopyActionsIfNecessary(Iterable<Action<? super FileCopyDetails>> parentCopyActions, Iterable<Action<? super FileCopyDetails>> copyActions) {
        if (parentCopyActions == EMPTY_ACTIONS) {
            return copyActions;
        }
        if (copyActions == EMPTY_ACTIONS) {
            return parentCopyActions;
        }
        return Iterables.concat(parentCopyActions, copyActions);
    }
}
