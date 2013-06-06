/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.reflect

import org.gradle.internal.UncheckedException
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException

import static org.gradle.internal.reflect.JavaReflectionUtil.*

class JavaReflectionUtilTest extends Specification {
    JavaTestSubject myProperties = new JavaTestSubject()

    def "read property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "myValue"
    }

    def "write property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myProperty", "otherValue")

        then:
        JavaReflectionUtil.readProperty(myProperties, "myProperty") == "otherValue"
    }

    def "read boolean property"() {
        expect:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == true
    }

    def "write boolean property"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "myBooleanProperty", false)

        then:
        JavaReflectionUtil.readProperty(myProperties, "myBooleanProperty") == false
    }

    def "read property that doesn't exist"() {
        when:
        JavaReflectionUtil.readProperty(myProperties, "unexisting")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    def "write property that doesn't exist"() {
        when:
        JavaReflectionUtil.writeProperty(myProperties, "unexisting", "someValue")

        then:
        UncheckedException e = thrown()
        e.cause instanceof NoSuchMethodException
    }

    def "call methods successfully reflectively"() {
        expect:
        invokeMethod(myProperties, "getMyProperty") == myProperties.myProp
        invokeMethod(myProperties, "setMyProperty", "foo") == null
        invokeMethod(myProperties, "getMyProperty") == "foo"
    }

    def "call failing methods reflectively"() {
        when:
        invokeMethod(myProperties, "throwsException")

        then:
        def e = thrown InvocationTargetException
        e.cause instanceof IllegalStateException

        when:
        invokeMethodWrapException(myProperties, "throwsException")

        then:
        def e2 = thrown RuntimeException
        e2.cause instanceof InvocationTargetException
        e2.cause.cause instanceof IllegalStateException
    }

    def "call declared method that may not be public"() {
        when:
        invokeMethod(new JavaTestSubjectSubclass(), "protectedMethod")

        then:
        thrown NoSuchMethodException

        then:
        expect:
        invokeDeclaredMethod(new JavaTestSubjectSubclass(), JavaTestSubject, "protectedMethod", [] as Class[]) == "parent"
        invokeDeclaredMethod(new JavaTestSubjectSubclass(), JavaTestSubject, "overridden", [] as Class[]) == "subclass"
    }

}

