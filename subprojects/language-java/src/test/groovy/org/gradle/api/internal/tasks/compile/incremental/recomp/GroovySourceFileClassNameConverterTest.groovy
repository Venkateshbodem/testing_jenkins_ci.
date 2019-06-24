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

package org.gradle.api.internal.tasks.compile.incremental.recomp

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GroovySourceFileClassNameConverterTest extends Specification {
    @Subject
    GroovySourceFileClassNameConverter converter

    def setup() {
        Multimap<File, String> sourceClassesMapping = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build()

        sourceClassesMapping.put(new File('MyClass'), 'org.gradle.MyClass1')
        sourceClassesMapping.put(new File('MyClass'), 'org.gradle.MyClass2')
        sourceClassesMapping.put(new File('YourClass'), 'org.gradle.YourClass')

        converter = new GroovySourceFileClassNameConverter(sourceClassesMapping)
    }

    @Unroll
    def 'can get class names by file'() {
        expect:
        converter.getClassNames(file) == classes
        where:
        file                   | classes
        new File('MyClass')    | ['org.gradle.MyClass1', 'org.gradle.MyClass2'] as Set
        new File('YourClass')  | ['org.gradle.YourClass'] as Set
        new File('OtherClass') | [] as Set
    }

    @Unroll
    def 'can get file by classname'() {
        expect:
        converter.getFile(fqcn) == file
        where:
        fqcn                    | file
        'org.gradle.MyClass1'   | Optional.of(new File('MyClass'))
        'org.gradle.MyClass2'   | Optional.of(new File('MyClass'))
        'org.gradle.YourClass'  | Optional.of(new File('YourClass'))
        'org.gradle.OtherClass' | Optional.empty()
    }
}
