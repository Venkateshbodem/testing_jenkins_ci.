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

package org.gradle.runtime.base.component

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.base.internal.DefaultFunctionalSourceSet
import org.gradle.runtime.base.ComponentSpecIdentifier
import org.gradle.runtime.base.ModelInstantiationException
import spock.lang.Specification

class DefaultComponentSpecTest extends Specification {
    def instantiator = new DirectInstantiator()
    def componentId = Mock(ComponentSpecIdentifier)
    FunctionalSourceSet functionalSourceSet;
    def setup(){
        functionalSourceSet = new DefaultFunctionalSourceSet("testFSS", new DirectInstantiator());
    }

    def "library has name and path"() {
        def component = DefaultComponentSpec.create(DefaultComponentSpec, componentId, functionalSourceSet, instantiator)

        when:
        _ * componentId.name >> "jvm-lib"
        _ * componentId.projectPath >> ":project-path"

        then:
        component.name == "jvm-lib"
        component.projectPath == ":project-path"
        component.displayName == "DefaultComponentSpec 'jvm-lib'"
    }

    def "has sensible display name"() {
        def component = DefaultComponentSpec.create(MySampleComponent, componentId, functionalSourceSet, instantiator)

        when:
        _ * componentId.name >> "jvm-lib"

        then:
        component.displayName == "MySampleComponent 'jvm-lib'"
    }

    def "create fails if subtype does not have a public no-args constructor"() {

        when:
        DefaultComponentSpec.create(MyConstructedComponent, componentId, functionalSourceSet, instantiator)

        then:
        def e = thrown ModelInstantiationException
        e.message == "Could not create component of type MyConstructedComponent"
        e.cause instanceof IllegalArgumentException
        e.cause.message.startsWith "Could not find any public constructor for class"
    }

    def "contains sources of associated mainsourceSet"() {

        when:
        DefaultComponentSpec.create(MyConstructedComponent, componentId, functionalSourceSet, instantiator)

        then:
        def e = thrown ModelInstantiationException
        e.message == "Could not create component of type MyConstructedComponent"
        e.cause instanceof IllegalArgumentException
        e.cause.message.startsWith "Could not find any public constructor for class"
    }

    static class MySampleComponent extends DefaultComponentSpec {}
    static class MyConstructedComponent extends DefaultComponentSpec {
        MyConstructedComponent(String arg) {}
    }
}
