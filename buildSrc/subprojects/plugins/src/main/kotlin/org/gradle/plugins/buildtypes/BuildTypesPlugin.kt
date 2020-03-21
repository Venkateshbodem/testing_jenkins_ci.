package org.gradle.plugins.buildtypes

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.*


class BuildTypesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val buildTypes = container(BuildType::class)
        extensions.add("buildTypes", buildTypes)
        buildTypes.all {
            register(this)
            val activeBuildTypes = buildTypes.filter { it.active }
            require(activeBuildTypes.size <= 1) {
                "Only one build type can be active. Active build types: ${activeBuildTypes.joinToString(", ") { it.name }}"
            }
        }
    }

    private
    fun Project.register(buildType: BuildType) {
        val buildTypeTask = tasks.register(buildType.name) {

            group = "Build Type"

            description = "The $name build type (can only be abbreviated to '${buildType.abbreviation}')"

            doFirst {
                if (!gradle.startParameter.taskNames.any { it == buildType.name || it == buildType.abbreviation }) {
                    throw GradleException("'$name' is a build type and must be invoked directly, and its name can only be abbreviated to '${buildType.abbreviation}'.")
                }
            }
        }

        val invokedTaskNames = gradle.startParameter.taskNames
        val usedTaskNames = buildType.findUsedTaskNamesWithIndexIn(invokedTaskNames).reversed()
        usedTaskNames.forEach { (_, usedName) ->
            require(usedName.isNotEmpty())
            buildType.active = true
            buildType.onProjectProperties = { properties: ProjectProperties ->
                properties.forEach { (name, value) ->
                    project.setOrCreateProperty(name, value)
                }
            }
        }
        if (usedTaskNames.isNotEmpty()) {
            afterEvaluate {
                usedTaskNames.forEach { (index, usedName) ->
                    invokedTaskNames.removeAt(index)
                    val subproject = usedName.substringBeforeLast(":", "")
                    insertBuildTypeTasksInto(invokedTaskNames, buildTypeTask, index, buildType, subproject)
                }
                if (!invokedTaskNames.contains(buildType.name)) {
                    invokedTaskNames.add(buildType.name)
                }
                gradle.startParameter.setTaskNames(invokedTaskNames)
            }
        }
    }

    private
    fun BuildType.findUsedTaskNamesWithIndexIn(taskNames: List<String>): List<IndexedValue<String>> {
        val candidates = arrayOf(name, abbreviation)
        val nameSuffix = ":$name"
        val abbreviationSuffix = ":$abbreviation"
        return taskNames.withIndex().filter { (index, taskName) ->
            (taskName in candidates || taskName.endsWith(nameSuffix) || taskName.endsWith(abbreviationSuffix)) && !isTaskHelpInvocation(taskNames, index)
        }
    }

    private
    fun isTaskHelpInvocation(taskNames: List<String>, taskIndex: Int) =
        taskIndex >= 2
            && taskNames[taskIndex - 1] == "--task"
            && taskNames[taskIndex - 2].let(helpTaskRegex::matches)

    private
    val helpTaskRegex = Regex("h(e(lp?)?)?")
}


internal
fun Project.findTaskNameOrEmptyList(taskName: String) = tasks.findByName(taskName)?.let { listOf(it.path) } ?: emptyList()


internal
fun Project.insertBuildTypeTasksInto(
    invokeTaskNames: MutableList<String>,
    buildTypeTask: TaskProvider<Task>,
    index: Int,
    buildType: BuildType,
    subproject: String
) {

    fun insert(tasks: List<String>) = tasks.forEach { task ->
        buildTypeTask.configure { dependsOn(task) }
        invokeTaskNames.add(index, task)
    }

    fun forEachBuildTypeTask(act: (String) -> Unit) =
        buildType.tasks.reversed().forEach(act)

    when {
        subproject.isEmpty() ->
            forEachBuildTypeTask { taskInBuildType ->
                if (taskInBuildType.startsWith(":")) {
                    insert(listOf(taskInBuildType))
                } else {
                    insert(subprojects.flatMap { it.findTaskNameOrEmptyList(taskInBuildType) })
                }
            }

        findProject(subproject) != null ->
            forEachBuildTypeTask { taskInBuildType ->
                if (taskInBuildType.startsWith(":")) {
                    insert(listOf(taskInBuildType))
                } else {
                    val taskPath = "$subproject:$taskInBuildType"
                    when {
                        tasks.findByPath(taskPath) == null -> println("Skipping task '$taskPath' requested by build type ${buildType.name}, as it does not exist.")
                        else -> insert(listOf(taskPath))
                    }
                }
            }
        else -> {
            println("Skipping execution of build type '${buildType.name}'. Project '$subproject' not found in root project '$name'.")
        }
    }

    if (invokeTaskNames.isEmpty()) {
        invokeTaskNames.add("help") // do not trigger the default tasks
    }
}


fun Project.setOrCreateProperty(propertyName: String, value: Any) {
    when {
        hasProperty(propertyName) -> setProperty(propertyName, value)
        else -> extra.set(propertyName, value)
    }
}
