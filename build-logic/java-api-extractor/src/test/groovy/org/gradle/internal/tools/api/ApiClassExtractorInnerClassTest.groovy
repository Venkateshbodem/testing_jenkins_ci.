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

package org.gradle.internal.tools.api

import org.objectweb.asm.Opcodes

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

class ApiClassExtractorInnerClassTest extends ApiClassExtractorTestSupport {

    private final static int ACC_PUBLICSTATIC = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
    private final static int ACC_PROTECTEDSTATIC = Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC

    def "should not remove #modifier inner class if no API is declared"() {
        given:
        def api = toApi 'A': """
            public class A {
               $modifier class Inner {
                  public void foo() {}
               }
            }
        """

        when:
        def outer = api.classes.A
        def inner = api.classes['A$Inner']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)
        def extractedInner = api.extractAndLoadApiClassFrom(inner)

        then:
        inner.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        extractedInner.modifiers == access
        hasMethod(extractedInner, 'foo').modifiers == Modifier.PUBLIC

        when:
        def o = (modifier =~ /static/) ? createInstance(extractedInner) : extractedInner.newInstance(createInstance(extractedOuter))
        o.foo()

        then:
        def e = thrown(Exception)
        e.cause instanceof Error

        where:
        modifier           | access
        'public'           | Opcodes.ACC_PUBLIC
        'protected'        | Opcodes.ACC_PROTECTED
        ''                 | 0
        'public static'    | ACC_PUBLICSTATIC
        'protected static' | ACC_PROTECTEDSTATIC
        'static'           | Opcodes.ACC_STATIC
    }

    def "should not keep private #modifier inner class"() {
        given:
        def api = toApi 'A': """
            public class A {
               private $modifier class Inner {
                  public void foo() {}
               }
            }
        """

        when:
        def outer = api.classes.A
        def inner = api.classes['A$Inner']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)

        then:
        !api.isApiClassExtractedFrom(inner)
        extractedOuter.classes.length == 0

        where:
        modifier | _
        ''       | _
        'static' | _
    }

    def "should not keep #modifier inner class if API is declared"() {
        given:
        def api = toApi([''], ['A': """
            public class A {
               $modifier class Inner {
                  public void foo() {}
               }
            }
        """
        ])

        when:
        def outer = api.classes.A
        def inner = api.classes['A$Inner']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)

        then:
        !api.isApiClassExtractedFrom(inner)
        inner.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        extractedOuter.classes.length == 0

        where:
        modifier | access
        ''       | 0
        'static' | Opcodes.ACC_STATIC
    }

    def "should not keep anonymous inner classes"() {
        given:
        def api = toApi 'A': '''
            public class A {
               public void foo() {
                   Runnable r = new Runnable() {
                      public void run() {}
                   };
               }
            }
        '''

        when:
        def outer = api.classes.A
        def inner = api.classes['A$1']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)

        then:
        !api.isApiClassExtractedFrom(inner)
        extractedOuter.classes.length == 0
    }

    def "should not keep anonymous local classes"() {
        given:
        def api = toApi 'A': '''
            public class A {
               public void foo() {
                   class Person {}
               }
            }
        '''

        when:
        def outer = api.classes.A
        def inner = api.classes['A$1Person']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)

        then:
        !api.isApiClassExtractedFrom(inner)
        extractedOuter.classes.length == 0
    }

    def "properly extracts annotations from inner class constructor"() {
        given:
        def api = toApi([
            'Outer': '''
                public class Outer<T> {
                   public class Inner<I extends T> {
                       public Inner(String name, Class<I> type, @Nullable java.util.function.Consumer<? super I> configureAction) {
                           // Constructor
                       }
                   }
                }
            ''',
            'Nullable': '''
                @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                public @interface Nullable {}
            '''
        ])

        when:
        def outer = api.classes.Outer
        def inner = api.classes['Outer$Inner']
        def extractedOuter = api.extractAndLoadApiClassFrom(outer)
        def extractedInner = api.extractAndLoadApiClassFrom(inner)

        writeClass(api, "Outer", outer)
        writeClass(api, "Outer\$Inner", inner)

        then:
        inner.clazz.getDeclaredConstructor(outer.clazz, String, Class, Consumer).getParameterAnnotations().eachWithIndex { annotations, idx ->
            println "$idx: ${annotations.collect { it.annotationType().name }}"
        }
        inner.clazz.getDeclaredConstructor(outer.clazz, String, Class, Consumer).getParameterAnnotations().length == 4
        extractedInner.getDeclaredConstructor(extractedOuter, String, Class, Consumer).getParameterAnnotations().length == 4
    }

    private void writeClass(ApiContainer api, String name, GeneratedClass generatedClass) {
        def bytes = api.extractApiClassFrom(generatedClass)
        def classFile = Path.of("${name}.class")
        Files.write(classFile, bytes)
        println "- ${classFile.toAbsolutePath()}"
    }

}
