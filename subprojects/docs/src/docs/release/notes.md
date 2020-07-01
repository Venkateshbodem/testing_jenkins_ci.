The Gradle team is excited to announce Gradle @version@.

This release features [TBD Config cache](#configuration-cache), [release flag support](#support-java-cross-compilation-using-the-release-flag) and a new [Credentials Provider API](#credentials-provider-api).

There are also several other improvements including [injectable ArchiveOperations](#improvements-for-plugin-authors), [reproducible gradle metadata](#gradle-module-metadata-can-be-made-reproducible) and [many bug fixes](#fixed-issues). 

We would like to thank the following community contributors to this release of Gradle:

[Danny Thomas](https://github.com/DanielThomas),
[Daiki Hirabayashi](https://github.com/dhirabayashi),
[Sebastian Schuberth](https://github.com/sschuberth),
[Frieder Bluemle](https://github.com/friederbluemle),
[Brick Tamland](https://github.com/mleveill),
[Stefan Oehme](https://github.com/oehme),
[Yurii Serhiichuk](https://github.com/xSAVIKx),
[JunHyung Lim](https://github.com/EntryPointKR),
[Igor Dvorzhak](https://github.com/medb),
and [Leonid Gaiazov](https://github.com/gaiazov).

<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).

<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<!--
Add release features here!
## 1

details of 1

## 2

details of 2

## n
-->

## Configuration Cache

TBD

## Support Java cross-compilation using the `release` flag

TBD

## Improved handling of ZIP archives on runtime classpaths
For [up to date checks](userguide/more_about_tasks.html#sec:up_to_date_checks) and the build cache Gradle needs to determine if two task input properties have the same value. In order to do so, Gradle
first normalizes both inputs and then compares the result. Runtime classpath analysis can now inspect manifest and `META-INF` properties files, ignore changes to comments, and selectively ignore
attributes or properties that don't impact the runtime classpath.

```groovy
normalization {
    runtimeClasspath {
        metaInf {
            ignoreAttribute("Implementation-Version")
            ignoreProperty("timestamp")
        }
    }
}
```

This improves the likelihood of [build cache hits](userguide/build_cache.html) when jar and property files on the classpath are regenerated and only differ by unimportant values or comments.

See the [userguide](userguide/more_about_tasks.html#sec:meta_inf_normalization) for further information.  Note that this API is incubating and will likely change in future releases as support
is expanded for normalizing properties files outside of `META-INF`.

## Gradle module metadata can be made reproducible

The Gradle Module Metadata file contains a build identifier field which defaults to a unique ID generated during build execution.
This results in the generated file being different at each build execution.

This value can now be disabled at the publication level, allowing users to opt-in for a reproducible Gradle Module Metadata file.

```groovy
main(MavenPublication) {
    from components.java
    withoutBuildIdentifier()
}
```

See the documentation for more information on [Gradle Module Metadata generation](userguide/publishing_gradle_module_metadata.html#sub:gmm-reproducible).

## Variant-aware dependency substitution rules

Previously, it wasn't possible for Gradle to substitute a dependency which uses a classifier with a dependency without classifier, nor was it possible to substitute a dependency _without_ classifier with a classified dependency.
Similarly, dependencies with attributes (typically "platform" dependencies) or capabilities (typically "test fixtures" dependencies) could not be substituted.

Gradle now supports substitution of dependencies with classifiers, attributes or capabilities.
Gradle's dependency substitution API has been enriched to cover those cases.

```kotlin
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(platform(module("com.google.guava:guava:28.2-jre")))
            .using(module("com.google.guava:guava:28.2-jre"))
    }
}
```

See the documentation on [variant-aware substitution](userguide/resolution_rules.html#sec:variant_aware_substitutions) for details.

## Credentials Provider API

In the previous release we added two samples that demonstrated two common use cases where credentials are used in Gradle builds -
artifact repositories requiring authentication and arbitrary external tools invoked by the Gradle build requiring credentials.
Both samples aimed at guiding the users to our recommended practice of externalizing credentials from the build scripts and
supplying them using `gradle.properties`.

In this release we are rolling out a new provider API for credentials that will make working with credentials easier by establishing
a convention to supply credentials using `gradle.properties` and eliminating the previously demonstrated boilerplate code for
validating credential presence.

For more details on the new API see the [user manual](userguide/declaring_repositories.html#sec:handling_credentials) as well as 
updated downloadable samples that now make use of the new credentials provider API:

- [Authenticating with a Maven repository for publishing](samples/sample_publishing_credentials.html)
- [Supplying credentials to an external tool](samples/sample_publishing_credentials.html)

## Improvements for plugin authors

### Injectable `ArchiveOperations` service

Previously, it was only possible to create a `FileTree` for a ZIP or TAR archive by using the APIs provided by a `Project`.
However, a `Project` object is not always available.

The new `ArchiveOperations` service has [zipTree()](javadoc/org/gradle/api/file/ArchiveOperations.html#zipTree-java.lang.Object-) and [tarTree()](javadoc/org/gradle/api/file/ArchiveOperations.html#tarTree-java.lang.Object-) methods for creating read-only `FileTree` instances respectively for ZIP and TAR archives.

See the [user manual](userguide/custom_gradle_types.html#service_injection) for how to inject services and the [`ArchiveOperations`](javadoc/org/gradle/api/file/ArchiveOperations.html) api documentation for more details and examples.


## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
