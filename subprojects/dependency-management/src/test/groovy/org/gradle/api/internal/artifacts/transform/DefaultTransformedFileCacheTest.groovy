/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import com.google.common.hash.HashCode
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.caching.internal.BuildCacheHasher
import org.gradle.internal.util.BiFunction
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.UsesNativeServices
import org.junit.Rule

@UsesNativeServices
class DefaultTransformedFileCacheTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def artifactCacheMetaData = Mock(ArtifactCacheMetaData)
    def snapshotter = Mock(GenericFileCollectionSnapshotter)
    def scopeMapping = Stub(CacheScopeMapping)
    def cacheRepo = new DefaultCacheRepository(scopeMapping, new InMemoryCacheFactory())
    def decorator = Stub(InMemoryCacheDecoratorFactory)
    def cache

    def setup() {
        scopeMapping.getBaseDirectory(_, _, _) >> tmpDir.testDirectory
        scopeMapping.getRootDirectory(_) >> tmpDir.testDirectory
        artifactCacheMetaData.transformsStoreDirectory >> tmpDir.file("output")
        cache = new DefaultTransformedFileCache(artifactCacheMetaData, snapshotter, cacheRepo, decorator)
    }

    def "reuses result for given file and transform"() {
        def transform = Mock(BiFunction)

        when:
        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        def result = cachingTransform.transform(new File("a"))

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._

        when:
        def result2 = cachingTransform.transform(new File("a"))

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        0 * transform._
    }

    def "reuses result when transform returns input file"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a").createFile()

        when:
        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        def result = cachingTransform.transform(inputFile)

        then:
        result == [inputFile]

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> [file] }
        0 * transform._

        when:
        def result2 = cachingTransform.transform(inputFile)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        0 * transform._
    }

    def "applies transform once when requested concurrently by multiple threads"() {
        def transform = Mock(BiFunction)

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result1 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result2 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result3 = cachingTransform.transform(new File("a"))
            }
            start {
                def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
                result4 = cachingTransform.transform(new File("a"))
            }
        }

        then:
        result1*.name == ["a.1"]
        result2 == result1
        result3 == result1
        result4 == result1

        and:
        4 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._
    }

    def "multiple threads can transform files concurrently"() {
        def snapshot1 = Stub(FileCollectionSnapshot)
        def snapshot2 = Stub(FileCollectionSnapshot)
        _ * snapshot1.appendToHasher(_) >> { BuildCacheHasher hasher -> hasher.putString("first file snapshot") }
        _ * snapshot2.appendToHasher(_) >> { BuildCacheHasher hasher -> hasher.putString("second file snapshot") }

        when:
        def transform = cache.applyCaching(HashCode.fromInt(123)) { file, outDir ->
            instant."$file.name"
            thread.block()
            instant."${file.name}_done"
            [file]
        }
        async {
            start {
                transform.transform(new File("a"))
            }
            start {
                transform.transform(new File("b"))
            }
        }

        then:
        instant.a_done > instant.b
        instant.b_done > instant.a

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot1
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot2
    }

    def "does not reuse result when file snapshot hash is different"() {
        def transform = Mock(BiFunction)
        def snapshot1 = Stub(FileCollectionSnapshot)
        def snapshot2 = Stub(FileCollectionSnapshot)

        given:
        _ * snapshot1.appendToHasher(_) >> { BuildCacheHasher hasher -> hasher.putString("first file snapshot") }
        _ * snapshot2.appendToHasher(_) >> { BuildCacheHasher hasher -> hasher.putString("second file snapshot") }

        _ * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot1
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }

        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        cachingTransform.transform(new File("a"))

        when:
        def result = cachingTransform.transform(new File("a"))

        then:
        result*.name == ["b.1"]

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot2
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "b.1"); r.text = "result"; [r] }
        0 * transform._

        when:
        def result2 = cachingTransform.transform(new File("a"))
        def result3 = cachingTransform.transform(new File("a"))

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot1
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> snapshot2
        0 * transform._
    }

    def "does not reuse result when transform inputs are different"() {
        def transform1 = Mock(BiFunction)
        def transform2 = Mock(BiFunction)

        given:
        _ * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        _ * transform1.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }

        cache.applyCaching(HashCode.fromInt(123), transform1).transform(new File("a"))

        when:
        def result = cache.applyCaching(HashCode.fromInt(234), transform2).transform(new File("a"))

        then:
        result*.name == ["a.2"]

        and:
        _ * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform2.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._

        when:
        def result2 = cache.applyCaching(HashCode.fromInt(123), transform1).transform(new File("a"))
        def result3 = cache.applyCaching(HashCode.fromInt(234), transform2).transform(new File("a"))

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        0 * transform1._
        0 * transform2._
    }

    def "runs transform when previous execution failed and cleans up directory"() {
        def transform = Mock(BiFunction)
        def failure = new RuntimeException()

        when:
        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        cachingTransform.transform(new File("a"))

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir ->
            dir.mkdirs()
            new File(dir, "delete-me").text = "broken"
            throw failure
        }
        0 * transform._

        when:
        def result = cachingTransform.transform(new File("a"))

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir ->
            assert dir.list().length == 0
            def r = new File(dir, "a.1")
            r.text = "result"
            [r]
        }
        0 * transform._
    }

    def "runs transform when output has been removed"() {
        def transform = Mock(BiFunction)

        when:
        def cachingTransform = cache.applyCaching(HashCode.fromInt(123), transform)
        def result = cachingTransform.transform(new File("a"))

        then:
        result.size() == 1
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._

        when:
        result.first().delete()
        def result2 = cachingTransform.transform(new File("a"))

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(_, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE) >> Stub(FileCollectionSnapshot)
        1 * transform.apply(new File("a").absoluteFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        0 * transform._
    }

}
