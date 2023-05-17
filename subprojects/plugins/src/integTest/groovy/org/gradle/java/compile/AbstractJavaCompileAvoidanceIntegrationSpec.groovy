/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java.compile

import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.language.fixtures.HelperProcessorFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

abstract class AbstractJavaCompileAvoidanceIntegrationSpec extends AbstractJavaGroovyCompileAvoidanceIntegrationSpec {
    CompiledLanguage language = CompiledLanguage.JAVA

    def "doesn't recompile when private inner class changes"() {
        given:
        // Groovy doesn't produce real private inner classes - the generated bytecode has no ACC_PRIVATE
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {
                private class Thing { }
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // ABI change of inner class
        sourceFile.text = """
            public class ToolImpl {
                private class Thing {
                    public long v;
                }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Remove inner class
        sourceFile.text = """
            public class ToolImpl {
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Anonymous class
        sourceFile.text = """
            public class ToolImpl {
                private Object r = new Runnable() { public void run() { } };
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"

        when:
        // Add classes
        sourceFile.text = """
            public class ToolImpl {
                private Object r = new Runnable() {
                    public void run() {
                        class LocalThing { }
                    }
                };
                private static class C1 {
                }
                private class C2 {
                    public void go() {
                        Object r2 = new Runnable() { public void run() { } };
                    }
                }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"
    }

    def "recompiles source when annotation processor implementation on annotation processor classpath changes"() {
        given:
        settingsFile << "include 'c'"

        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
            project(':c') {
                configurations {
                    processor
                }
                dependencies {
                    implementation project(':a')
                    processor project(':b')
                }
                ${language.compileTaskName}.options.annotationProcessorPath = configurations.processor
                task run(type: JavaExec) {
                    mainClass = 'TestApp'
                    classpath = sourceSets.main.runtimeClasspath
                }
            }
        """

        def fixture = new HelperProcessorFixture()

        // The annotation
        fixture.writeApiTo(file("a"))

        // The processor and library
        fixture.writeSupportLibraryTo(file("b"))
        fixture.writeAnnotationProcessorTo(file("b"))

        // The class that is the target of the processor
        file("c/src/main/${language.name}/TestApp.${language.name}") << '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                    System.out.println(new TestAppHelper().getValue()); // generated class
                }
            }
        '''

        when:
        run(':c:run')

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('greetings')

        when:
        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")
        outputContains('greetings')

        when:
        // Update the library class
        fixture.message = 'hello'
        fixture.writeSupportLibraryTo(file("b"))

        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('hello')

        when:
        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")
        outputContains('hello')

        when:
        // Update the processor class
        fixture.suffix = 'world'
        fixture.writeAnnotationProcessorTo(file("b"))

        run(':c:run')

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")
        outputContains('hello world')
    }

    def "ignores annotation processor implementation when included in the compile classpath but annotation processing is disabled"() {
        given:
        settingsFile << "include 'c'"

        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
            project(':c') {
                dependencies {
                    implementation project(':b')
                }
                ${language.compileTaskName}.options.annotationProcessorPath = files()
            }
        """

        def fixture = new HelperProcessorFixture()

        fixture.writeSupportLibraryTo(file("a"))
        fixture.writeApiTo(file("b"))
        fixture.writeAnnotationProcessorTo(file("b"))

        file("c/src/main/${language.name}/TestApp.${language.name}") << '''
            @Helper
            class TestApp {
                public static void main(String[] args) {
                }
            }
        '''

        when:
        run(":c:${language.compileTaskName}")

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        executedAndNotSkipped(":c:${language.compileTaskName}")

        when:
        run(":c:${language.compileTaskName}")

        then:
        skipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")

        when:
        // Update the library class
        fixture.message = 'hello'
        fixture.writeSupportLibraryTo(file("a"))

        run(":c:${language.compileTaskName}")

        then:
        executedAndNotSkipped(":a:${language.compileTaskName}")
        skipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")

        when:
        // Update the processor class
        fixture.suffix = 'world'
        fixture.writeAnnotationProcessorTo(file("b"))

        run(":c:${language.compileTaskName}")

        then:
        skipped(":a:${language.compileTaskName}")
        executedAndNotSkipped(":b:${language.compileTaskName}")
        skipped(":c:${language.compileTaskName}")
    }

    // Note: In Groovy the generated constructor is not the same anymore as the empty one (it's annotated now)
    def "doesn't recompile when empty initializer, static initializer or constructor is added"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """
        def sourceFile = file("a/src/main/${language.name}/ToolImpl.${language.name}")
        sourceFile << """
            public class ToolImpl {
                public Object s = String.valueOf(12);
                public void execute() { int i = 12; }
            }
        """
        file("b/src/main/${language.name}/Main.${language.name}") << """
            public class Main { ToolImpl t = new ToolImpl(); }
        """

        when:
        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped ":a:${language.compileTaskName}"
        executedAndNotSkipped ":b:${language.compileTaskName}"

        when:
        // add empty initializer, static initializer and constructor
        sourceFile.text = """
            public class ToolImpl {
                {}
                static {}
                public ToolImpl() {}
                public Object s = "12";
                public void execute() { String s = toString(); }
            }
        """

        then:
        succeeds ":b:${language.compileTaskName}"
        executedAndNotSkipped ":a:${language.compileTaskName}"
        skipped ":b:${language.compileTaskName}"
    }

    @Issue("https://github.com/gradle/gradle/issues/20394")
    @Requires(UnitTestPreconditions.Jdk16OrLater)
    def "doesn't recompile when record implementation changes"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """

        file("a/src/main/${language.name}/Foo.${language.name}") << 'record Foo(String property) {}'
        file("b/src/main/${language.name}/Bar.${language.name}") << 'class Bar { public void useFoo(Foo foo) { } }'

        when:
        // Run with --debug to ensure that class snapshotting didn't fail.
        succeeds ":b:${language.compileTaskName}", "--debug"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")

        when:
        // Change internal implementation, but not API.
        file("a/src/main/${language.name}/Foo.${language.name}").delete()
        file("a/src/main/${language.name}/Foo.${language.name}") << '''
record Foo(String property) {
    public String property() {
        return property + "!";
    }
}
'''

        succeeds ":b:${language.compileTaskName}"

        then:
        skipped(":b:${language.compileTaskName}")
    }

    @Issue("https://github.com/gradle/gradle/issues/20394")
    @Requires(UnitTestPreconditions.Jdk16OrLater)
    def "recompiles when record components change"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """

        file("a/src/main/${language.name}/Foo.${language.name}") << 'record Foo(String property) {}'
        file("b/src/main/${language.name}/Bar.${language.name}") << 'class Bar { public void useFoo(Foo foo) { } }'

        when:
        // Run with --debug to ensure that class snapshotting didn't fail.
        succeeds ":b:${language.compileTaskName}", "--debug"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")

        when:
        // Add new property, breaks API.
        file("a/src/main/${language.name}/Foo.${language.name}").delete()
        file("a/src/main/${language.name}/Foo.${language.name}") << '''
record Foo(String property, int newProperty) {
    public String property() {
        return property + "!";
    }
}
'''

        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")
    }

    @Issue("https://github.com/gradle/gradle/issues/20394")
    @Requires(UnitTestPreconditions.Jdk17OrLater)
    def "recompiles when sealed modifier is changed"() {
        given:
        buildFile << """
            project(':b') {
                dependencies {
                    implementation project(':a')
                }
            }
        """

        file("a/src/main/${language.name}/Foo.${language.name}") << '''
sealed interface Foo {
    non-sealed interface Sub extends Foo {}
}
'''
        file("b/src/main/${language.name}/Bar.${language.name}") << 'class Bar implements Foo.Sub {}'

        when:
        // Run with --debug to ensure that class snapshotting didn't fail.
        succeeds ":b:${language.compileTaskName}", "--debug"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")

        when:
        // Remove sealed modifier, forces change to API.
        file("a/src/main/${language.name}/Foo.${language.name}").delete()
        file("a/src/main/${language.name}/Foo.${language.name}") << '''
interface Foo {
    interface Sub extends Foo {}
}
'''

        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")

        when:
        // Re-add sealed modifier, forces change to API.
        file("a/src/main/${language.name}/Foo.${language.name}").delete()
        file("a/src/main/${language.name}/Foo.${language.name}") << '''
sealed interface Foo {
    non-sealed interface Sub extends Foo {}
}
'''

        succeeds ":b:${language.compileTaskName}"

        then:
        executedAndNotSkipped(":b:${language.compileTaskName}")
    }
}
