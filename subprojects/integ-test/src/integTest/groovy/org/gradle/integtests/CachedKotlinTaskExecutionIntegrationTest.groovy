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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractPluginIntegrationTest
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import spock.lang.IgnoreIf

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires([KOTLIN_SCRIPT])
class CachedKotlinTaskExecutionIntegrationTest extends AbstractPluginIntegrationTest implements DirectoryBuildCacheFixture {

    @Override
    protected String getDefaultBuildFileName() {
        'build.gradle.kts'
    }

    def setup() {
        settingsFile << "rootProject.buildFileName = '$defaultBuildFileName'"

        file("buildSrc/settings.gradle.kts") << """
            buildCache {
                local(DirectoryBuildCache::class.java) {
                    directory = "${cacheDir.absoluteFile.toURI()}"
                    isPush = true
                }
            }
        """
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    @LeaksFileHandles
    def "tasks stay cached after buildSrc with custom Kotlin task is rebuilt"() {
        withKotlinBuildSrc()
        file("buildSrc/src/main/kotlin/CustomTask.kt") << customKotlinTask()
        file("input.txt") << "input"
        buildFile << """
            task<CustomTask>("customTask") {
                inputFile = project.file("input.txt")
                outputFile = project.file("build/output.txt")
            }
        """
        when:
        withBuildCache().run "customTask"
        then:
        skippedTasks.empty

        when:
        file("buildSrc/build").deleteDir()
        file("buildSrc/.gradle").deleteDir()
        cleanBuildDir()

        withBuildCache().run "customTask"
        then:
        skippedTasks.contains ":customTask"
    }

    @IgnoreIf({GradleContextualExecuter.parallel})
    @LeaksFileHandles
    def "changing custom Kotlin task implementation in buildSrc doesn't invalidate built-in task"() {
        withKotlinBuildSrc()
        def taskSourceFile = file("buildSrc/src/main/kotlin/CustomTask.kt")
        taskSourceFile << customKotlinTask()
        file("input.txt") << "input"
        buildFile << """
            task<CustomTask>("customTask") {
                inputFile = project.file("input.txt")
                outputFile = project.file("build/output.txt")
            }
        """
        when:
        withBuildCache().run "customTask"
        then:
        skippedTasks.empty
        file("build/output.txt").text == "input"

        when:
        taskSourceFile.text = customKotlinTask(" modified")

        cleanBuildDir()
        withBuildCache().run "customTask"
        then:
        nonSkippedTasks.contains ":customTask"
        file("build/output.txt").text == "input modified"
    }

    def withKotlinBuildSrc() {
        file("buildSrc/build.gradle.kts") << KotlinDslTestUtil.kotlinDslBuildSrcScript
    }

    private static String customKotlinTask(String suffix = "") {
        """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import java.io.File

            @CacheableTask
            open class CustomTask() : DefaultTask() {
                @get:InputFile var inputFile: File? = null
                @get:OutputFile var outputFile: File? = null
                @TaskAction fun doSomething() {
                    outputFile!!.apply {
                        parentFile.mkdirs()
                        writeText(inputFile!!.readText())
                        appendText("$suffix")
                    }
                }
            }
        """
    }

    private TestFile cleanBuildDir() {
        file("build").assertIsDir().deleteDir()
    }

}
