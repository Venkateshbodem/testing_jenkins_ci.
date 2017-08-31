## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Support for Google Cloud Storage backed repositories

It is now possible to consume dependencies from, and publish to, [Google Cloud Storage](https://cloud.google.com/storage/) buckets when using [`MavenArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.MavenArtifactRepository.html) or [`IvyArtifactRepository`](dsl/org.gradle.api.artifacts.repositories.IvyArtifactRepository.html).

    repositories {
        maven {
            url "gcs://someGcsBucket/maven2"
        }

        ivy {
            url "gcs://someGcsBucket/ivy"
        }
    }

Downloading dependencies from Google Cloud Storage is supported for Maven and Ivy type repositories as shown above. Publishing to Google Cloud Storage is supported with both the [Ivy Publishing](userguide/publishing_ivy.html) and [Maven Publishing](userguide/publishing_maven.html) plugins, as well as when using an `IvyArtifactRepository` with an `Upload` task (see section [publishing artifacts of the user guide](userguide/artifact_management.html#sec:publishing_artifacts)).

Please see the [repositories section of the dependency management chapter](userguide/dependency_management.html#sec:repositories) in the user guide for more information on configuring Google Cloud Storage repository access.

### Features for easier plugin authoring

#### Nested DSL elements

While it is easy for a plugin author to extend the Gradle DSL to add top level blocks to the DSL using project extensions, in previous versions of Gradle it was awkward to create a deeply nested DSL inside these top level blocks, often requiring the use of internal Gradle APIs.

In this release of Gradle, API methods have been added to allow a plugin author to create nested DSL elements. See the [example in the user guide](userguide/custom_plugins.html#sec:nested_dsl_elements) section on custom plugins.

#### Declaring the output of a task as a publish artifact

In previous versions of Gradle, it was not possible to declare the output of a task as a publish artifact in a way that deals with changes to the build directory and other configuration. A publish artifact makes a file or directory available to be referenced by a project dependency or published to a binary repository.

TBD: The publish artifact DSL now accepts `Provider<File>`, `Provider<RegularFile>` and `Provider<Directory>` instances, which allows a plugin author to easily wire up a particular task output as a publish artifact in a way that respects configuration changes.

#### Groovy DSL support for properties of type `PropertyState`

The last several version of Gradle have been steadily adding new features for plugin authors that build on the `Provider<T>` and `PropertyState<T>` types. This version of Gradle adds some DSL conveniences for working with these types.

TBD: The Groovy DSL adds convenience methods to set the value of a property whose type is `PropertyState<T>` using any value of `T` or a `Provider<T>`. This makes the DSL clearer when configuring such a property, including wiring the output of one task in as the input of some other task or setting output locations relative to some configurable value, such as the build directory.  

### Safer handling of stale output files

In previous releases, tasks could produce incorrect results when output files were left behind during upgrades or when processes outside of Gradle created files in a shared output directory.
Gradle is able to detect these situations and automatically remove stale files, if it is safe to do so.
Only files within `buildDir`, paths registered as targets for the `clean` task and source set outputs are considered safe to remove.

### CLI abbreviates long test names

In Gradle 4.1, the Gradle CLI began displaying tests in-progress. We received feedback that long packages and test names caused the test name to be truncated or omitted. This version will abbreviate java packages of long test names to 60 characters to make it highly likely to fit on terminal screens.

### Better support for script plugins loaded via HTTP

Script plugins are applied to Gradle settings or projects via the `apply from: 'URL'` syntax. Support for `http://` and `https://` URLs has been improved in this release:

- HTTP script plugins are cached for [`--offline`](userguide/dependency_management.html#sub:cache_offline) use.
- Download of HTTP script plugins honours [proxy authentication settings](userguide/build_environment.html#sec:accessing_the_web_via_a_proxy).

### Naming task actions defined with doFirst {} and doLast {}

Task actions that are defined in build scripts can now be named using `doFirst("First things first") {}` or `doLast("One last thing") {}`. Gradle uses the names for logging, which allows the user, for example, to see the order in which actions are executed in the task execution views of IDEs. The action names will also be utilised in build scans in the future. This feature is supported in both Kotlin and Groovy build scripts.

### Better Play support

#### Support for Play 2.6

Gradle now supports Play applications built with [Play 2.6](https://www.playframework.com/documentation/2.6.x/LatestRelease).

By default, Gradle still uses Play 2.3.10.  Change the version of Play you're using with:

    model {
        components {
            play {
                platform play: '2.6.2', scala: '2.11'
            }
        }
    }

#### Requests to a running Play application will block until all changes are incorporated

The `PlayRun` task is used to start a Play application from within Gradle, keeping the application up-to-date with any source changes made after startup.

In earlier versions of Gradle, an HTTP request to the running Play application may have been served by an application in a 'stale' state, where file changes had not yet been fully rebuilt and reloaded.

In Gradle 4.2, this has been fixed, and an HTTP request to the running Play application will block until all pending changes have been incorporated.

#### On-demand rebuild and reload for `PlayRun`

When a Play application is started with `PlayRun`, Gradle will monitor the input files for changes. Gradle now supports 2 different rebuild behaviours for `PlayRun`: continuous or on-demand.

If Gradle is run with `--continuous` (or `-t`), the application will be rebuilt and reloaded as soon as any file changes are detected. 
Without the `--continuous` flag, Gradle will only rebuild the application on-demand, when an HTTP request is received by the application.

In summary:
- `gradle --continuous runPlay` will start the application, monitor changes, and rebuild/reload the application as soon as a change is detected.
- `gradle runPlay` will start the application, monitor changes, and rebuild/reload the application only when a request is received after a change is detected.

#### Improved Twirl template support

Gradle now supports the standard built-in Twirl templates for HTML, JavaScript, TXT and XML. These will work out of the box.

Custom Twirl templates are supported: a [`TwirlSourceSet`](dsl/org.gradle.language.twirl.TwirlSourceSet.html) can now be configured to use these user-defined template formats.

Arbitrary additional imports for packages and classes can also be specified on a a [`TwirlSourceSet`](dsl/org.gradle.language.twirl.TwirlSourceSet.html). These will be added to the Scala/Java code generated by the Play Twirl template compiler.

### Faster zipTree and tarTree

The `zipTree` and `tarTree` implementations had a major performance issue, unpacking files every time the tree was traversed. This has now been fixed and should speed up builds using these trees a lot.

### Connect to untrusted HTTPS build cache

The HTTP build cache connector can now be configured to allow HTTPS connections to servers with untrusted SSL certificates. 
The SSL certificate for the HTTP build cache server may be untrusted since it is internally provisioned or a self-signed certificate.
For more details see the [`HttpBuildCache.allowUntrustedServer`](dsl/org.gradle.caching.http.HttpBuildCache.html#org.gradle.caching.http.HttpBuildCache:allowUntrustedServer).

### Timeouts for HTTP requests

Previous versions of Gradle did not define a timeout for any HTTP requests. Under certain conditions e.g. network problems, unresponsive or overloaded servers this behavior could lead to hanging connections. Gradle now defines connection and socket timeouts for all HTTP requests. In the event of a timeout, Gradle will skip subsequent connections to the same repository for the duration of the build. The output of a build clearly indicates which request was skipped.

    * What went wrong:
    Could not resolve all files for configuration ':deps'.
    > Could not resolve group:a:1.0.
      Required by:
          project :
       > Could not resolve group:a:1.0.
          > Could not get resource 'http://localhost:54347/repo/group/a/1.0/a-1.0.pom'.
             > Could not GET 'http://localhost:54347/repo/group/a/1.0/a-1.0.pom'.
                > Read timed out
    > Could not resolve group:b:1.0.
      Required by:
          project :
       > Skipped due to earlier error
       
The timeouts are also effective for connections to an [HTTP build cache](dsl/org.gradle.caching.http.HttpBuildCache.html#org.gradle.caching.http.HttpBuildCache).
If connections to the build cache time out then it will be disabled for the rest of the build.

    :compileJava
    Could not load entry 2b308a0ad9cbd0ad048d4ea84c186f71 for task ':compileJava' from remote build cache: Unable to load entry from 'https://example.com/cache/2b308a0ad9cbd0ad048d4ea84c186f71': Read timed out
    
    BUILD SUCCESSFUL in 4s
    1 actionable task: 1 executed
    The remote build cache was disabled during the build due to errors.

### Faster Native Builds
Native compile and link tasks now execute in parallel by default, making native builds faster than ever.  This means that when two (or more) compile or link tasks have no dependencies on each other, they can execute simultaneously (up to the `max-workers` limit for each Gradle invocation).  The resulting performance improvement is highly dependent on project and component structure (for instance, the more inter-dependencies there are between components, the less opportunity there is to execute tasks in parallel) but in our testing, native build times have improved by as much as 50% or more. 

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

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
