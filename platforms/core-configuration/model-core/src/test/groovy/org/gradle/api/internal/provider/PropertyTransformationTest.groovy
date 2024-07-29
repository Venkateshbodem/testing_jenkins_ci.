/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.provider

import spock.lang.Specification

class PropertyTransformationTest extends Specification {

    def 'can add transformation over set value'() {
        given:
        def property = propertyWithDefaultValue(String)

        and:
        def transformed = property.addTransformation {
            it.reverse()
        }

        when:
        property.set('foobar')

        then:
        operation(property) == 'raboof'

        and:
        transformed === property

        where:
        description | operation
        'get'       | { it.get() }
        'getOrNull' | { it.getOrNull() }
        'map'       | { it.map { it }.get() }
    }

    def 'can add transformation over convention'() {
        given:
        def property = propertyWithDefaultValue(String)

        and:
        property.addTransformation {
            it.reverse()
        }

        when:
        property.convention('foobar')

        then:
        property.get() == 'raboof'
    }

    def 'can add transformation to unset property'() {
        given:
        def property = propertyWithDefaultValue(String)

        when:
        property.addTransformation {
            it.reverse()
        }

        then:
        property.getOrNull() == null
    }

    <T> DefaultProperty<T> propertyWithDefaultValue(Class<T> type) {
        return new DefaultProperty(host, type)
    }

    def host = Mock(PropertyHost)

}
