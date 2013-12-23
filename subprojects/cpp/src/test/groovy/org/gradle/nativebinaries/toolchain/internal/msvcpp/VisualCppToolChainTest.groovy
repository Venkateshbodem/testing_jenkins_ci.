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

package org.gradle.nativebinaries.toolchain.internal.msvcpp

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.toolchain.internal.ToolChainAvailability
import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final ExecActionFactory execActionFactory = Mock(ExecActionFactory)
    final ToolSearchResult visualStudio = Mock(ToolSearchResult)
    final ToolSearchResult windowsSdkLookup = Mock(ToolSearchResult)
    final WindowsSdk windowsSdk = Mock(WindowsSdk)
    final VisualStudioLocator visualStudioLocator = Stub(VisualStudioLocator) {
        locateVisualStudioInstalls(_) >> visualStudio
    }
    final WindowsSdkLocator windowsSdkLocator = Stub(WindowsSdkLocator) {
        locateWindowsSdks(_) >> windowsSdkLookup
        getDefaultSdk() >> windowsSdk
    }
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
    }
    final toolChain = new VisualCppToolChain("visualCpp", operatingSystem, fileResolver, execActionFactory, visualStudioLocator, windowsSdkLocator)

    def "uses .lib file for shared library at link time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryLinkFileName("test") == "test.lib"
    }

    def "uses .dll file for shared library at runtime time"() {
        given:
        operatingSystem.getSharedLibraryName("test") >> "test.dll"

        expect:
        toolChain.getSharedLibraryName("test") == "test.dll"
    }

    def "installs an unavailable tool chain when not windows"() {
        given:
        def operatingSystem = Stub(OperatingSystem)
        operatingSystem.isWindows() >> false
        def toolChain = new VisualCppToolChain("visualCpp", operatingSystem, fileResolver, execActionFactory, visualStudioLocator, windowsSdkLocator)

        when:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == 'Visual Studio is not available on this operating system.'
    }

    def "is unavailable when visual studio installation cannot be located"() {
        when:
        visualStudio.available >> false
        visualStudio.explain(_) >> { TreeVisitor<String> visitor -> visitor.node("vs install not found anywhere") }
        windowsSdkLookup.available >> false

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == "vs install not found anywhere"
    }

    def "is unavailable when windows SDK cannot be located"() {
        when:
        visualStudio.available >> true
        windowsSdkLookup.available >> false
        windowsSdkLookup.explain(_) >> { TreeVisitor<String> visitor -> visitor.node("sdk not found anywhere") }

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);

        then:
        !availability.available
        availability.unavailableMessage == "sdk not found anywhere"
    }

    def "is available when visual studio installation and windows SDK can be located"() {
        when:
        visualStudio.available >> true
        windowsSdkLookup.available >> true

        and:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);

        then:
        availability.available
    }

    def "uses provided installDir and windowsSdkDir for location"() {
        when:
        toolChain.installDir = "install-dir"
        toolChain.windowsSdkDir = "windows-sdk-dir"

        and:
        fileResolver.resolve("install-dir") >> file("vs")
        visualStudioLocator.locateVisualStudioInstalls(file("vs")) >> visualStudio
        visualStudio.available >> true

        and:
        fileResolver.resolve("windows-sdk-dir") >> file("win-sdk")
        windowsSdkLocator.locateWindowsSdks(file("win-sdk")) >> windowsSdkLookup
        windowsSdkLookup.available >> true

        and:
        0 * _._

        then:
        def availability = new ToolChainAvailability()
        toolChain.checkAvailable(availability);
        availability.available
    }

    def "resolves install directory"() {
        when:
        toolChain.installDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.installDir == file("one")
    }

    def "resolves windows sdk directory"() {
        when:
        toolChain.windowsSdkDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.windowsSdkDir == file("one")
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
