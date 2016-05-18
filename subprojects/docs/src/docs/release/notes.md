## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy
-->

### Improvements since Gradle 2.0

- Performance improvements, faster builds and reduced memory usage
    - Configuration time, incremental build, incremental native compilation, build script compilation, test execution
- Gradle plugin portal
    - Publishing plugin
    - Maven and Ivy plugin repositories
- Dependency management
    - Compile-only dependencies for Java projects
    - Improved component meta-data rules
    - Component selection rules
    - Component replacement rules
    - Dependency substitution rules
    - Support for S3 repositories
    - Configurable HTTP authentication, including preemptive HTTP authentication
    - Artifact query API access to ivy.xml and pom.xml
    - Depend on a particular Maven snapshot
- Daemon
    - Health monitoring
    - System memory pressure aware expiration
- Continuous build
- Incremental Java compile
- Tooling API
    - Composite builds
    - Rich test, task and build progress events
    - Run test classes or methods
    - Cancellation
    - Color output
    - Eclipse builders and natures, Java source and runtime version, build JDK
- IDE
    - Improved Eclipse WTP integration, Scala integration
    - Java source and runtime version
- TestKit
- publish plugins
    - Publish to SFTP and S3 repositories
    - Maven dependency exclusions, dependency classifiers
    - Ivy extra attributes, dependency exclusions
- Groovy annotation processing
- Build environment report
- Code quality and application plugins
    - Various improvements
- Native
    - Parallel compilation
    - Cross compilation
    - Precompiled headers
    - Google test support
- Community
    - More frequent releases
    - More pull requests
- Play support
- Text resources
- Software model    
    - Dependency management for JVM libraries, target platform aware
        - inter-project, intra-project and external libraries 
    - JVM library API definition, compile avoidance
    - JUnit support
    - Components report, model report
    - Validation and defaults rules, apply rules to all subjects with type
    - More managed model features
    - Better extension by plugins
    - Model DSL
    

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
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

<!--
### Example deprecation
-->

## Potential breaking changes

### Sonar plugin has been removed

The legacy Sonar plugin has been removed from the distribution. It is superceded by the official plugin from SonarQube (http://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner+for+Gradle).

### Ant-Based Scala Compiler has been removed

The deprecated Ant-Based Scala Compiler has been removed from Gradle
3.0. The Zinc Scala Compiler is now used exclusively. The following
properties have been removed from the ScalaCompile task:

1. `daemonServer`
1. `fork`
1. `useAnt`
1. `useCompileDaemon`

### Changes to previously deprecated APIs

* The `AbstractTask` methods `setName()` and `setProject()` are removed.
* The `plus(Iterable<FileCollection>)` and `#minus(Iterable<FileCollection>)` methods have been removed from `FileCollection`.
* Changing configurations after they have been resolved now throws an error.
* Changing configurations after task dependencies have been resolved now throws an error.
* Declaring custom `check`, `clean`, `build` or `assemble` tasks is not allowed anymore when using the lifecycle plugin.
* Configuring the Eclipse project name during `beforeMerged` or `whenMerged` is not allowed anymore.
* Removed `--no-color` command-line option (use `--console=plain` instead).
* Removed `Zip.encoding` (use `Zip.metadataCharset` instead).
* Removed `DistributionPlugin.addZipTask()` and `addTarTask()`.
* The `installApp` task is no longer created by the `application` plugin (use `installDist` instead).
* Removed `Groovydoc.overview` (use `overviewText` instead).
* Removed `LoggingManager.setLevel()`. It is now not possible to change the log level during the execution of a task.
  If you were using this method to expose Ant logging messages, please use `AntBuilder.setLifecycleLogLevel()` instead.
* Removed `AntScalaCompiler` in favor of `ZincScalaCompiler`.
* Removed `EclipseClasspath.noExportConfigurations` property.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

 - [Tatsiana Drabovich](https://github.com/blestka) - Fixed TestNG Listener adapters equality (GRADLE-3189)

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (GRADLE-1234)
-->

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
