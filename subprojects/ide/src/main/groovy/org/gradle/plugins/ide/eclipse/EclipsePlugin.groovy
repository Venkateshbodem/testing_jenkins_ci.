/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.eclipse.internal.EclipseNameDeduper
import org.gradle.plugins.ide.eclipse.internal.LinkedResourcesCreator
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.api.plugins.*
import org.gradle.plugins.ide.eclipse.model.*

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @author Hans Dockter
 */
class EclipsePlugin extends IdePlugin {
    static final String ECLIPSE_TASK_NAME = "eclipse"
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

        project.extensions.add('eclipse', model)

        configureEclipseProject(project)
        configureEclipseClasspath(project)
        configureEclipseJdt(project)
        configureEclipseWtpComponent(project)
        configureEclipseWtpFacet(project)

        hookDeduplicationToTheRoot(project)
    }

    void hookDeduplicationToTheRoot(Project project) {
        if (project.parent == null) {
            project.gradle.projectsEvaluated {
                makeSureProjectNamesAreUnique()
            }
        }
    }

    public void makeSureProjectNamesAreUnique() {
        new EclipseNameDeduper().configureRoot(project.rootProject);
    }

    private void configureEclipseProject(Project project) {
        maybeAddTask(project, this, ECLIPSE_PROJECT_TASK_NAME, GenerateEclipseProject) {
            //task properties:
            description = "Generates the Eclipse project file."
            inputFile = project.file('.project')
            outputFile = project.file('.project')

            //model:
            model.project = projectModel

            projectModel.name = project.name
            projectModel.conventionMapping.comment = { project.description }

            project.plugins.withType(JavaBasePlugin) {
                if (!project.plugins.hasPlugin(EarPlugin)) {
                    projectModel.buildCommand "org.eclipse.jdt.core.javabuilder"
                }
                projectModel.natures "org.eclipse.jdt.core.javanature"
                projectModel.conventionMapping.linkedResources = {
                    new LinkedResourcesCreator().links(project)
                }
            }

            project.plugins.withType(GroovyBasePlugin) {
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }

            project.plugins.withType(ScalaBasePlugin) {
                projectModel.buildCommands.set(buildCommands.findIndexOf { it.name == "org.eclipse.jdt.core.javabuilder" },
                        new BuildCommand("org.scala-ide.sdt.core.scalabuilder"))
                projectModel.natures.add(projectModel.natures.indexOf("org.eclipse.jdt.core.javanature"), "org.scala-ide.sdt.core.scalanature")
            }

            configureEclipseProjectWithType(project, WarPlugin)
            configureEclipseProjectWithType(project, EarPlugin)
        }
    }

    private void configureEclipseProjectWithType(Project project, Class<?> type) {
        project.plugins.withType(type) {
            configureTask(project, ECLIPSE_PROJECT_TASK_NAME) {
                projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
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

    private void configureEclipseClasspath(Project project) {
        model.classpath = project.services.get(ClassGenerator).newInstance(EclipseClasspath, [project: project])
        model.classpath.conventionMapping.defaultOutputDir = { new File(project.projectDir, 'bin') }

        project.plugins.withType(JavaBasePlugin) {
            maybeAddTask(project, this, ECLIPSE_CP_TASK_NAME, GenerateEclipseClasspath) { task ->
                //task properties:
                description = "Generates the Eclipse classpath file."
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')

                //model properties:
                classpath = model.classpath
                classpath.file = new XmlFileContentMerger(xmlTransformer)

                classpath.sourceSets = project.sourceSets

                classpath.containers 'org.eclipse.jdt.launching.JRE_CONTAINER'

                project.plugins.withType(JavaPlugin) {
                    classpath.plusConfigurations = [project.configurations.testRuntime]
                    classpath.conventionMapping.classFolders = {
                        def dirs = project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs
                        dirs.collect { project.relativePath(it)} .findAll { !it.contains('..') }
                    }
                    task.dependsOn {
                        project.sourceSets.main.output.dirs + project.sourceSets.test.output.dirs
                    }
                }

                project.plugins.withType(WarPlugin) {
                    doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
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
            maybeAddTask(project, this, ECLIPSE_JDT_TASK_NAME, GenerateEclipseJdt) {
                //task properties:
                description = "Generates the Eclipse JDT settings file."
                outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                //model properties:
                model.jdt = jdt
                jdt.conventionMapping.sourceCompatibility = { project.sourceCompatibility }
                jdt.conventionMapping.targetCompatibility = { project.targetCompatibility }
            }
        }
    }

    private void configureEclipseWtpComponent(Project project) {
        configureEclipseWtpComponentWithType(project, WarPlugin)
        configureEclipseWtpComponentWithType(project, EarPlugin)
    }

    private void configureEclipseWtpComponentWithType(Project project, Class<?> type) {
        project.plugins.withType(type) {
            maybeAddTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                //task properties:
                description = 'Generates the Eclipse WTP component settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.component')
                outputFile = project.file('.settings/org.eclipse.wst.common.component')

                //model properties:
                model.wtp.component = component

                component.deployName = project.name

                if (WarPlugin.class.isAssignableFrom(type)) {
                    component.libConfigurations = [project.configurations.runtime]
                    component.minusConfigurations = [project.configurations.providedRuntime]
                    component.conventionMapping.contextPath = { project.war.baseName }
                    component.resource deployPath: '/', sourcePath: project.convention.plugins.war.webAppDirName // TODO: not lazy
                    component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                } else if (EarPlugin.class.isAssignableFrom(type)) {
                    component.rootConfigurations = [project.configurations.deploy]
                    component.libConfigurations = [project.configurations.earlib]
                    component.minusConfigurations = []
                    component.classesDeployPath = "/"
                    component.libDeployPath = "/lib"
                    project.plugins.withType(JavaPlugin) {
                        component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                    }
                }
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                def eclipsePlugin = otherProject.plugins.getPlugin(EclipsePlugin)
                // require Java plugin because we need source set 'main'
                // (in the absence of 'main', it probably makes no sense to write the file)
                otherProject.plugins.withType(JavaPlugin) {
                    maybeAddTask(otherProject, eclipsePlugin, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                        //task properties:
                        description = 'Generates the Eclipse WTP component settings file.'
                        inputFile = otherProject.file('.settings/org.eclipse.wst.common.component')
                        outputFile = otherProject.file('.settings/org.eclipse.wst.common.component')

                        //model properties:
                        eclipsePlugin.model.wtp.component = component

                        component.deployName = otherProject.name
                        component.conventionMapping.resources = {
                            getMainSourceDirs(otherProject).collect { new WbResource("/", otherProject.relativePath(it)) }
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseWtpFacet(Project project) {
        configureEclipseWtpFacetWithType(project, WarPlugin)
        configureEclipseWtpFacetWithType(project, EarPlugin)
    }

    private void configureEclipseWtpFacetWithType(Project project, Class<?> type) {
        project.plugins.withType(type) {
            maybeAddTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                //task properties:
                description = 'Generates the Eclipse WTP facet settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                //model properties:
                model.wtp.facet = facet
                if (WarPlugin.isAssignableFrom(type)) {
                    facet.conventionMapping.facets = { [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                        new Facet(FacetType.installed, "jst.web", "2.4"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.sourceCompatibility))] }
                } else if (EarPlugin.isAssignableFrom(type)) {
                    facet.conventionMapping.facets = { [new Facet(FacetType.fixed, "jst.ear", null), new Facet(FacetType.installed, "jst.ear", "5.0")] }
                }
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                def eclipsePlugin = otherProject.plugins.getPlugin(EclipsePlugin)
                maybeAddTask(otherProject, eclipsePlugin, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                    //task properties:
                    description = 'Generates the Eclipse WTP facet settings file.'
                    inputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                    outputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                    //model properties:
                    eclipsePlugin.model.wtp.facet = facet

                    facet.conventionMapping.facets = { [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                        new Facet(FacetType.installed, "jst.utility", "1.0")] }
                    otherProject.plugins.withType(JavaPlugin) {
                        facet.conventionMapping.facets = {
                            [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null), 
                                new Facet(FacetType.installed, "jst.utility", "1.0"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(otherProject.sourceCompatibility))]
                        }
                    }
                }
            }
        }
    }

    // TODO: might have to search all class paths of all source sets for project dependendencies, not just runtime configuration
    private void doLaterWithEachDependedUponEclipseProject(Project project, Closure action) {
        project.gradle.projectsEvaluated {
            eachDependedUponEclipseProject(project, action)
        }
    }

    private void eachDependedUponEclipseProject(Project project, Closure action) {
        def runtimeConfig = project.configurations.findByName("runtime")
        if (runtimeConfig) {
            def projectDeps = runtimeConfig.getAllDependencies(ProjectDependency)
            def dependedUponProjects = projectDeps*.dependencyProject
            for (dependedUponProject in dependedUponProjects) {
                dependedUponProject.plugins.withType(EclipsePlugin) { action(dependedUponProject) }
                eachDependedUponEclipseProject(dependedUponProject, action)
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

    private void maybeAddTask(Project project, EclipsePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) { return }
        def task = project.tasks.add(taskName, taskType)
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
