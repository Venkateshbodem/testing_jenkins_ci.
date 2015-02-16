/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.groovy.scripts.internal

import org.gradle.api.internal.initialization.loadercache.DummyClassLoaderCache
import org.gradle.api.internal.project.ProjectScript
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.internal.Actions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildScriptMetadataExtractingTransformerSpec extends Specification {

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    final DefaultScriptCompilationHandler scriptCompilationHandler = new DefaultScriptCompilationHandler(new AsmBackedEmptyScriptGenerator(), new DummyClassLoaderCache())
    final String classpathClosureName = "buildscript"

    File scriptCacheDir
    File metadataCacheDir

    def setup() {
        File testProjectDir = tmpDir.createDir("projectDir");
        scriptCacheDir = new File(testProjectDir, "cache");
        metadataCacheDir = new File(testProjectDir, "metadata");
    }

    private boolean containsImperativeStatements(String script) {
        def source = new StringScriptSource("test script", script)
        def loader = getClass().getClassLoader()

        PluginsAndBuildscriptTransformer scriptBlockTransformer = new PluginsAndBuildscriptTransformer(classpathClosureName, null, source, null);

        StatementFilteringScriptTransformer classpathScriptTransformer = new StatementFilteringScriptTransformer(classpathClosureName, scriptBlockTransformer);

        def transformer = new BuildScriptMetadataExtractingTransformer("no_" + classpathScriptTransformer.getId(), classpathScriptTransformer.invert(), classpathClosureName, source);

        scriptCompilationHandler.compileToDir(source, loader, scriptCacheDir, metadataCacheDir, transformer, classpathClosureName, ProjectScript, Actions.doNothing())
        scriptCompilationHandler.loadFromDir(source, loader, scriptCacheDir, metadataCacheDir, transformer, ProjectScript).metadata
    }

    def "empty script does not contain imperative code"() {
        expect:
        !containsImperativeStatements("")
        !containsImperativeStatements("//ignore me")
    }

    def "class, method and property declarations are not considered imperative code"() {
        expect:
        !containsImperativeStatements("""
            class SomeClass {}
            String a
        """)
    }

    def "non-imperative script blocks are not considered imperative code"() {
        expect:
        !containsImperativeStatements("plugins {}; buildscript {}; model {}")
    }

    def "method declarations are considered imperative code"() {
        expect:
        containsImperativeStatements("def method() { println 'hi' }")
    }

    def "imperative code is detected"() {
        expect:
        containsImperativeStatements("foo = 'bar'")
        containsImperativeStatements("foo")
        containsImperativeStatements("println 'hi!'")
    }
}
