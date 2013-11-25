/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testing

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.internal.os.OperatingSystem
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * General tests for the JVM testing infrastructure that don't deserve their own test class.
 */
class TestingIntegrationTest extends AbstractIntegrationSpec {

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-1948")
    def "test interrupting its own thread does not kill test execution"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "junit:junit:4.11" }
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    Thread.currentThread().interrupt();
                }
            }
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }

    @Timeout(30)
    def "fails cleanly even if an exception is thrown that doesn't serialize cleanly"() {
        given:
        file('src/test/java/ExceptionTest.java') << """
            import org.junit.*;
            import java.io.*;

            public class ExceptionTest {

                static class BadlyBehavedException extends Exception {
                    private void writeObject(ObjectOutputStream os) throws IOException {
                        throw new IOException("Failed strangely");
                    }
                }

                @Test
                public void testThrow() throws Throwable {
                    throw new BadlyBehavedException();
                }
            }
        """
        file('build.gradle') << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.10' }
        """

        when:
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"
    }

    @Timeout(30)
    def "fails cleanly even if an exception is thrown that doesn't de-serialize cleanly"() {
        given:

        // I'm not sure what it is exactly about InvalidRequestException
        // that brings out this problem. It's a thrift-generated
        // class and it has a "readObject" method. I couldn't reproduce the issue
        // using a simple class as in the previous case, presumably because it has to be
        // on the Gradle worker classpath as well as the Gradle main classpath -
        // could this be the case for Thrift?

        file('src/test/java/ExceptionTest.java') << """
            import org.junit.*;
            import org.apache.cassandra.thrift.InvalidRequestException;

            public class ExceptionTest {
                @Test
                public void testThrow() throws Throwable {
                    throw new InvalidRequestException("causes de-serialization failure");
                }
            }
        """
        file('build.gradle') << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
                testCompile 'junit:junit:4.10'
                compile 'org.hectorclient:hector-core:1.1-3'
            }
        """

        when:
        // an exception was thrown so we should fail here
        runAndFail "test"

        then:
        failureHasCause "There were failing tests"
    }

    @IgnoreIf({ OperatingSystem.current().isWindows() })
    def "can use long paths for working directory"() {
        given:
        // windows can handle a path up to 260 characters
        // we create a path that is 260 +1 (offset + "/" + randompath)
        def pathoffset = 260 - testDirectory.getAbsolutePath().length()
        def alphanumeric = RandomStringUtils.randomAlphanumeric(pathoffset)
        def testWorkingDir = testDirectory.createDir("$alphanumeric")

        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile "junit:junit:4.11" }
            test.workingDir = "${testWorkingDir.toURI()}"
        """

        and:
        file("src/test/java/SomeTest.java") << """
            import org.junit.*;

            public class SomeTest {
                @Test public void foo() {
                    System.out.println(new java.io.File(".").getAbsolutePath());
                }
            }
        """

        expect:
        succeeds "test"
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2313")
    @Unroll
    "can clean test after extracting class file with #framework"() {
        when:
        buildFile << """
            apply plugin: "java"
            repositories.mavenCentral()
            dependencies { testCompile "$dependency" }
            test { $framework() }
        """
        and:
        file("src/test/java/SomeTest.java") << """
            public class SomeTest extends $superClass {
            }
        """
        then:
        succeeds "clean", "test"

        and:
        file("build/tmp/test").exists() // ensure we extracted classes

        where:
        framework   | dependency                | superClass
        "useJUnit"  | "junit:junit:4.11"        | "org.junit.runner.Result"
        "useTestNG" | "org.testng:testng:6.3.1" | "org.testng.Converter"
    }

    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-2527")
    def "test class detection works for custom test tasks"() {
        given:
        buildFile << """
                apply plugin:'java'
                repositories{ mavenCentral() }

                sourceSets{
	                othertests{
		                java.srcDir file('src/othertests/java')
	                    resources.srcDir file('src/othertests/resources')
	                }
                }

                dependencies{
	                othertestsCompile "junit:junit:4.11"
                }

                task othertestsTest(type:Test){
	                useJUnit()
	                classpath = sourceSets.othertests.runtimeClasspath
	                testClassesDir = sourceSets.othertests.output.classesDir
	            }
            """

        and:
        file("src/othertests/java/AbstractTestClass.java") << """
                import junit.framework.TestCase;
                public abstract class AbstractTestClass extends TestCase {
                }
            """

        file("src/othertests/java/TestCaseExtendsAbstractClass.java") << """
                import junit.framework.Assert;
                public class TestCaseExtendsAbstractClass extends AbstractTestClass{
                    public void testTrue() {
                        Assert.assertTrue(true);
                    }
                }
            """

        when:
        run "othertestsTest"
        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted("TestCaseExtendsAbstractClass")
    }
}