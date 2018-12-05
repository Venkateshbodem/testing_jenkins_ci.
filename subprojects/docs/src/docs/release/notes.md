The Gradle team is excited to announce Gradle 5.1.

This release features [Stricter validation with `validateTaskProperties`](userguide/java_gradle_plugin.html), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
[Mike Kobit](https://github.com/mkobit),
[Kent Fletcher](https://github.com/fletcher-sumglobal),
[Niklas Grebe](https://github.com/ThYpHo0n),
[Jonathan Leitschuh](https://github.com/JLLeitschuh),
[Sebastian Schuberth](https://github.com/sschuberth),
[Dan Sănduleac](https://github.com/dansanduleac),
[Olivier Voortman](https://github.com/blop),
[Alex Saveau](https://github.com/SUPERCILEX)
[Till Krullmann](https://github.com/tkrullmann)

## Use plugins from included build using the `plugins { }` block 

The `plugins { }` block in build scripts can now be used to refer to plugins defined in included builds.

## Stricter validation with `validateTaskProperties`

Cacheable tasks are validated stricter than non-cacheable tasks by the `validateTaskProperties` task, which is added automatically by the [`java-gradle-plugin`](userguide/java_gradle_plugin.html).
For example, all file inputs are required to have a normalization declared, like e.g. `@PathSensitive(RELATIVE)`.
This stricter validation can now be enabled for all tasks via [`validateTaskProperties.enableStricterValidation = true`](javadoc/org/gradle/plugin/devel/tasks/ValidateTaskProperties.html#setEnableStricterValidation-boolean-).

## Filter tasks with `--group`

Instead of listing all available tasks, [`gradle tasks` can now show only the tasks](userguide/command_line_interface.html#sec:listing_tasks) belonging to a particular group. This makes it easier to find tasks in builds with many available tasks.

This feature was contributed by [Alex Saveau](https://github.com/SUPERCILEX).

## Repository content filtering

It is now possible to match repositories to dependencies, so that Gradle doesn't search for a dependency in a repository if it's never going to be found there.

Example:
```
repositories {
    maven {
        url "https://repo.mycompany.com"
        content {
           includeGroupByRegex "com\\.mycompany.*"
        }
    }
}
```

This filtering can also be used to separate snapshot repositories from release repositories.

Look at the [userguide](userguide/declaring_repositories.html#sec::matching_repositories_to_dependencies) for more details.

## Improvements for plugin authors

### Conveniences for properties of type Map

This release includes a lazy [`MapProperty`](javadoc/org/gradle/api/provider/MapProperty.html) type which allows efficient configuration of maps in the Gradle model.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Configuration avoidance for Tasks

[In a recent blog post](https://blog.gradle.org/preview-avoiding-task-configuration-time), we described a new API for creating and configuration `Task` instances that allow Gradle to avoid creating and configuring tasks that do not need to be executed.

In this release, we now [recommend that you use this new API when working with tasks](userguide/task_configuration_avoidance.html). 

By default, the Kotlin DSL [uses the new APIs](current/userguide/kotlin_dsl.html#type-safe-accessors).

"Old style" task declarations in the Groovy DSL continue to be eager and force creation/configuration of any tasks created this way.
```
// "Old style" task declaration uses Task create API.  This always creates a task.
task myTask(type: MyTask) {
    // This is always called
}
```

In Groovy, to use the new API:
```
tasks.register("myTask", MyTask) {
    
}
```

The existing API is not deprecated, but as builds transition to the new API, we will consider deprecating the API in a future release.

## Fixed issues

### Inherited configuration-wide dependency excludes are now published

Previously, only exclude rules directly declared on published configurations (e.g. `apiElements` and `runtimeElements` for the `java` component defined by the [Java Library Plugin](userguide/java_library_plugin.html#sec:java_library_configurations_graph)) were published in the Ivy descriptor and POM when using the [Ivy Publish Plugin](userguide/publishing_ivy.html) or [Maven Publish Plugins](userguide/publishing_maven.html), respectively.
Now, inherited exclude rules defined on extended configurations (e.g. `api` for Java libraries) are also taken into account.

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version. See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

### Setters for `classes` and `classpath` on `ValidateTaskProperties`

There should not be setters for lazy properties like `ConfigurableFileCollection`s.
Use `setFrom` instead.

    validateTaskProperties.getClasses().setFrom(fileCollection)
    validateTaskProperties.getClasspath().setFrom(fileCollection)
    
### Breaking changes

<!-- add any notable changes here in a summary -->

See the [Gradle 5.x upgrade guide](userguide/upgrading_version_5.html) to learn about breaking changes and considerations for upgrading from Gradle 5.x.

### Changes to native linking tasks

To expand our idiomatic [Provider API](userguide/lazy_configuration.html) practices, the install name property from `org.gradle.nativeplatform.tasks.LinkSharedLibrary` is affected by this change.
- `getInstallName()` was changed to return a `Property`.
- `setInstallName(String)` was removed. Use `Property.set()` instead.
    
## Potential breaking changes

<!--
### Example breaking change
-->
### Worker API: working directory of a worker can no longer be set 

Since JDK 11 no longer supports changing the working directory of a running process, setting the working directory of a worker via its fork options is now prohibited.
All workers now use the same working directory to enable reuse.
Please pass files and directories as arguments instead.

### Passing arguments to Windows Resource Compiler

To follow idiomatic [Provider API](userguide/lazy_configuration.html) practices, the `WindowsResourceCompile` task has been converted to use the Provider API.

Passing additional compiler arguments now follow the same pattern as the `CppCompile` and other tasks.

### Copied configuration no longer shares a list of `beforeResolve` actions with original

The fix for gradle/gradle#6996 means that the list of `beforeResolve` actions are no longer shared between a copied configuration and the original.
Instead, a copied configuration receives a copy of the `beforeResolve` actions at the time the copy is made.
Any `beforeResolve` actions added after copying (to either configuration) will not be shared between the original and the copy.
This may break plugins that relied on the previous behaviour.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Mike Kobit](https://github.com/mkobit) - Add missing `@Deprecated` annotations to `ProjectLayout` methods (gradle/gradle#7344)
 - [Kent Fletcher](https://github.com/fletcher-sumglobal) - Convert `WindowsResourceCompile` to use Provider API (gradle/gradle#7432)
 - [Niklas Grebe](https://github.com/ThYpHo0n) - Add more examples of dynamic versions to documentation (gradle/gradle#7417)
 - [Jonathan Leitschuh](https://github.com/JLLeitschuh) - Add Provider API types to `AbstractArchiveTask` task types (gradle/gradle#7435)
 - [Sebastian Schuberth](https://github.com/sschuberth) - Improve init-command comments for Kotlin projects (gradle/gradle#7592)
 - [Sebastian Schuberth](https://github.com/sschuberth) - BuildScriptBuilder: Do not print a trailing space for empty header lines (gradle/gradle#7887)
 - [Dan Sănduleac](https://github.com/dansanduleac) - Don't share dependency resolution listeners list when copying configuration (gradle/gradle#6996)
 - [Olivier Voortman](https://github.com/blop) - Do not use a timestamp of 0 for tar file entries (gradle/gradle#7577).
 - [John Bennewitz](https://github.com/b-john) - Allow C++ binary to relocate on Linux (gradle/gradle#6176)
 - [Alex Saveau](https://github.com/SUPERCILEX) - Add option to display tasks from a specific group only (gradle/gradle#7788) 
 - [Till Krullmann](https://github.com/tkrullmann) - Add `MapProperty` (gradle/gradle#6863)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
