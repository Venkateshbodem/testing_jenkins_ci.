## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

### Disabled equivalents for existing command line options

All Command line options that allow to enable a feature now also have an equivalent for disabling the feature. For example the command line option `--build-scan` supports `--no-build-scan` as well. Some of the existing command line options did not follow the same pattern. With this version of Gradle every boolean-based command line option also expose a "disabled" option. For more information please review the list of [command line options](userguide/gradle_command_line.html) in the user guide.

## Fixed issues


## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 5.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

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

- [Tomáš Polešovský](https://github.com/topolik) - Support for FindBugs JVM arguments (gradle/gradle#781)
- [Juan Martín Sotuyo Dodero](https://github.com/jsotuyod) - Support PMD's analysis cache (gradle/gradle#2223)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
