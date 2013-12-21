/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.language.cpp.fixtures

import org.gradle.test.fixtures.file.TestFile

class SharedLibraryFixture extends NativeBinaryFixture {
    SharedLibraryFixture(TestFile file, AvailableToolChains.InstalledToolChain toolChain) {
        super(file, toolChain)
    }

    @Override
    void assertExists() {
        super.assertExists()
        if (toolChain.visualCpp) {
            file.withExtension("lib").assertIsFile()
            file.withExtension("exp").assertIsFile()
        }
    }

    @Override
    void assertDoesNotExist() {
        super.assertDoesNotExist()
        if (toolChain.visualCpp) {
            file.withExtension("lib").assertDoesNotExist()
            file.withExtension("exp").assertDoesNotExist()
        }
    }

    String getSoName() {
        return binaryInfo.soName
    }
}
