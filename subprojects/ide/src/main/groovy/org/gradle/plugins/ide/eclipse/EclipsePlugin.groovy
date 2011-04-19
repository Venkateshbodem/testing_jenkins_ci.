/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.plugins.ide.eclipse.internal.EclipseNameDeduper
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.plugins.ide.eclipse.model.*

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @author Hans Dockter
 */
class EclipsePlugin extends IdePlugin {
    static final String ECLIPSE_TASK_NAME = "eclipse"
    static final String CLEAN_ECLIPSE_TASK_NAME = "cleanEclipse"
    static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject"
    static final String ECLIPSE_WTP_COMPONENT_TASK_NAME = "eclipseWtpComponent"
    static final String ECLIPSE_WTP_FACET_TASK_NAME = "eclipseWtpFacet"
    static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath"
    static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt"

    EclipseModel model = new EclipseModel()

    @Override protected String getLifecycleTaskName() {
        return 'eclipse'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates all Eclipse files.'
        cleanTask.description = 'Cleans all Eclipse files.'

        project.convention.plugins.eclipse = model

        configureEclipseProject(project)
        configureEclipseClasspath(project)
        configureEclipseJdt(project)
        configureEclipseWtpComponent(project)
        configureEclipseWtpFacet(project)

        project.afterEvaluate {
            new EclipseNameDeduper().configure(project)
        }
    }

    private void configureEclipseProject(Project project) {
        addEclipsePluginTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject) {
            //task properties:
            description = "Generates the Eclipse project file."
            inputFile = project.file('.project')
            outputFile = project.file('.project')

            //model:
            model.project = services.get(ClassGenerator).newInstance(EclipseProject)
            projectModel = model.project

            projectModel.name = project.name
            projectModel.conventionMapping.comment = { project.description }

            project.plugins.withType(JavaBasePlugin) {
                projectModel.buildCommand "org.eclipse.jdt.core.javabuilder"
                projectModel.natures "org.eclipse.jdt.core.javanature"
            }

            project.plugins.withType(GroovyBasePlugin) {
                projectModel.natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }

            project.plugins.withType(ScalaBasePlugin) {
                projectModel.buildCommands.set(buildCommands.findIndexOf { it.name == "org.eclipse.jdt.core.javabuilder" },
                        new BuildCommand("ch.epfl.lamp.sdt.core.scalabuilder"))
                projectModel.natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "ch.epfl.lamp.sdt.core.scalanature")
            }

            project.plugins.withType(WarPlugin) {
                projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'

                eachDependedUponProject(project) { Project otherProject ->
                    configureTask(otherProject, ECLIPSE_PROJECT_TASK_NAME) {
                        projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                        projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                        projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                        projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                        projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'
                    }
                }
            }
        }
    }

    private void configureEclipseClasspath(Project project) {
        model.classpath = project.services.get(ClassGenerator).newInstance(EclipseClasspath, [project: project])
        model.classpath.conventionMapping.classesOutputDir = { new File(project.projectDir, 'bin') }

        project.plugins.withType(JavaBasePlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath) {
                //task properties:
                description = "Generates the Eclipse classpath file."
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')

                //model properties:
                classpath = model.classpath

                classpath.sourceSets = project.sourceSets //TODO SF - should be a convenience property?
                classpath.containers 'org.eclipse.jdt.launching.JRE_CONTAINER'

                project.plugins.withType(JavaPlugin) {
                    classpath.plusConfigurations = [project.configurations.testRuntime]
                }

                project.plugins.withType(WarPlugin) {
                    eachDependedUponProject(project) { Project otherProject ->
                        configureTask(otherProject, ECLIPSE_CP_TASK_NAME) {
                            whenConfigured { Classpath classpath ->
                                for (entry in classpath.entries) {
                                    if (entry instanceof Library) {
                                        // '../' and '/WEB-INF/lib' both seem to be correct (and equivalent) values here
                                        entry.entryAttributes['org.eclipse.jst.component.dependency'] = '../'
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseJdt(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt) {
                //task properties:
                description = "Generates the Eclipse JDT settings file."
                outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                //model properties:
                projectModel = model.project
                projectModel.conventionMapping.sourceCompatibility = { project.sourceCompatibility }
                projectModel.conventionMapping.targetCompatibility = { project.targetCompatibility }
            }
        }
    }

    private void configureEclipseWtpComponent(Project project) {
        project.plugins.withType(WarPlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                //task properties:
                description = 'Generates the Eclipse WTP component settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.component')
                outputFile = project.file('.settings/org.eclipse.wst.common.component')

                //model properties:
                wtp = services.get(ClassGenerator).newInstance(EclipseWtp, [project: project])
                model.wtp = wtp

                wtp.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                wtp.plusConfigurations = [project.configurations.runtime]
                wtp.minusConfigurations = [project.configurations.providedRuntime]
                wtp.deployName = project.name
                wtp.resource deployPath: '/', sourcePath: project.convention.plugins.war.webAppDirName // TODO: not lazy
                wtp.conventionMapping.contextPath = { project.war.baseName }
            }

            eachDependedUponProject(project) { otherProject ->
                // require Java plugin because we need source set 'main'
                // (in the absence of 'main', it probably makes no sense to write the file)
                otherProject.plugins.withType(JavaPlugin) {
                    addEclipsePluginTask(otherProject, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                        //task properties:
                        description = 'Generates the Eclipse WTP component settings file.'
                        inputFile = otherProject.file('.settings/org.eclipse.wst.common.component')
                        outputFile = otherProject.file('.settings/org.eclipse.wst.common.component')

                        //model properties:
                        wtp = services.get(ClassGenerator).newInstance(EclipseWtp, [project: otherProject])
                        otherProject.plugins.withType(EclipsePlugin) { it.model.wtp = wtp }

                        wtp.deployName = otherProject.name
                        wtp.conventionMapping.resources = {
                            getMainSourceDirs(otherProject).collect { new WbResource("/", otherProject.relativePath(it)) }
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseWtpFacet(Project project) {
        project.plugins.withType(WarPlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                //task properties:
                description = 'Generates the Eclipse WTP facet settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                //model properties:
                wtp = model.wtp
                wtp.conventionMapping.facets = { [new Facet("jst.web", "2.4"), new Facet("jst.java", toJavaFacetVersion(project.sourceCompatibility))] }
            }

            eachDependedUponProject(project) { otherProject ->
                addEclipsePluginTask(otherProject, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                    //task properties:
                    description = 'Generates the Eclipse WTP facet settings file.'
                    inputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                    outputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                    //model properties:
                    wtp = model.wtp
                    conventionMapping.facets = { [new Facet("jst.utility", "1.0")] }
                    otherProject.plugins.withType(JavaPlugin) {
                        conventionMapping.facets = {
                            [new Facet("jst.utility", "1.0"), new Facet("jst.java",
                                    toJavaFacetVersion(otherProject.sourceCompatibility))]
                        }
                    }
                }
            }
        }
    }

    // TODO: might have to search all class paths of all source sets for project dependendencies, not just runtime configuration
    private void eachDependedUponProject(Project project, Closure action) {
        project.gradle.projectsEvaluated {
            doEachDependedUponProject(project, action)
        }
    }

    private void doEachDependedUponProject(Project project, Closure action) {
        def runtimeConfig = project.configurations.findByName("runtime")
        if (runtimeConfig) {
            def projectDeps = runtimeConfig.getAllDependencies(ProjectDependency)
            def dependedUponProjects = projectDeps*.dependencyProject
            for (dependedUponProject in dependedUponProjects) {
                action(dependedUponProject)
                doEachDependedUponProject(dependedUponProject, action)
            }
        }
    }

    private void withTask(Project project, String taskName, Closure action) {
        project.tasks.matching { it.name == taskName }.all(action)
    }

    private void configureTask(Project project, String taskName, Closure action) {
        withTask(project, taskName) { task ->
            project.configure(task, action)
        }
    }

    // note: we only add and configure the task if it doesn't exist yet
    private void addEclipsePluginTask(Project project, EclipsePlugin plugin = null, String taskName, Class taskType, Closure action) {
        if (plugin) {
            doAddEclipsePluginTask(project, plugin, taskName, taskType, action)
        } else {
            project.plugins.withType(EclipsePlugin) { EclipsePlugin otherPlugin ->
                doAddEclipsePluginTask(project, otherPlugin, taskName, taskType, action)
            }
        }
    }

    private void doAddEclipsePluginTask(Project project, EclipsePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) { return }

        def task = project.tasks.add(taskName, taskType) // TODO: whenTaskAdded hook will fire before task has been configured
        project.configure(task, action)
        plugin.addWorker(task)
    }

    private String toJavaFacetVersion(JavaVersion version) {
        if (version == JavaVersion.VERSION_1_5) {
            return '5.0'
        }
        if (version == JavaVersion.VERSION_1_6) {
            return '6.0'
        }
        return version.toString()
    }

    private Set<File> getMainSourceDirs(Project project) {
        project.sourceSets.main.allSource.srcDirs as LinkedHashSet
    }
}
