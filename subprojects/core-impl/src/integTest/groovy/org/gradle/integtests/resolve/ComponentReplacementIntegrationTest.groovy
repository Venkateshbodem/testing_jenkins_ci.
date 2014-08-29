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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestDependency

class ComponentReplacementIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            task resolvedFiles(type: Copy) {
                from configurations.conf
                into 'resolved-files'
                dependsOn 'dependencies'
                doLast {
                    println "All files:"
                    configurations.conf.each { println it.name }
                }
            }
        """
    }

    //publishes and declares the dependencies
    void declaredDependencies(String ... deps) {
        publishedMavenModules(deps)
        def content = ''
        deps.each {
            content += "dependencies.conf '${new TestDependency(it).notation}'\n"
        }
        buildFile << """
            $content
        """
    }

    void declaredReplacements(String ... reps) {
        def content = ''
        reps.each {
            def d = new TestDependency(it)
            content +=  "dependencies.components.module('${d.group}:${d.name}').replacedBy '${d.pointsTo.group}:${d.pointsTo.name}'\n"
        }
        buildFile << """
            $content
        """
    }

    void resolvedFiles(String ... files) {
        run("resolvedFiles")
        assert file('resolved-files').listFiles()*.name as Set == files as Set
    }

    void resolvedModules(String ... modules) {
        resolvedFiles(modules.collect { new TestDependency(it).jarName } as String[])
    }

    def "ignores replacement if not in graph"() {
        declaredDependencies 'a'
        declaredReplacements 'a->b'
        expect: resolvedModules 'a'
    }

    def "ignores replacement if org does not match"() {
        declaredDependencies 'a', 'com:b'
        declaredReplacements 'a->org:b'
        expect: resolvedModules 'a', 'com:b'
    }

    def "just uses replacement if source not in graph"() {
        declaredDependencies 'b'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b'
    }

    def "replaces already resolved module"() {
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b'
    }

    def "replaces not yet resolved module"() {
        declaredDependencies 'b', 'a'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b'
    }

    def "uses highest when it is last"() {
        declaredDependencies 'b', 'a', 'b:2'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b:2'
    }

    def "uses highest when it is last following replacedBy"() {
        declaredDependencies 'a', 'b', 'b:2'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b:2'
    }

    def "uses highest when it is first"() {
        declaredDependencies 'b:2', 'b', 'a'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b:2'
    }

    def "uses highest when it is first followed by replacedBy"() {
        declaredDependencies 'b:2', 'b', 'a'
        declaredReplacements 'a->b'
        expect: resolvedModules 'b:2'
    }

    def "evicts transitive dependencies of replaced module"() {
        declaredDependencies 'a', 'c'
        declaredReplacements 'a->e'
        //resolution sequence: a,c,b,d,e!
        publishedMavenModules 'a->b', 'c->d', 'd->e'
        expect: resolvedModules 'c', 'd', 'e' //'b' is evicted
    }

    def "replaces transitive module"() {
        declaredDependencies 'a', 'c'
        declaredReplacements 'b->d'
        publishedMavenModules 'a->b', 'c->d'
        expect: resolvedModules 'a', 'd', 'c'
    }

    def "replaces module even if it was already conflict-resolved"() {
        declaredDependencies 'a:1', 'a:2'
        declaredReplacements 'a->c'
        //resolution sequence: a1,a2,!,b,c,!
        publishedMavenModules 'a:2->b', 'b->c'
        expect: resolvedModules 'c'
    }

    def "uses already resolved highest version"() {
        declaredDependencies 'a:1', 'a:2'
        declaredReplacements 'c->a'
        //resolution sequence: a1,a2,!,b,c,!
        publishedMavenModules 'a:2->b', 'b->c'
        expect: resolvedModules 'a:2', 'b'
    }

    def "latest replacement wins"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'a->c' //2 replacements for the same source module
        expect: resolvedModules 'c', 'b'
    }

    def "supports consecutive replacements"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'b->c'
        expect: resolvedModules 'c'
    }

    //TODO SF when forced
    //when resolve target is unresolved, check exception
}
