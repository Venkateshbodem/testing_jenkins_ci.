/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.connection.BuildIdentity
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.connection.GradleConnectionBuilder
import org.gradle.tooling.connection.ModelResult
import org.gradle.util.GradleVersion

@ToolingApiVersion(ToolingApiVersions.SUPPORTS_COMPOSITE_BUILD)
@TargetGradleVersion(">=1.0")
abstract class CompositeToolingApiSpecification extends AbstractToolingApiSpecification {

    static GradleVersion getTargetDistVersion() {
        // Create a copy to work around classloader issues
        GradleVersion.version(targetDist.version.baseVersion.version)
    }

    GradleConnection createComposite(File... rootProjectDirectories) {
        createComposite(rootProjectDirectories.toList())
    }

    GradleConnection createComposite(List<File> rootProjectDirectories) {
        GradleConnectionBuilder builder = createCompositeBuilder()

        rootProjectDirectories.each {
            addCompositeParticipant(builder, it)
        }

        builder.build()
    }

    GradleConnectionBuilder createCompositeBuilder() {
        return toolingApi.createCompositeBuilder()
    }

    BuildIdentity addCompositeParticipant(GradleConnectionBuilder builder, File rootDir) {
        return toolingApi.addCompositeParticipant(builder, rootDir)
    }

    def <T> T withCompositeConnection(File rootProjectDir, Closure<T> c) {
        withCompositeConnection([rootProjectDir], c)
    }

    def <T> T withCompositeConnection(List<File> rootProjectDirectories, @ClosureParams(value = SimpleType, options = [ "org.gradle.tooling.connection.GradleConnection" ]) Closure<T> c) {
        GradleConnection connection = createComposite(rootProjectDirectories)
        try {
            return c(connection)
        } finally {
            connection?.close()
        }
    }

    def <T> T withCompositeBuildParticipants(List<File> rootProjectDirectories, Closure<T> c) {
        GradleConnectionBuilder builder = createCompositeBuilder()
        def buildIds = []

        rootProjectDirectories.each {
            buildIds << addCompositeParticipant(builder, it)
        }

        GradleConnection connection = builder.build()
        try {
            return c(connection, buildIds)
        } finally {
            connection?.close()
        }
    }

    TestFile getRootDir() {
        temporaryFolder.testDirectory
    }

    TestFile file(Object... path) {
        rootDir.file(path)
    }

    TestFile populate(String projectName, @DelegatesTo(ProjectTestFile) Closure cl) {
        def project = new ProjectTestFile(rootDir, projectName)
        project.with(cl)
        project
    }

    TestFile projectDir(String project) {
        file(project)
    }

    static class ProjectTestFile extends TestFile {
        private final String projectName

        ProjectTestFile(TestFile rootDir, String projectName) {
            super(rootDir, [ projectName ])
            this.projectName = projectName
        }
        String getRootProjectName() {
            projectName
        }
        TestFile getBuildFile() {
            file("build.gradle")
        }
        TestFile getSettingsFile() {
            file("settings.gradle")
        }
        void addChildDir(String name) {
            file(name).file("build.gradle") << "// Dummy child build"
        }
    }

    // Transforms Iterable<ModelResult<T>> into Iterable<T>
    def unwrap(Iterable<ModelResult> modelResults) {
        modelResults.collect { it.model }
    }

    void assertFailure(Throwable failure, String... messages) {
        assert failure != null
        def causes = getCauses(failure)

        messages.each { message ->
            assert causes.contains(message)
        }
    }

    void assertFailureHasCause(Throwable failure, Class<Throwable> cause) {
        assert failure != null
        Throwable throwable = failure
        List causes = []
        while (throwable != null) {
            causes << throwable
            throwable = throwable.cause
        }
        assert causes.any { it.getClass().getCanonicalName().equals(cause.getCanonicalName()) }
    }

    private static String getCauses(Throwable throwable) {
        def causes = '';
        while (throwable != null) {
            if (throwable.message != null) {
                causes += throwable.message
                causes += '\n'
            }
            throwable = throwable.cause
        }
        causes
    }
}
