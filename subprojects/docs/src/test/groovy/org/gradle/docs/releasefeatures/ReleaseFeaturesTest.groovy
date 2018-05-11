/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.docs.releasefeatures

import org.gradle.docs.SystemPropertyFiles
import spock.lang.Specification

class ReleaseFeaturesTest extends Specification {

    def "release features must follow conventions"() {
        given:
        def featuresFile = SystemPropertyFiles.get("org.gradle.docs.releasefeatures")

        when:
        def featuresText = featuresFile.text

        then:
        def lines = featuresText.readLines()
        lines.size() <= 10
        lines.every { it.startsWith(" - ") }
        lines.every { it.length() <= 80 }
    }
}
