/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.buildinit.plugins.internal.maven

import org.apache.maven.project.MavenProject
import org.gradle.api.JavaVersion
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.util.RelativePathUtil

/**
 * This script obtains the effective POM of the current project, reads its dependencies
 * and generates build.gradle scripts. It also generates settings.gradle for multimodule builds. <br/>
 *
 * It currently supports both single-module and multi-module POMs, inheritance, dependency management, properties - everything.
 */
class Maven2Gradle {

    BuildInitDsl dsl
    def dependentWars = []
    def qualifiedNames
    def workingDir
    def effectivePom

    Logger logger = Logging.getLogger(getClass())
    private Set<MavenProject> mavenProjects

    Maven2Gradle(BuildInitDsl dsl, Set<MavenProject> mavenProjects, File workingDir) {
        assert !mavenProjects.empty: "No Maven projects provided."
        this.dsl = dsl;
        this.mavenProjects = mavenProjects
        this.workingDir = workingDir.canonicalFile;
    }

    def convert() {
        //For now we're building the effective POM XML from the model
        //and then we parse the XML using slurper.
        //This way we don't have to rewrite the Maven2Gradle just yet.
        //Maven2Gradle should be rewritten (with coverage) so that feeds of the maven object model, not XML.
        def effectivePom = new MavenProjectXmlWriter().toXml(mavenProjects)
        //use the Groovy XmlSlurper library to parse the text string
        this.effectivePom = new XmlSlurper().parseText(effectivePom)

        String build
        def multimodule = this.effectivePom.name() == "projects"

        if (multimodule) {
            def allProjects = this.effectivePom.project
            qualifiedNames = generateSettings(workingDir.getName(), allProjects[0].artifactId, allProjects);
            def dependencies = [:];
            allProjects.each { project ->
                dependencies[project.artifactId.text()] = getDependencies(project, allProjects, false)
            }

            def commonDeps = dependencies.get(allProjects[0].artifactId.text())
            build = """allprojects  {
  ${imperativelyApplyPlugin("maven")}

  ${getArtifactData(allProjects[0])}
}

subprojects {
  ${imperativelyApplyPlugin("java")}
  ${compilerSettings(allProjects[0], "  ", false)}
  ${packageSources(allProjects[0])}
  ${getRepositoriesForProjects(allProjects)}
  ${globalExclusions(allProjects[0])}
  ${commonDeps}
  ${testNg(commonDeps)}
}
"""
            modules(allProjects, false).each { module ->
                def id = module.artifactId.text()
                String moduleDependencies = dependencies.get(id)
                boolean warPack = module.packaging.text().equals("war")
                def hasDependencies = !(moduleDependencies == null || moduleDependencies.length() == 0)
                def submoduleBuildFilename = dsl.fileNameFor("build")
                File submoduleBuildFile = new File(projectDir(module), submoduleBuildFilename)

                def group = ''
                if (module.groupId != allProjects[0].groupId) {
                    group = withDsl(
                        GROOVY: { "group = '${module.groupId}'\n" },
                        KOTLIN: { "group = \"${module.groupId}\"\n" })
                }
                String moduleBuild = ""
                if (warPack) {
                    moduleBuild += "${declarativelyApplyPlugins(["war"])}\n"
                }
                moduleBuild += "${group}\n"
                if (warPack) {
                    if (dependentWars.any { project ->
                        project.groupId.text() == module.groupId.text() &&
                            project.artifactId.text() == id
                    }) {
                        moduleBuild += "jar.enabled = true\n"
                    }
                }
                if (module.name) {
                    moduleBuild += withDsl(
                        GROOVY: { "description = '${module.name}'\n" },
                        KOTLIN: { "description = \"${module.name}\"\n" })
                }


                if (hasDependencies) {
                    moduleBuild += moduleDependencies
                }

                moduleBuild += testNg(moduleDependencies)

                if (submoduleBuildFile.exists()) {
                    submoduleBuildFile.renameTo(new File(projectDir(module), "${submoduleBuildFilename}.bak"))
                }
                def packageTests = packageTests(module, false);
                if (packageTests) {
                    moduleBuild += packageTests;
                }

                logger.debug("writing $submoduleBuildFilename file at ${submoduleBuildFile.absolutePath}");
                submoduleBuildFile.text = moduleBuild
            }
            //TODO deployment
        } else {//simple
            build = """${declarativelyApplyPlugins(["java", "maven"])}

${getArtifactData(this.effectivePom)}

description = \"""${this.effectivePom.name}\"""

${compilerSettings(this.effectivePom, "")}
${globalExclusions(this.effectivePom)}

"""

            Set<String> repoSet = new LinkedHashSet<String>();
            getRepositoriesForModule(this.effectivePom, repoSet)
            String repos = """repositories {
        $localRepoUri
"""
            repoSet.each {
                repos = "${repos} ${it}\n"
            }
            build += "${repos}}\n"
            String dependencies = getDependencies(this.effectivePom, null)
            build += dependencies

            String packageTests = packageTests(this.effectivePom);
            if (packageTests) {
                build += '//packaging tests\n'
                build += packageTests
            }
            generateSettings(workingDir.getName(), this.effectivePom.artifactId, null);
        }
        def buildFilename = dsl.fileNameFor("build")
        def buildFile = new File(workingDir, buildFilename)
        if (buildFile.exists()) {
            buildFile.renameTo(new File(workingDir, "${buildFilename}.bak"))
        }
        logger.debug("writing ${buildFilename} file at ${buildFile.absolutePath}");
        buildFile.text = build
    }

    def imperativelyApplyPlugin = { pluginId ->
        withDsl(
            GROOVY: { "apply plugin: '$pluginId'\n" },
            KOTLIN: { "apply { plugin(\"$pluginId\") }\n" })
    }

    def declarativelyApplyPlugins = { List<String> pluginIds ->
        String script = "plugins {\n"
        pluginIds.each { pluginId ->
            script += withDsl(
                GROOVY: { "  id '$pluginId'\n" },
                KOTLIN: { "    $pluginId\n" })
        }
        return script + "}\n"
    }

    def globalExclusions = { project ->
        def exclusions = ''
        def enforcerPlugin = plugin('maven-enforcer-plugin', project)
        def enforceGoal = pluginGoal('enforce', enforcerPlugin)
        if (enforceGoal) {
            exclusions += 'configurations.all {\n'
            enforceGoal.configuration.rules.bannedDependencies.excludes.childNodes().each {
                def tokens = it.text().tokenize(':')
                def group = tokens[0]
                def module = tokens.size() > 1 && tokens[1] != '*' ? tokens[1] : null
                exclusions += withDsl(
                    GROOVY: { "it.exclude group: '$group'${module ? ", module: '$module'" : ''}\n" },
                    KOTLIN: { "exclude(group = \"$group\"${module ? ", module = \"$module\"" : ''})\n" })
            }
        }
        exclusions = exclusions ? exclusions += '}' : exclusions
        exclusions
    }

    def testNg(moduleDependencies) {
        if (moduleDependencies.contains('testng')) {
            withDsl(
                GROOVY: { 'test.useTestNG()\n' },
                KOTLIN: { 'val test by tasks.getting(Test::class) {\n    useTestNG()\n}\n' })
        } else {
            ''
        }
    }

    def modules = { allProjects, incReactors ->
        return allProjects.findAll { project ->
            def parentIsPartOfThisBuild = allProjects.find { proj ->
                proj.artifactId == project.parent.artifactId && proj.groupId == project.parent.groupId
            }
            project.parent.text().length() > 0 && parentIsPartOfThisBuild && (incReactors || project.packaging.text() != 'pom')
        }
    }

    def fqn = { project, allProjects ->
        def buffer = new StringBuilder()
        generateFqn(project, allProjects, buffer)
        return buffer.toString()
    }

    private generateFqn(def project, def allProjects, StringBuilder buffer) {
        def artifactId = project.artifactId.text()
        buffer.insert(0, ":${artifactId}")
        //we don't need the top-level parent in gradle, so we stop on it
        if (project.parent.artifactId.text() != allProjects[0].artifactId.text()) {
            def parentInBuild = allProjects.find { proj ->
                proj.artifactId.text() == project.parent.artifactId.text()
            }
            if (parentInBuild) {
                generateFqn(parentInBuild, allProjects, buffer)
            }
        }
    }

    def localRepoUri = {
        """mavenLocal()
    """
    }

    private String getArtifactData(project) {
        return withDsl(
            GROOVY: { "group = '$project.groupId'\nversion = '$project.version'" },
            KOTLIN: { "group = \"$project.groupId\"\nversion = \"$project.version\"" })
    }

    private String getRepositoriesForProjects(projects) {
        String repos = """repositories {
    ${localRepoUri()}
"""
        def repoSet = new LinkedHashSet<String>();
        projects.each {
            getRepositoriesForModule(it, repoSet)
        }
        repoSet.each {
            repos = "${repos}${it}\n"
        }
        repos = "${repos}  }\n"
        return repos
    }

    private void getRepositoriesForModule(module, repoSet) {
        module.repositories.repository.each { repo ->
            repoSet.add(withDsl(
                GROOVY: { "  maven { url '${repo.url}' }\n" },
                KOTLIN: { "    maven { url = uri(\"${repo.url}\") }\n" }))
        }
        //No need to include plugin repos - who cares about maven plugins?
    }

    private String getDependencies(project, allProjects, useKotlinAccessors = true) {
        // use GPath to navigate the object hierarchy and retrieve the collection of dependency nodes.
        def dependencies = project.dependencies.dependency
        def war = project.packaging == "war"

        def compileTimeScope = []
        def runTimeScope = []
        def testScope = []
        def providedScope = []
        def systemScope = []

        //cleanup duplicates from parent
        // using Groovy Looping and mapping a Groovy Closure to each element, we collect together all
        // the dependency nodes into corresponding collections depending on their scope value.
        dependencies.each() {
            if (!duplicateDependency(it, project, allProjects)) {
                def scope = (elementHasText(it.scope)) ? it.scope : "compile"
                switch (scope) {
                    case "compile":
                        compileTimeScope.add(it)
                        break
                    case "test":
                        testScope.add(it)
                        break
                    case "provided":
                        providedScope.add(it)
                        break
                    case "runtime":
                        runTimeScope.add(it)
                        break
                    case "system":
                        systemScope.add(it)
                        break
                }
            }
        }

        /**
         * print function then checks the exclusions node to see if it exists, if
         * so it branches off, otherwise we call our simple print function
         */
        def createGradleDep = { String scope, StringBuilder sb, mavenDependency ->
            def projectDep = allProjects.find { prj ->
                return prj.artifactId.text() == mavenDependency.artifactId.text() && prj.groupId.text() == mavenDependency.groupId.text()
            }

            if (projectDep) {
                createProjectDependency(projectDep, sb, scope, allProjects, useKotlinAccessors)
            } else {
                if (!war && scope == 'providedCompile') {
                    scope = 'compileOnly'
                }
                def exclusions = mavenDependency.exclusions.exclusion
                if (exclusions.size() > 0) {
                    createComplexDependency(mavenDependency, sb, scope, useKotlinAccessors)
                } else {
                    createBasicDependency(mavenDependency, sb, scope, useKotlinAccessors)
                }
            }
        }


        StringBuilder build = new StringBuilder()
        if (!compileTimeScope.isEmpty() || !runTimeScope.isEmpty() || !testScope.isEmpty() || !providedScope.isEmpty() || !systemScope.isEmpty()) {
            build.append("dependencies {").append("\n")
            // for each collection, one at a time, we take each element and call our print function
            if (!compileTimeScope.isEmpty()) {
                compileTimeScope.each() { createGradleDep("compile", build, it) }
            }
            if (!runTimeScope.isEmpty()) {
                runTimeScope.each() { createGradleDep("runtime", build, it) }
            }
            if (!testScope.isEmpty()) {
                testScope.each() { createGradleDep("testCompile", build, it) }
            }
            if (!providedScope.isEmpty()) {
                providedScope.each() { createGradleDep("providedCompile", build, it) }
            }
            if (!systemScope.isEmpty()) {
                systemScope.each() { createGradleDep("system", build, it) }
            }
            build.append("}\n")
        }
        return build.toString();
    }

    def compilerSettings(project, indent, useKotlinAccessors = true) {
        def configuration = plugin('maven-compiler-plugin', project).configuration
        def encoding = project.properties.'project.build.sourceEncoding'.text()
        def settings = new StringBuilder()
        withDsl(
            GROOVY: {
                settings.append "sourceCompatibility = ${configuration.source.text() ?: '1.5'}\n"
                settings.append "${indent}targetCompatibility = ${configuration.target.text() ?: '1.5'}\n"
                if (encoding) {
                    settings.append "${indent}tasks.withType(JavaCompile) {\n"
                    settings.append "${indent}  options.encoding = '${encoding}'\n"
                    settings.append "${indent}}\n"
                }
            },
            KOTLIN: {
                if (useKotlinAccessors) {
                    settings.append "${indent}java {\n"
                } else {
                    settings.append "${indent}configure<JavaPluginConvention> {\n"
                }
                JavaVersion sourceCompatibility = configuration.source.text() ? JavaVersion.toVersion(configuration.source.text()) : JavaVersion.VERSION_1_5
                JavaVersion targetCompatibility = configuration.target.text() ? JavaVersion.toVersion(configuration.target.text()) : JavaVersion.VERSION_1_5
                settings.append "${indent}    sourceCompatibility = JavaVersion.${sourceCompatibility.name()}\n"
                settings.append "${indent}    targetCompatibility = JavaVersion.${targetCompatibility.name()}\n"
                settings.append "${indent}}\n"
                if (encoding) {
                    settings.append "${indent}tasks.withType<JavaCompile> {\n"
                    settings.append "${indent}    options.encoding = \"${encoding}\"\n"
                    settings.append "${indent}}\n"
                }
            })
        return settings
    }

    def plugin = { artifactId, project ->
        project.build.plugins.plugin.find { pluginTag ->
            pluginTag.artifactId.text() == artifactId
        }
    }

    def pluginGoal = { goalName, plugin ->
        plugin.executions.execution.find { exec ->
            exec.goals.goal.find { gl ->
                gl.text().startsWith(goalName)
            }
        }
    }

    def packSources = { sourceSets ->
        def sourceSetStr = ''
        if (!sourceSets.empty) {
            withDsl(
                GROOVY: {
                    sourceSetStr = """task packageSources(type: Jar) {
classifier = 'sources'
"""
                    sourceSets.each { sourceSet ->
                        sourceSetStr += "  from sourceSets.${sourceSet}.allSource\n"
                    }
                    sourceSetStr += """
}
artifacts.archives packageSources"""
                },
                KOTLIN: {
                    sourceSetStr = """val packageSources by tasks.creating(Jar::class) {
    classifier = "sources"
"""
                    sourceSets.each { sourceSet ->
                        sourceSetStr += "    from(sourceSets[\"$sourceSet\"].allSource)\n"
                    }
                    sourceSetStr += """}
artifacts.add("archives", packageSources)
"""
                })
        }
        sourceSetStr
    }

    def packageTests(project, useKotlinAccessors = true) {
        def jarPlugin = plugin('maven-jar-plugin', project)
        if (!pluginGoal('test-jar', jarPlugin)) {
            return ''
        }
        return withDsl(
            GROOVY: {
                """task packageTests(type: Jar) {
  from sourceSets.test.output
  classifier = 'tests'
}
artifacts.archives packageTests
"""
            },
            KOTLIN: {
                """val packageTests by tasks.creating(Jar::class) {
    from(${useKotlinAccessors ? "java" : "the<JavaPluginConvention>()"}.sourceSets["test"].output)
    classifier = "tests"
}
artifacts.add("archives", packageTests)
"""
            })
    }

    def packageSources = { project ->
        def sourcePlugin = plugin('maven-source-plugin', project)
        def sourceSets = []
        if (sourcePlugin) {
            if (pluginGoal('jar', sourcePlugin)) {
                sourceSets += 'main'
            } else if (pluginGoal('test-jar', sourcePlugin)) {
                sourceSets += 'test'
            }
        }
        packSources(sourceSets)
    }

    private boolean duplicateDependency(dependency, project, allProjects) {
        def parentTag = project.parent
        if (allProjects == null || parentTag.isEmpty()) {//simple project or no parent
            return false;
        } else {
            def parent = allProjects.find {
                it.groupId.equals(parentTag.groupId) && it.artifactId.equals(parentTag.artifactId)
            }
            def duplicate = parent.dependencies.dependency.any {
                it.groupId.equals(dependency.groupId) && it.artifactId.equals(dependency.artifactId)
            }
            if (duplicate) {
                return true;
            } else {
                duplicateDependency(dependency, parent, allProjects)
            }
        }
    }

    def artifactId = { File dir ->
        return new XmlSlurper().parse(new File(dir, 'pom.xml')).artifactId.text()
    }

    def projectDir = { project ->
        return new File(project.build.directory.text()).parentFile
    }

    private def generateSettings(def dirName, def mvnProjectName, def projects) {
        def qualifiedNames = [:]
        def projectName = "";
        if (dirName != mvnProjectName) {
            projectName = withDsl(
                GROOVY: { "rootProject.name = '${mvnProjectName}'\n" },
                KOTLIN: { "rootProject.name = \"${mvnProjectName}\"\n" })
        }
        def modulePoms = modules(projects, true)

        List<String> moduleNames = new ArrayList<String>();
        def artifactIdToDir = [:]
        if (projects) {
            modulePoms.each { project ->
                def fqn = fqn(project, projects)
                File projectDirectory = projectDir(project)
                // don't add project if it's the rootproject
                if (!workingDir.equals(projectDirectory)) {
                    artifactIdToDir[fqn] = RelativePathUtil.relativePath(workingDir, projectDirectory)
                    moduleNames.add(fqn)
                }
            }
        }
        def settingsFilename = dsl.fileNameFor("settings")
        File settingsFile = new File(workingDir, settingsFilename)
        if (settingsFile.exists()) {
            settingsFile.renameTo(new File(workingDir, "${settingsFilename}.bak"))
        }
        StringBuffer settingsText = new StringBuffer(projectName)
        if (moduleNames.size() > 0) {
            moduleNames.each { moduleName ->
                settingsText.append(withDsl(
                    GROOVY: { "include '$moduleName'\n" },
                    KOTLIN: { "include(\"$moduleName\")\n" }))
            }
        }

        artifactIdToDir.each { entry ->
            settingsText.append(withDsl(
                GROOVY: { "project('$entry.key').projectDir = file('${entry.value}')\n" },
                KOTLIN: { "project(\"$entry.key\").projectDir = file(\"${entry.value}\")\n" }))
        }
        settingsFile.text = settingsText.toString()
        return qualifiedNames
    }

    /**
     * complex print statement does one extra task which is
     * iterate over each <exclusion> node and print out the artifact id.
     * It also provides review comments for the user.
     */
    private void createComplexDependency(mavenDependency, build, scope, useKotlinAccessors) {
        def signature = contructSignature(mavenDependency)
        build.append(withDsl(
            GROOVY: {
                build.append("  ${scope}(${signature}) {\n")
                mavenDependency.exclusions.exclusion.each() {
                    build.append("  exclude(module: '${it.artifactId}')\n")
                }
                build.append("  }\n")
            },
            KOTLIN: {
                build.append("    ${useKotlinAccessors ? scope : "\"$scope\""}(${signature}) {\n")
                mavenDependency.exclusions.exclusion.each() {
                    build.append("    exclude(module = \"${it.artifactId}\")\n")
                }
                build.append("    }\n")
            }))
    }

    /**
     * Print out the basic form og gradle dependency
     */
    private void createBasicDependency(mavenDependency, build, String scope, useKotlinAccessors) {
        def signature = contructSignature(mavenDependency)
        build.append(withDsl(
            GROOVY: { "  ${scope} ${signature}\n" },
            KOTLIN: { "    ${useKotlinAccessors ? scope : "\"$scope\""}(${signature})\n" }))
    }

    /**
     * Print out the basic form of gradle dependency
     */
    private void createProjectDependency(projectDep, build, String scope, allProjects, useKotlinAccessors) {
        if (projectDep.packaging.text() == 'war') {
            dependentWars += projectDep
        }
        def fqn = fqn(projectDep, allProjects)
        build.append(withDsl(
            GROOVY: { "  ${scope} project('$fqn')\n" },
            KOTLIN: { "    ${useKotlinAccessors ? scope : "\"$scope\""}(project(\"$fqn\"))\n" }))
    }

    /**
     * Construct and return the signature of a dependency, including its version and
     * classifier if it exists
     */
    private String contructSignature(mavenDependency) {
        def groupId = mavenDependency.groupId.text()
        def artifactId = mavenDependency.artifactId.text()
        def version = mavenDependency.version?.text()
        def classifier = elementHasText(mavenDependency.classifier) ? mavenDependency.classifier.text().trim() : null
        return withDsl(
            GROOVY: { "group: '$groupId', name: '$artifactId', version: '$version'${classifier ? ", classifier: '$classifier'" : ''}" },
            KOTLIN: { "group = \"$groupId\", name = \"$artifactId\", version = \"$version\"${classifier ? ", classifier = \"$classifier\"" : ''}" })
    }

    private String withDsl(Map<String, Closure<String>> args) {
        return args[dsl.name()].call()
    }

    /**
     * Check to see if the selected node has content
     */
    private boolean elementHasText(it) {
        return it.text().length() != 0
    }
}
