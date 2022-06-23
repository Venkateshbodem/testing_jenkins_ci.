/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.testsuites.dependencies

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import spock.lang.Ignore


class TestSuitesKotlinDSLDependenciesIntegrationTest extends AbstractIntegrationSpec {
    private versionCatalog = file('gradle', 'libs.versions.toml')

    // region basic functionality
    def 'suites do not share dependencies by default'() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation("org.apache.commons:commons-lang3:3.11")
                        }
                    }
                    val integTest by registering(JvmTestSuite::class) {
                        useJUnit()
                    }
                }
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")
                doLast {
                    assert(configurations.getByName("testCompileClasspath").files.map { it.name }.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 is an implementation dependency for the default test suite" }
                    assert(configurations.getByName("testRuntimeClasspath").files.map { it.name }.equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 is an implementation dependency for the default test suite" }
                    assert(!configurations.getByName("integTestCompileClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar")) { "default test suite dependencies should not leak to integTest" }
                    assert(!configurations.getByName("integTestRuntimeClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar")) { "default test suite dependencies should not leak to integTest" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def "#suiteDesc supports annotationProcessor dependencies"() {
        given: "a suite that uses Google's Auto Value as an example of an annotation processor"
        settingsKotlinFile << """
            rootProject.name = "Test"
        """

        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation("com.google.auto.value:auto-value-annotations:1.9")
                            annotationProcessor("com.google.auto.value:auto-value:1.9")
                        }
                    }
                }
            }
            """

        file("src/$suiteName/java/Animal.java") << """
            import com.google.auto.value.AutoValue;

            @AutoValue
            abstract class Animal {
              static Animal create(String name, int numberOfLegs) {
                return new AutoValue_Animal(name, numberOfLegs);
              }

              abstract String name();
              abstract int numberOfLegs();
            }
            """

        file("src/$suiteName/java/AnimalTest.java") << """
            import org.junit.Test;

            import static org.junit.Assert.assertEquals;

            public class AnimalTest {
                @Test
                public void testCreateAnimal() {
                    Animal dog = Animal.create("dog", 4);
                    assertEquals("dog", dog.name());
                    assertEquals(4, dog.numberOfLegs());
                }
            }
            """

        expect: "tests using a class created by running that annotation processor will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion basic functionality

    // region dependencies - projects
    def 'default suite has project dependency by default; others do not'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class)
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")
            doLast {
                assert(configurations.getByName("testRuntimeClasspath").files.map { it.name } .equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(!configurations.getByName("integTestRuntimeClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar")) { "integTest does not implicitly depend on the production project" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'custom suites have project dependency if explicitly set'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(project)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")
            doLast {
                assert(configurations.getByName("testCompileClasspath").files.map { it.name } .equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(configurations.getByName("testRuntimeClasspath").files.map { it.name } .equals(listOf("commons-lang3-3.11.jar"))) { "commons-lang3 leaks from the production project dependencies" }
                assert(configurations.getByName("integTestRuntimeClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar")) { "integTest explicitly depends on the production project" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependencies to other projects to #suiteDesc'() {
        given:
        settingsKotlinFile << """
            rootProject.name = "root"

            include("consumer", "util")
        """

        buildKotlinFile << """
            allprojects {
                group = "org.test"
                version = "1.0"
            }

            subprojects {
                apply(plugin = "java-library")
            }
        """

        file('util/build.gradle.kts') << """
            dependencies {
                api("org.apache.commons:commons-lang3:3.11")
            }
        """

        file('consumer/build.gradle.kts') << """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(":util"))
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                doLast {
                    assert(configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar"))
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'


        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    @Ignore("exclude not available yet in Kotlin DSL")
    def 'can add dependencies to other projects with actions (using exclude) to #suiteDesc'() {
        settingsKotlinFile << """
            rootProject.name = "root"

            include("consumer", "util")
        """

        buildKotlinFile << """
            allprojects {
                group = "org.test"
                version = "1.0"
            }

            subprojects {
                apply(plugin = "java-library")
            }
        """

        file('util/build.gradle.kts') << """
            dependencies {
                api("org.apache.commons:commons-lang3:3.11")
            }
        """

        file('consumer/build.gradle.kts') << """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(":util")) {
                                exclude(group = "org.apache.commons", module = "commons-lang3")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                doLast {
                    configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }.contains("commons-lang3-3.11.jar")
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies to other projects with actions (using because) to #suiteDesc'() {
        settingsKotlinFile << """
            rootProject.name = "root"

            include("consumer", "util")
        """

        buildKotlinFile << """
            allprojects {
                group = "org.test"
                version = "1.0"
            }

            subprojects {
                apply(plugin = "java-library")
            }
        """

        file('util/build.gradle.kts') << """
            dependencies {
                api("org.apache.commons:commons-lang3:3.11")
            }
        """

        file('consumer/build.gradle.kts') << """
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(project(":util")) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                doLast {
                    val deps = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ProjectDependency::class)
                    assert(deps.size == 1)
                    deps.forEach {
                        assert(it.getReason().equals("for testing purposes"))
                    }
                }
            }
        """

        expect:
        succeeds ':consumer:checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    // endregion dependencies - projects

    // region dependencies - modules (GAV)
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite using #desc'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        dependencies {
            // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
            implementation("org.apache.commons:commons-lang3:3.11")
        }

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation($implementationNotationTest)
                        compileOnly($compileOnlyNotationTest)
                        runtimeOnly($runtimeOnlyNotationTest)
                    }
                }
                val integTest by registering(JvmTestSuite::class) {
                    // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                    dependencies {
                        implementation(project)
                        implementation($implementationNotationInteg)
                        compileOnly($compileOnlyNotationInteg)
                        runtimeOnly($runtimeOnlyNotationInteg)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")
            doLast {
                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        desc                | implementationNotationTest                     | compileOnlyNotationTest                              | runtimeOnlyNotationTest                        | implementationNotationInteg                     | compileOnlyNotationInteg                              | runtimeOnlyNotationInteg
        'GAV string'        | gavStr(guavaGroup, guavaName, guavaVerTest)    | gavStr(servletGroup, servletName, servletVerTest)    | gavStr(mysqlGroup, mysqlName, mysqlVerTest)    | gavStr(guavaGroup, guavaName, guavaVerInteg)    | gavStr(servletGroup, servletName, servletVerInteg)    | gavStr(mysqlGroup, mysqlName, mysqlVerInteg)
        'GAV map'           | gavMap(guavaGroup, guavaName, guavaVerTest)    | gavMap(servletGroup, servletName, servletVerTest)    | gavMap(mysqlGroup, mysqlName, mysqlVerTest)    | gavMap(guavaGroup, guavaName, guavaVerInteg)    | gavMap(servletGroup, servletName, servletVerInteg)    | gavMap(mysqlGroup, mysqlName, mysqlVerInteg)
    }

    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via DependencyHandler using #desc'() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val integTest by registering(JvmTestSuite::class)
                }
            }

            dependencies {
                // production code requires commons-lang3 at runtime, which will leak into tests' runtime classpaths
                implementation("org.apache.commons:commons-lang3:3.11")

                testImplementation($implementationNotationTest)
                testCompileOnly($compileOnlyNotationTest)
                testRuntimeOnly($runtimeOnlyNotationTest)

                // intentionally setting lower versions of the same dependencies on the `test` suite to show that no conflict resolution should be taking place
                val integTestImplementation by configurations.getting
                integTestImplementation(project)
                integTestImplementation($implementationNotationInteg)
                val integTestCompileOnly by configurations.getting
                integTestCompileOnly($compileOnlyNotationInteg)
                val integTestRuntimeOnly by configurations.getting
                integTestRuntimeOnly($runtimeOnlyNotationInteg)
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("integTest"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("test", "integTest")
                doLast {
                    val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                    val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar", "guava-30.1.1-jre.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-30.1.1-jre.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }

                    val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                    val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                    assert(integTestCompileClasspathFileNames.containsAll(listOf("servlet-api-2.5.jar", "guava-29.0-jre.jar")))
                    assert(!integTestCompileClasspathFileNames.contains("commons-lang3-3.11.jar")) { "implementation dependency of project, should not leak to integTest" }
                    assert(!integTestCompileClasspathFileNames.contains("mysql-connector-java-6.0.6.jar")) { "runtimeOnly dependency" }
                    assert(integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "guava-29.0-jre.jar", "mysql-connector-java-6.0.6.jar")))
                    assert(!integTestRuntimeClasspathFileNames.contains("servlet-api-2.5.jar")) { "compileOnly dependency" }
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        desc                | implementationNotationTest                     | compileOnlyNotationTest                              | runtimeOnlyNotationTest                        | implementationNotationInteg                     | compileOnlyNotationInteg                              | runtimeOnlyNotationInteg
        'GAV string'        | gavStr(guavaGroup, guavaName, guavaVerTest)    | gavStr(servletGroup, servletName, servletVerTest)    | gavStr(mysqlGroup, mysqlName, mysqlVerTest)    | gavStr(guavaGroup, guavaName, guavaVerInteg)    | gavStr(servletGroup, servletName, servletVerInteg)    | gavStr(mysqlGroup, mysqlName, mysqlVerInteg)
        'GAV map'           | gavMap(guavaGroup, guavaName, guavaVerTest)    | gavMap(servletGroup, servletName, servletVerTest)    | gavMap(mysqlGroup, mysqlName, mysqlVerTest)    | gavMap(guavaGroup, guavaName, guavaVerInteg)    | gavMap(servletGroup, servletName, servletVerInteg)    | gavMap(mysqlGroup, mysqlName, mysqlVerInteg)
        'named args'        | namedArgs(guavaGroup, guavaName, guavaVerTest) | namedArgs(servletGroup, servletName, servletVerTest) | namedArgs(mysqlGroup, mysqlName, mysqlVerTest) | namedArgs(guavaGroup, guavaName, guavaVerInteg) | namedArgs(servletGroup, servletName, servletVerInteg) | namedArgs(mysqlGroup, mysqlName, mysqlVerInteg)
    }

    @Ignore("currently failing in Kotlin, with or without mapOf, due to lack of exclude extension function from DependencyHandlerExtensions")
    def "can add dependency with actions on suite using a #desc"() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation($dependencyNotation) { (dep: ModuleDependency) ->
                                exclude(mapOf("group" to "$collectionsGroup", "module" to "$collectionsName"))
                                exclude(group = "$collectionsGroup", module = "$collectionsName")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("test")
                doLast {
                    val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                    assert(testCompileClasspathFileNames.contains("${beanUtilsName}-${beanUtilsVer}.jar"))
                    assert(!testCompileClasspathFileNames.contains("${collectionsName}-${collectionsVer}.jar")) { "excluded dependency" }
                }
            }
        """

        file('src/main/org/sample/Person.java') << """
            package org.sample;

            public class Person {
                private String name;
                private int age;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

                public int getAge() {
                    return age;
                }

                public void setAge(int age) {
                    this.age = age;
                }
            }
        """

        file('src/test/org/samplePersonTest.java') << """
            package org.sample;

            import org.apache.commons.beanutils.PropertyUtils;

            public class PersonTest {
                @Test
                public void testPerson() {
                    Object person = new Person();
                    PropertyUtils.setSimpleProperty(person, "name", "Bart Simpson");
                    PropertyUtils.setSimpleProperty(person, "age", 38);
                    assertEquals("Bart Simpson", person.getName());
                    assertEquals(38, person.getAge());
                }
            }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        desc                | dependencyNotation
        'GAV string'        | gavStr(beanUtilsGroup, beanUtilsName, beanUtilsVer)
        'GAV map'           | gavMap(beanUtilsGroup, beanUtilsName, beanUtilsVer)
    }

    def "can add dependencies using a non-String CharSequence: #type"() {
        given:
        buildKotlinFile << """
        import org.apache.commons.lang3.text.StrBuilder;

        buildscript {
            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                classpath("org.apache.commons:commons-lang3:3.11")
            }
        }

        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        val buf: $type = $creationNotation

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation(buf)
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("test")
            doLast {
                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
            }
        }
        """

        expect:
        succeeds 'checkConfiguration'

        where:
        type            | creationNotation
        'StringBuffer'  | "StringBuffer(\"org.apache.commons:commons-lang3:3.11\")"
        'StringBuilder' | "StringBuilder(\"org.apache.commons:commons-lang3:3.11\")"
        'StrBuilder'    | "StrBuilder(\"org.apache.commons:commons-lang3:3.11\")"
    }

    def "can NOT add a list of GAV dependencies to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(listOf("org.apache.commons:commons-lang3:3.11", "com.google.guava:guava:30.1.1-jre"))
                        }
                    }
                }
            }

        """

        expect:
        fails 'help'
        result.assertHasErrorOutput("Cannot convert the provided notation to an object of type Dependency: [org.apache.commons:commons-lang3:3.11, com.google.guava:guava:30.1.1-jre].")

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    private static guavaGroup = 'com.google.guava'
    private static guavaName = 'guava'
    private static guavaVerTest = '30.1.1-jre'
    private static guavaVerInteg = '29.0-jre'

    private static servletGroup = 'javax.servlet'
    private static servletName = 'servlet-api'
    private static servletVerTest = '3.0-alpha-1'
    private static servletVerInteg = '2.5'

    private static mysqlGroup = 'mysql'
    private static mysqlName = 'mysql-connector-java'
    private static mysqlVerTest = '8.0.26'
    private static mysqlVerInteg = '6.0.6'

    private static beanUtilsGroup = 'commons-beanutils'
    private static beanUtilsName = 'commons-beanutils'
    private static beanUtilsVer = '1.9.4'

    private static collectionsGroup = 'commons-collections'
    private static collectionsName = 'commons-collections'
    private static collectionsVer = '3.2.2'

    private static gavStr(String group, String name, String version) {
        return "\"$group:$name:$version\""
    }

    private static gavMap(String group, String name, String version) {
        return "mapOf(\"group\" to \"$group\", \"name\" to  \"$name\", \"version\" to \"$version\")"
    }

    private static namedArgs(String group, String name, String version) {
        return "group = \"$group\", name = \"$name\", version = \"$version\""
    }
    // endregion dependencies - modules (GAV)

    // region dependencies - dependency objects
    def 'can add dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = dependencies.create("org.apache.commons:commons-lang3:3.11")
            val servletApi = dependencies.create("javax.servlet:servlet-api:3.0-alpha-1")
            val mysql = dependencies.create("mysql:mysql-connector-java:8.0.26")

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation(commonsLang)
                            compileOnly(servletApi)
                            runtimeOnly(mysql)
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("test")
                doLast {
                    val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                    val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'
    }

    @Ignore("exclude not yet present in Kotlin DSL")
    def 'can add dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = dependencies.create("commons-beanutils:commons-beanutils:1.9.4")

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency objects with actions (using because) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = dependencies.create("commons-beanutils:commons-beanutils:1.9.4")

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val deps = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }
                    assert(deps.size == 1)
                    deps.forEach {
                        assert(it.getReason().equals("for testing purposes"))
                    }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - dependency objects

    // region dependencies - dependency providers
    def 'can add dependency providers which provide dependency objects to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = project.provider { dependencies.create("org.apache.commons:commons-lang3:3.11") }
            val servletApi = project.provider { dependencies.create("javax.servlet:servlet-api:3.0-alpha-1") }
            val mysql = project.provider { dependencies.create("mysql:mysql-connector-java:8.0.26") }

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation(commonsLang)
                            compileOnly(servletApi)
                            runtimeOnly(mysql)
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("test")
                doLast {
                    val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                    val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'
    }

    def 'can add dependency providers which provide GAVs to the implementation, compileOnly and runtimeOnly configurations of a suite'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val commonsLang = project.provider { "org.apache.commons:commons-lang3:3.11" }
            val servletApi = project.provider { "javax.servlet:servlet-api:3.0-alpha-1" }
            val mysql = project.provider { "mysql:mysql-connector-java:8.0.26" }

            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation(commonsLang)
                            compileOnly(servletApi)
                            runtimeOnly(mysql)
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("test")
                doLast {
                    val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                    val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                    assert(testCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "servlet-api-3.0-alpha-1.jar")))
                    assert(!testCompileClasspathFileNames.contains("mysql-connector-java-8.0.26.jar")) { "runtimeOnly dependency" }
                    assert(testRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar", "mysql-connector-java-8.0.26.jar")))
                    assert(!testRuntimeClasspathFileNames.contains("servlet-api-3.0-alpha-1.jar")) { "compileOnly dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'
    }

    @Ignore("exclude not yet present in Kotlin DSL")
    def 'can add dependency providers which provide dependency objects with actions (using exclude) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { dependencies.create("commons-beanutils:commons-beanutils:1.9.4") }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency providers which provide dependency objects with actions (using because) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { dependencies.create("commons-beanutils:commons-beanutils:1.9.4") }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val deps = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }
                    assert(deps.size == 1)
                    deps.forEach {
                        assert(it.getReason().equals("for testing purposes"))
                    }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    @Ignore("exclude not yet present in Kotlin DSL")
    def 'can add dependency providers which provide GAVs with actions (using excludes) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { "commons-beanutils:commons-beanutils:1.9.4" }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                exclude(group = "commons-collections", module = "commons-collections")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependency providers which provide GAVs with actions (using because) to #suiteDesc'() {
        given :
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            val beanUtils = project.provider { "commons-beanutils:commons-beanutils:1.9.4" }

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(beanUtils) {
                                because("for testing purposes")
                            }
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val deps = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }
                    assert(deps.size == 1)
                    deps.forEach {
                        assert(it.getReason().equals("for testing purposes"))
                    }
                }
            }
             """

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - dependency providers

    // region dependencies - Version Catalog
    def 'can add dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite via a Version Catalog'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                val integTest by registering(JvmTestSuite::class) {
                    dependencies {
                        implementation(libs.guava)
                        compileOnly(libs.commons.lang3)
                        runtimeOnly(libs.mysql.connector)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("integTest"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("test", "integTest")
            doLast {
                val integTestCompileClasspathFileNames = configurations.getByName("integTestCompileClasspath").files.map { it.name }
                val integTestRuntimeClasspathFileNames = configurations.getByName("integTestRuntimeClasspath").files.map { it.name }

                assert(integTestCompileClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(integTestCompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!integTestRuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!integTestCompileClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
                assert(integTestRuntimeClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        guava = "30.1.1-jre"
        commons-lang3 = "3.11"
        mysql-connector = "6.0.6"

        [libraries]
        guava = { module = "com.google.guava:guava", version.ref = "guava" }
        commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang3" }
        mysql-connector = { module = "mysql:mysql-connector-java", version.ref = "mysql-connector" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'
    }

    @Ignore("exclude not yet present in Kotlin DSL")
    def 'can add dependencies via a Version Catalog with actions (using exclude) to #suiteDesc'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons.beanutils) {
                            exclude(group = "commons-collections", module = "commons-collections")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }

                assert(${suiteName}CompileClasspathFileNames.contains("commons-beanutils-1.9.4.jar"))
                assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar")) { "excluded dependency" }
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-beanutils = "1.9.4"

        [libraries]
        commons-beanutils = { module = "commons-beanutils:commons-beanutils", version.ref = "commons-beanutils" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies via a Version Catalog with actions (using because) to #suiteDesc'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons.beanutils) {
                            because("for testing purposes")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val deps = configurations.getByName("${suiteName}CompileClasspath").getAllDependencies().withType(ModuleDependency::class).matching { it.group.equals("commons-beanutils") }
                assert(deps.size == 1)
                deps.forEach {
                    assert(it.getReason().equals("for testing purposes"))
                }
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-beanutils = "1.9.4"

        [libraries]
        commons-beanutils = { module = "commons-beanutils:commons-beanutils", version.ref = "commons-beanutils" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog bundle to #suiteDesc'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.bundles.groovy)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }

                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("groovy-json-3.0.5.jar", "groovy-nio-3.0.5.jar", "groovy-3.0.5.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("groovy-json-3.0.5.jar", "groovy-nio-3.0.5.jar", "groovy-3.0.5.jar")))
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        groovy = "3.0.5"

        [libraries]
        groovy-core = { module = "org.codehaus.groovy:groovy", version.ref = "groovy" }
        groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
        groovy-nio = { module = "org.codehaus.groovy:groovy-nio", version.ref = "groovy" }
        commons-lang3 = { group = "org.apache.commons", name = "commons-lang3", version = { strictly = "[3.8, 4.0[", prefer="3.9" } }

        [bundles]
        groovy = ["groovy-core", "groovy-json", "groovy-nio"]
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog with a hierarchy of aliases to #suiteDesc'() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.commons)
                        implementation(libs.commons.collections)
                        runtimeOnly(libs.commons.io)
                        runtimeOnly(libs.commons.io.csv)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }

                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "commons-collections4-4.4.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.12.0.jar", "commons-collections4-4.4.jar", "commons-io-2.11.0.jar", "commons-csv-1.9.0.jar")))
            }
        }
        """

        versionCatalog = file('gradle', 'libs.versions.toml') << """
        [versions]
        commons-lang = "3.12.0"
        commons-collections = "4.4"
        commons-io = "2.11.0"
        commons-io-csv = "1.9.0"

        [libraries]
        commons = { group = "org.apache.commons", name = "commons-lang3", version.ref = "commons-lang" }
        commons-collections = { group = "org.apache.commons", name = "commons-collections4", version.ref = "commons-collections" }
        commons-io = { group = "commons-io", name = "commons-io", version.ref = "commons-io" }
        commons-io-csv = { group = "org.apache.commons", name = "commons-csv", version.ref = "commons-io-csv" }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def 'can add dependencies using a Version Catalog defined programmatically to #suiteDesc'() {
        given:
        buildKotlinFile << """
        plugins {
            `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(libs.guava)
                        compileOnly(libs.commons.lang3)
                        runtimeOnly(libs.mysql.connector)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn(testing.suites.named("$suiteName"))
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }

                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("guava-30.1.1-jre.jar")))
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-lang3-3.11.jar")))
                assert(!${suiteName}CompileClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
                assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("mysql-connector-java-6.0.6.jar")))
            }
        }
        """

        settingsKotlinFile <<"""
        dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    version("guava", "30.1.1-jre")
                    version("commons-lang3", "3.11")
                    version("mysql-connector", "6.0.6")

                    library("guava", "com.google.guava", "guava").versionRef("guava")
                    library("commons-lang3", "org.apache.commons", "commons-lang3").versionRef("commons-lang3")
                    library("mysql-connector", "mysql", "mysql-connector-java").versionRef("mysql-connector")
                }
            }
        }
        """.stripIndent(8)

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - Version Catalog

    // region dependencies - platforms
    @Ignore("platforms not yet available in test suites")
    def "can add a platform dependency to #suiteDesc"() {
        given: "a suite that uses a platform dependency"
        settingsKotlinFile << """
            rootProject.name = "Test"

            include("platform", "consumer")
        """

        buildKotlinFile << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("org.apache.commons:commons-lang3:3.8.1")
                }
            }
        """

        file('platform/build.gradle.kts') << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("org.apache.commons:commons-lang3:3.8.1")
                }
            }
        """

        file('consumer/build.gradle.kts') << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(platform(project(":platform")))
                            implementation("org.apache.commons:commons-lang3")
                        }
                    }
                }
            }
        """

        file("consumer/src/$suiteName/java/SampleTest.java") << """
            import org.apache.commons.lang3.StringUtils;
            import org.junit.Test;

            import static org.junit.Assert.assertTrue;

            public class SampleTest {
                @Test
                public void testCommons() {
                    assertTrue(StringUtils.isAllLowerCase("abc"));
                }
            }
            """

        expect: "tests using a class from that platform will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    @Ignore("platforms not yet available in test suites")
    def "can add an enforced platform dependency to #suiteDesc"() {
        given: "a suite that uses an enforced platform dependency"
        settingsKotlinFile << """
            rootProject.name = "Test"

            include("platform", "consumer")
        """

        file('platform/build.gradle.kts') << """
            plugins {
                `java-platform`
            }

            dependencies {
                constraints {
                    api("commons-beanutils:commons-beanutils:1.9.0") // depends on commons-collections 3.2.1
                }
            }
        """

        file('consumer/build.gradle.kts') << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(enforcedPlatform(project(":platform")))
                            implementation("commons-collections:commons-collections:3.2.2")
                        }
                    }
                }
            }

            tasks.named("check") {
                dependsOn(testing.suites.named("$suiteName"))
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                    val ${suiteName}RuntimeClasspathFileNames = configurations.getByName("${suiteName}RuntimeClasspath").files.map { it.name }

                    assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("commons-beanutils-1.9.0.jar", "commons-collections-3.2.1.jar")))
                    assert(${suiteName}RuntimeClasspathFileNames.containsAll(listOf("commons-beanutils-1.9.0.jar", "commons-collections-3.2.1.jar")))
                    assert(!${suiteName}CompileClasspathFileNames.contains("commons-collections-3.2.2.jar"))
                    assert(!${suiteName}RuntimeClasspathFileNames.contains("commons-collections-3.2.2.jar"))
                }
            }
        """

        expect: "tests using a class from that enforcedPlatform will succeed"
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - platforms

    // region dependencies - file collections
    def "can add file collection dependencies to the implementation, compileOnly and runtimeOnly configurations of a suite"() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        testing {
            suites {
                val test by getting(JvmTestSuite::class) {
                    dependencies {
                        implementation(files("libs/dummy-1.jar"))
                        compileOnly(files("libs/dummy-2.jar"))
                        runtimeOnly(files("libs/dummy-3.jar"))
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("test")
            doLast {
                val testCompileClasspathFileNames = configurations.getByName("testCompileClasspath").files.map { it.name }
                val testRuntimeClasspathFileNames = configurations.getByName("testRuntimeClasspath").files.map { it.name }

                assert(testCompileClasspathFileNames.containsAll(listOf("dummy-1.jar")))
                assert(testRuntimeClasspathFileNames.containsAll(listOf("dummy-1.jar")))
                assert(testCompileClasspathFileNames.containsAll(listOf("dummy-2.jar")))
                assert(!testRuntimeClasspathFileNames.containsAll(listOf("dummy-2.jar")))
                assert(!testCompileClasspathFileNames.containsAll(listOf("dummy-3.jar")))
                assert(testRuntimeClasspathFileNames.containsAll(listOf("dummy-3.jar")))
            }
        }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'
    }

    def "can add file collection dependencies to #suiteDesc using fileTree"() {
        given:
        buildKotlinFile << """
            plugins {
              `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        dependencies {
                            implementation(fileTree("libs").matching {
                                include("dummy-*.jar")
                            })
                        }
                    }
                }
            }

            tasks.register("checkConfiguration") {
                dependsOn("$suiteName")
                doLast {
                    val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                    assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("dummy-1.jar", "dummy-2.jar", "dummy-3.jar")))
                }
            }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    def "can add file collection dependencies #suiteDesc with actions"() {
        given:
        buildKotlinFile << """
        plugins {
          `java-library`
        }

        ${mavenCentralRepository(GradleDsl.KOTLIN)}

        val configurationActions: MutableList<String> = mutableListOf()

        testing {
            suites {
                $suiteDeclaration {
                    dependencies {
                        implementation(files("libs/dummy-1.jar", "libs/dummy-2.jar")) {
                            configurationActions.add("configured files")
                        }
                    }
                }
            }
        }

        tasks.register("checkConfiguration") {
            dependsOn("$suiteName")
            doLast {
                val ${suiteName}CompileClasspathFileNames = configurations.getByName("${suiteName}CompileClasspath").files.map { it.name }
                assert(${suiteName}CompileClasspathFileNames.containsAll(listOf("dummy-1.jar", "dummy-2.jar")))

                assert(configurationActions.containsAll(listOf("configured files")))
            }
        }
        """

        file('libs/dummy-1.jar').createFile()
        file('libs/dummy-2.jar').createFile()
        file('libs/dummy-3.jar').createFile()

        expect:
        succeeds 'checkConfiguration'

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - file collections

    // region dependencies - self-resolving dependencies
    @Ignore("self-resolving methods not yet available in test suites")
    def "can add localGroovy dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `groovy`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(localGroovy())
                        }
                    }
                }
            }
        """

        file("src/$suiteName/groovy/Tester.groovy") << """
            import org.junit.Test

            class Tester {
                @Test
                public void testGroovyListOperations() {
                    List myList = ['Jack']
                    myList << 'Jill'
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    @Ignore("self-resolving methods not yet available in test suites")
    def "can add gradleApi dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnit()
                        dependencies {
                            implementation(gradleApi())
                        }
                    }
                }
            }
        """

        file("src/$suiteName/java/Tester.java") << """
            import org.junit.Test;
            import org.gradle.api.file.FileType;

            public class Tester {
                @Test
                public void testGradleApiAvailability() {
                    FileType type = FileType.FILE;
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }

    @Ignore("self-resolving methods not yet available in test suites")
    def "can add gradleTestKit dependency to #suiteDesc"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            testing {
                suites {
                    $suiteDeclaration {
                        useJUnitJupiter()
                        dependencies {
                            implementation(gradleTestKit())
                        }
                    }
                }
            }
        """

        file("src/$suiteName/java/Tester.java") << """
            import org.gradle.testkit.runner.TaskOutcome;
            import org.junit.jupiter.api.Test;

            public class Tester {
                @Test
                public void testTestKitAvailability()  {
                    TaskOutcome result = TaskOutcome.SUCCESS;
                }
            }
        """

        expect:
        succeeds(suiteName)

        where:
        suiteDesc           | suiteName   | suiteDeclaration
        'the default suite' | 'test'      | 'val test by getting(JvmTestSuite::class)'
        'a custom suite'    | 'integTest' | 'val integTest by registering(JvmTestSuite::class)'
    }
    // endregion dependencies - self-resolving dependencies
}
