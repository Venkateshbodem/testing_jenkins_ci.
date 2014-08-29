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

package org.gradle.api.internal.initialization

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.internal.Factories
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultClassLoaderScopeTest extends Specification {

    ClassLoader rootClassLoader

    ClassLoaderScope root
    ClassLoaderScope scope

    Cache<DefaultClassLoaderCache.Key, ClassLoader> backingCache = CacheBuilder.newBuilder().build()
    ClassLoaderCache classLoaderCache = new DefaultClassLoaderCache(backingCache)

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        file("root/root") << "root"
        rootClassLoader = new URLClassLoader(classPath("root").asURLArray)
        root = new RootClassLoaderScope(rootClassLoader, classLoaderCache)
        scope = root.createChild()
    }

    TestFile file(String path) {
        testDirectoryProvider.testDirectory.file(path)
    }

    ClassPath classPath(String... paths) {
        new DefaultClassPath(paths.collect { file(it).createDir() } as Iterable<File>)
    }

    def "locked scope with no modifications exports parent"() {
        when:
        scope.lock()

        then:
        scope.localClassLoader.is rootClassLoader
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with only exports with same parent only exports exports through that loader"() {
        when:
        file("export/foo") << "foo"
        def loader = root.loader(classPath("export")).create()
        scope.export(Factories.constant(loader)).lock()

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("foo").text == "foo"
        scope.exportClassLoader.is(loader)
        scope.localClassLoader.is scope.exportClassLoader
    }

    def "locked scope with only exports only exports exports"() {
        when:
        file("export/foo") << "foo"
        def loader = new URLClassLoader(classPath("export").asURLArray)
        scope.export(Factories.constant(loader)).lock()

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("foo").text == "foo"
        !scope.exportClassLoader.is(loader)
        scope.localClassLoader.is scope.exportClassLoader
    }

    def "can export more than one loader"() {
        when:
        file("export/1/foo") << "foo"
        file("export/2/bar") << "bar"
        scope.
                export(root.loader(classPath("export/1"))).
                export(root.loader(classPath("export/2"))).
                lock()

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("foo").text == "foo"
        scope.exportClassLoader.getResource("bar").text == "bar"
        scope.localClassLoader.is scope.exportClassLoader
    }

    def "locked scope with only local exports parent loader to children"() {
        when:
        file("local/1/foo") << "foo"
        file("local/2/bar") << "bar"
        scope.
                local(root.loader(classPath("local/1"))).
                local(root.loader(classPath("local/2"))).
                lock()

        then:
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("foo").text == "foo"
        scope.localClassLoader.getResource("bar").text == "bar"
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with only one local exports parent loader to children and uses loader as local loader"() {
        when:
        file("local/foo") << "foo"
        def loader = root.loader(classPath("local")).create()
        scope.local(Factories.constant(loader)).lock()

        then:
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("foo").text == "foo"
        scope.localClassLoader.is(loader)
        scope.exportClassLoader.is rootClassLoader
    }

    def "can have more than one local loader"() {
        when:
        file("local/1/foo") << "foo"
        file("local/2/bar") << "bar"
        scope.
                local(root.loader(classPath("local/1"))).
                local(root.loader(classPath("local/2"))).
                lock()

        then:
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("foo").text == "foo"
        scope.localClassLoader.getResource("bar").text == "bar"
        scope.exportClassLoader.is rootClassLoader
    }

    def "locked scope with local and exports exports custom classloader to children"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        def exportLoader = root.loader(classPath("export")).create()
        scope.
                local(root.loader(classPath("local"))).
                export(Factories.constant(exportLoader)).
                lock()

        then:
        scope.exportClassLoader.is(exportLoader)
        scope.exportClassLoader.getResource("export").text == "bar"
        scope.exportClassLoader.getResource("local") == null
        scope.localClassLoader instanceof CachingClassLoader
        scope.localClassLoader.getResource("export").text == "bar"
        scope.localClassLoader.getResource("local").text == "bar"
    }

    def "single classloaders are reused if both parents collapse"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        def exportLoader = root.loader(classPath("export")).create()
        def localLoader = new URLClassLoader(classPath("local").asURLArray, exportLoader)

        scope.
                local(Factories.constant(localLoader)).
                export(Factories.constant(exportLoader)).
                lock()

        then:
        scope.exportClassLoader.is(exportLoader)
        scope.exportClassLoader.getResource("export").text == "bar"
        scope.exportClassLoader.getResource("local") == null
        scope.localClassLoader.is(localLoader)
        scope.localClassLoader.getResource("export").text == "bar"
        scope.localClassLoader.getResource("local").text == "bar"
    }

    def "requesting loaders before locking creates pessimistic setup"() {
        given:
        scope.localClassLoader // trigger

        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.
                local(root.loader(classPath("local"))).
                export(root.loader(classPath("export")))

        then:
        scope.exportClassLoader.getResource("root").text == "root"
        scope.exportClassLoader.getResource("export").text == "bar"
        scope.exportClassLoader.getResource("local") == null
        scope.localClassLoader instanceof CachingClassLoader
        scope.localClassLoader.getResource("root").text == "root"
        scope.localClassLoader.getResource("export").text == "bar"
        scope.localClassLoader.getResource("local").text == "bar"
    }

    def "cannot modify after locking"() {
        given:
        scope.lock()

        when:
        scope.local(root.loader(classPath("local")))

        then:
        thrown IllegalStateException

        when:
        scope.export(root.loader(classPath("local")))

        then:
        thrown IllegalStateException
    }

    def "child scopes can access exported but not local"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.local(root.loader(classPath("local")))
        scope.export(root.loader(classPath("export")))
        def child = scope.lock().createChild().lock()

        then:
        child.localClassLoader.getResource("root").text == "root"
        child.localClassLoader.getResource("export").text == "bar"
        child.localClassLoader.getResource("local") == null
        child.exportClassLoader.getResource("root").text == "root"
        child.exportClassLoader.getResource("export").text == "bar"
        child.exportClassLoader.getResource("local") == null
    }

    def "sibling scopes can not access exported or local"() {
        when:
        file("local/local") << "bar"
        file("export/export") << "bar"
        scope.local(root.loader(classPath("local")))
        scope.export(root.loader(classPath("export")))
        def sibling = scope.lock().createSibling().lock()

        then:
        sibling.localClassLoader.getResource("root").text == "root"
        sibling.localClassLoader.getResource("export") == null
        sibling.localClassLoader.getResource("local") == null
        sibling.exportClassLoader.getResource("root").text == "root"
        sibling.exportClassLoader.getResource("export") == null
        sibling.exportClassLoader.getResource("local") == null
    }

    def "class loaders are reused"() {
        expect:
        backingCache.size() == 0

        when:
        def c1 = classPath("c1")
        def c2 = classPath("c2")
        def c1LoaderRoot = root.loader(c1)
        def c2LoaderRoot = root.loader(c2)
        scope.export(c1LoaderRoot).local(c2LoaderRoot).lock()

        def sibling = scope.createSibling().lock()
        def child = scope.createChild().lock()

        scope.localClassLoader
        sibling.localClassLoader
        child.localClassLoader

        then:
        backingCache.size() == 2
        sibling.loader(c1).create().is c1LoaderRoot.create()
        backingCache.size() == 2

        !child.loader(c1).create().is(c1LoaderRoot.create()) // classpath is the same, but root is different
        backingCache.size() == 3
    }

    def "pessimistic structure has parent visibility"() {
        expect:
        scope.localClassLoader.getResource("root").text == "root"
    }

    def "optimised structure has parent visibility"() {
        expect:
        scope.lock().localClassLoader.getResource("root").text == "root"
    }

}
