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

package org.gradle.integtests.tooling.r47

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.UnsupportedVersionException

import java.util.regex.Pattern

@ToolingApiVersion(">=4.7")
class PhasedBuildActionCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import javax.inject.Inject

            task hello {
                doLast {
                    println "hello"
                }
            }

            task bye(dependsOn: hello) {
                doLast {
                    println "bye"
                }
            }
            
            allprojects {
                apply plugin: CustomPlugin
            }
            
            class DefaultCustomModel implements Serializable {
                private final String value;
                DefaultCustomModel(String value) {
                    this.value = value;
                }
                public String getValue() {
                    return value;
                }
            }

            interface CustomParameter {
                void setTasks(List<String> tasks);
                List<String> getTasks();
            }
            
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry) {
                    registry.register(new CustomBuilder());
                }
            
                public void apply(Project project) {
                }
            }
            
            class CustomBuilder implements ParameterizedToolingModelBuilder<CustomParameter> {
                boolean canBuild(String modelName) {
                    return modelName == '${CustomProjectsEvaluatedModel.name}' || modelName == '${CustomBuildFinishedModel.name}'
                }
                
                Class<CustomParameter> getParameterType() {
                    return CustomParameter.class;
                }
                
                Object buildAll(String modelName, Project project) {
                    if (modelName == '${CustomProjectsEvaluatedModel.name}') {
                        return new DefaultCustomModel('configuration');
                    }
                    if (modelName == '${CustomBuildFinishedModel.name}') {
                        return new DefaultCustomModel('build');
                    }
                    return null
                }
                
                Object buildAll(String modelName, CustomParameter parameter, Project project) {
                    if (modelName == '${CustomProjectsEvaluatedModel.name}') {
                        project.setDefaultTasks(parameter.getTasks());
                        return new DefaultCustomModel('configurationWithTasks');
                    }
                    return null
                }
            }
        """
    }

    @TargetGradleVersion(">=4.7")
    def "can run phased action"() {
        PhasedResultHandlerCollector projectsLoadedHandler = new PhasedResultHandlerCollector()
        PhasedResultHandlerCollector projectsEvaluatedHandler = new PhasedResultHandlerCollector()
        PhasedResultHandlerCollector buildFinishedHandler = new PhasedResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .projectsEvaluated(new CustomProjectsEvaluatedAction(null), projectsEvaluatedHandler)
                .buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        projectsLoadedHandler.getResult() == "loading"
        projectsEvaluatedHandler.getResult() == "configuration"
        buildFinishedHandler.getResult() == "build"
    }

    @TargetGradleVersion(">=4.7")
    def "failures are received and future actions not run"() {
        PhasedResultHandlerCollector projectsLoadedHandler = new PhasedResultHandlerCollector()
        PhasedResultHandlerCollector projectsEvaluatedHandler = new PhasedResultHandlerCollector()
        PhasedResultHandlerCollector buildFinishedHandler = new PhasedResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().projectsLoaded(new CustomProjectsLoadedAction(), projectsLoadedHandler)
                .projectsEvaluated(new FailAction(), projectsEvaluatedHandler)
                .buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        BuildActionFailureException e = thrown()
        e.message == "The supplied phased action failed with an exception."
        e.cause instanceof RuntimeException
        e.cause.message == "actionFailure"
        projectsLoadedHandler.getResult() == "loading"
        projectsEvaluatedHandler.getResult() == null
        buildFinishedHandler.getResult() == null
    }

    @TargetGradleVersion(">=4.7")
    def "can modify task graph in after configuration action"() {
        PhasedResultHandlerCollector projectsEvaluatedHandler = new PhasedResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.phasedAction().projectsEvaluated(new CustomProjectsEvaluatedAction(["hello"]), projectsEvaluatedHandler)
                .build()
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        projectsEvaluatedHandler.getResult() == "configurationWithTasks"
        stdOut.toString().contains("hello")
    }

    @TargetGradleVersion(">=4.7")
    def "can run pre-defined tasks and build finished action is run after tasks are executed"() {
        PhasedResultHandlerCollector buildFinishedHandler = new PhasedResultHandlerCollector()
        def stdOut = new ByteArrayOutputStream()

        when:
        withConnection { connection ->
            connection.phasedAction().buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .forTasks("bye")
                .setStandardOutput(stdOut)
                .run()
        }

        then:
        Pattern regex = Pattern.compile(".*hello.*bye.*afterBuildAction.*", Pattern.DOTALL)
        assert stdOut.toString().matches(regex)
        buildFinishedHandler.getResult() == "build"
        stdOut.toString().contains("hello")
        stdOut.toString().contains("bye")
    }

    @TargetGradleVersion("<4.7")
    def "exception when not supported gradle version"() {
        def version = targetDist.version.version
        PhasedResultHandlerCollector buildFinishedHandler = new PhasedResultHandlerCollector()

        when:
        withConnection { connection ->
            connection.phasedAction().buildFinished(new CustomBuildFinishedAction(), buildFinishedHandler)
                .build()
                .run()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (${version}) does not support the PhasedBuildActionExecuter API. Support for this is available in Gradle 4.7 and all later versions."
    }
}
