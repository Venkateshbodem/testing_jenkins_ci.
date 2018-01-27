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

package org.gradle.integtests.samples

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

import static org.gradle.util.TextUtil.normaliseFileSeparators

class SamplesWorkingWithDependenciesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/dependencies/iteratingDependencies")
    def "can iterate over dependencies assigned to a configuration"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('iterateScmDependencies')

        then:
        outputContains("""org.eclipse.jgit:org.eclipse.jgit:4.9.2.201712150930-r
commons-codec:commons-codec:1.7""")
    }

    @UsesSample("userguide/dependencies/iteratingArtifacts")
    def "can iterate over artifacts resolved for a module"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('iterateScmArtifacts')

        then:
        def normalizedContent = normaliseFileSeparators(output)
        normalizedContent.contains('org.eclipse.jgit/org.eclipse.jgit/4.9.2.201712150930-r/a3a2d1df793245ebfc7322db3c2b9828ee184850/org.eclipse.jgit-4.9.2.201712150930-r.jar')
        normalizedContent.contains('org.apache.httpcomponents/httpclient/4.3.6/4c47155e3e6c9a41a28db36680b828ced53b8af4/httpclient-4.3.6.jar')
        normalizedContent.contains('commons-codec/commons-codec/1.7/9cd61d269c88f9fb0eb36cea1efcd596ab74772f/commons-codec-1.7.jar')
        normalizedContent.contains('com.jcraft/jsch/0.1.54/da3584329a263616e277e15462b387addd1b208d/jsch-0.1.54.jar')
        normalizedContent.contains('com.googlecode.javaewah/JavaEWAH/1.1.6/94ad16d728b374d65bd897625f3fbb3da223a2b6/JavaEWAH-1.1.6.jar')
        normalizedContent.contains('org.slf4j/slf4j-api/1.7.2/81d61b7f33ebeab314e07de0cc596f8e858d97/slf4j-api-1.7.2.jar')
        normalizedContent.contains('org.apache.httpcomponents/httpcore/4.3.3/f91b7a4aadc5cf486df6e4634748d7dd7a73f06d/httpcore-4.3.3.jar')
        normalizedContent.contains('commons-logging/commons-logging/1.1.3/f6f66e966c70a83ffbdb6f17a0919eaf7c8aca7f/commons-logging-1.1.3.jar')
    }

    @UsesSample("userguide/dependencies/accessingMetadataArtifact")
    def "can accessing a module's metadata artifact"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds('printGuavaMetadata')

        then:
        def normalizedContent = normaliseFileSeparators(output)
        normalizedContent.contains("""com.google.guava/guava/18.0/2ec12f8d27a64e970b8be0fbd1d52dfec51cd41c/guava-18.0.pom
Guava: Google Core Libraries for Java

    Guava is a suite of core and expanded libraries that include
    utility classes, google's collections, io classes, and much
    much more.

    Guava has only one code dependency - javax.annotation,
    per the JSR-305 spec.
""")
    }
}
