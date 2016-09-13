## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->
### Location of deprecation warning in build file is shown

For each deprecation warning Gradle now prints its location in the
build file to the console. When passing `-s` or `-S` as a command
line option to Gradle then the whole stack trace is printed out.
This should make it much easier to spot and fix those warnings.

### The Wrapper can now use HTTP Basic Authentication to download distributions

The Gradle Wrapper can now download Gradle distributions from a server requiring authentication.
This allows you to host the Gradle distribution on a private server protected with HTTP Basic Authentication.

See the User guide section on “[authenticated distribution download](userguide/gradle_wrapper.html#sec:authenticated_download)“ for more information.

As stated in the User guide, please note that this shouldn't be used over insecure connections.

### Generate `gradle-plugin` template project with `init`

The [Build Init plugin](userguide/build_init_plugin.html) can now generate a complete Gradle plugin template project.  The generated project has a custom Gradle plugin, task and uses Gradle TestKit and Spock.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 4.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](https://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

<!--
### Example breaking change
-->

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

- [Vladislav Bauer](https://github.com/vbauer) - Remove code duplication in Jacoco plugin
- [Shintaro Katafuchi](https://github.com/hotchemi) - Fixed typo in `ShadedJar.java` under `buildSrc`
- [Jörn Huxhorn](https://github.com/huxi) - Show location in build file for deprecation warning
- [Jeff Baranski](https://github.com/jbaranski) - Fix doc bug with turning off daemon in a .bat file
- [Justin Sievenpiper](https://github.com/jsievenpiper) - Prevent navigating down to JDK classes (in TestFrameworkDetector)
- [Alex Proca](https://github.com/alexproca) - Limit Unix Start Scripts to use POSIX standard sh

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
