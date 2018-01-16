## New and noteworthy

Here are the new features introduced in this Gradle release.

### Advanced POM support (preview)

Gradle now supports [additional features for modules with POM metadata](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc). This includes support for _optional dependencies_, _BOM import_ and _compile/runtime scope separation_. 

_Note:_ This is a _Gradle 5.0 feature preview_, which means it is a potentially breaking change that will be activated by default in Gradle 5.0. It can be turned on in Gradle 4.6+ by setting `org.gradle.advancedpomsupport=true` in _gradle.properties_.

### New Gradle `.module` metadata format (preview)

In order to provide rich support for variant-aware dependency management and dependency constraints, Gradle 5.0 will define a [new module metadata format](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/preview-features.adoc), that can be used in conjunction with Ivy descriptor and Maven POM files in existing repositories.

The new metadata format is still under active development, but it can already be used in its current state in Gradle 4.6+ by setting `org.gralde.gradlemetadata=true` in _gradle.properties_.

<!--
### Example new and noteworthy
-->

### Default JaCoCo version upgraded to 0.8.0

[The JaCoCo plugin](userguide/jacoco_plugin.html) has been upgraded to use [JaCoCo version 0.8.0](http://www.jacoco.org/jacoco/trunk/doc/changes.html) by default.

### Safer handling of task dependencies in file collections

File collections can contain the output of tasks, but if the file collection gets resolved before the tasks are executed, the files returned would not include the output of the tasks.
This can happen when a file collection is unintentionally resolved during configuration time (before any tasks are executed); or a file collection is resolved during the execution of a task, but the file collection is not declared as an input of the task.
Previously Gradle would silently ignore these kind of problems.
Since Gradle 4.6 an exception is thrown instead that clearly states the problem:

    * What went wrong:
    A problem occurred evaluating root project 'test'.
    > Cannot resolve outputs for task ':unexecutedTask' because it has not been executed yet.


## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### TestKit becomes public feature

TestKit was first introduced in Gradle 2.6 to support developers with writing and executing functional tests for plugins. In the course of the Gradle 2.x releases, a lot of new functionality was added. This version of Gradle removes the incubating status and makes TestKit a public feature.

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

### Local build cache directory cleanup is now time-based

Previously, Gradle would clean up the [local build cache directory](userguide/build_cache.html#sec:build_cache_configure_local) only if the size of its contents reached 5 GB, or whatever was configured in `targetSizeInMB`.
From now on Gradle will instead clean up everything older than 7 days, regardless of the size of the cache directory.
As a consequence `targetSizeInMB` is now deprecated, and changing its value has no effect.

The minimum age for entries to be cleaned up can now be configured in `settings.gradle` via the [`removeUnusedEntriesAfterDays`](dsl/org.gradle.caching.local.DirectoryBuildCache.html#org.gradle.caching.local.DirectoryBuildCache:removeUnusedEntriesAfterDays) property:

```
buildCache {
    local {
        removeUnusedEntriesAfterDays = 30
    }
}
```

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

 - [Sergei Dryganets](https://github.com/dryganets) - Improved gpg instructions in signing plugin documentation (gradle/gradle#4023)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
