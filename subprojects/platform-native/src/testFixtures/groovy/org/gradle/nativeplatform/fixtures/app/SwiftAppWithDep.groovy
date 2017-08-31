/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

class SwiftAppWithDep extends SourceElement implements AppElement {
    final SwiftMain main

    SwiftAppWithDep(GreeterElement greeter, SumElement sum) {
        main = new SwiftMain(greeter, sum)
    }

    @Override
    List<SourceFile> getFiles() {
        return main.getFiles().collect {
            sourceFile(it.path, it.name, "import Greeter\n${it.content}")
        }
    }

    @Override
    String getExpectedOutput() {
        return main.expectedOutput
    }
}
