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

package org.gradle.internal.resource.transport.aws.s3

import software.amazon.awssdk.services.s3.model.CommonPrefix
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Object
import spock.lang.Specification

class S3ResourceResolverTest extends Specification {

    def "should resolve file names"() {
        setup:
        S3Object objectSummary = S3Object
            .builder()
            .key('/SNAPSHOT/some.jar')
            .build();

        S3Object objectSummary2 = S3Object
            .builder()
            .key('/SNAPSHOT/someOther.jar')
            .build();

        CommonPrefix commonPrefix = CommonPrefix
            .builder()
            .prefix('root/SNAPSHOT')
            .build();

        ListObjectsV2Response objectListing = ListObjectsV2Response
            .builder()
            .prefix('root/')
            .contents([objectSummary, objectSummary2])
            .commonPrefixes([commonPrefix])
            .build();

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == ['some.jar', 'someOther.jar', 'SNAPSHOT']
    }

    def "should clean common prefixes"() {
        setup:
        S3Object objectSummary = S3Object
            .builder()
            .key('/SNAPSHOT/some.jar')
            .build();

        ListObjectsV2Response objectListing = ListObjectsV2Response
            .builder()
            .prefix('root/')
            .contents([objectSummary])
            .commonPrefixes([CommonPrefix.builder().prefix(prefix).build()])
            .build();

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == ['some.jar', expected]

        where:
        prefix           | expected
        'root/SNAPSHOT'  | 'SNAPSHOT'
        'root/SNAPSHOT/' | 'SNAPSHOT'
    }

    def "should extract file name from s3 listing"() {
        setup:
        S3Object objectSummary = S3Object
            .builder()
            .key(listing)
            .build();

        ListObjectsV2Response objectListing = ListObjectsV2Response
            .builder()
            .contents([objectSummary])
            .build();

        S3ResourceResolver resolver = new S3ResourceResolver()

        when:
        def results = resolver.resolveResourceNames(objectListing)

        then:
        results == expected

        where:
        listing         | expected
        '/a/b/file.pom' | ['file.pom']
        '/file.pom'     | ['file.pom']
        '/file.pom'     | ['file.pom']
        '/SNAPSHOT/'    | []
        '/SNAPSHOT/bin' | []
        '/'             | []
    }


}
