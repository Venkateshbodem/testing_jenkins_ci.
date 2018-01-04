## New and noteworthy

Here are the new features introduced in this Gradle release.

<!--
IMPORTANT: if this is a patch release, ensure that a prominent link is included in the foreword to all releases of the same minor stream.
Add-->

<!--
### Example new and noteworthy

Think of every feature section as a mini blog post.

1) Make sure the release notes render properly: `./gradlew :docs:releaseNotes && open subprojects/docs/build/docs/release-notes.html`
  TIP: Continuous build is useful when working on release notes.
  NOTE: The markdown processor does not know GitHub flavored markdown syntax.

2) Explain why users should care.
  TIP: Avoid technical details with no indications how this impacts users.

3) Link to documentation or a blog post for more detailed information.

4) Show, don't just tell, if possible.
  NOTE: Totally fine to just link to an example that show the feature.
-->

### Deprecation warning improvements

In this release, deprecation warnings are no longer displayed in the console output by default. Instead, all deprecation warnings in the build will be collected and a single "summary" will be rendered at the end of the build:

    There're <number> deprecation warnings, which may break the build in Gradle 5.0. Please run with --warning-mode=all to see them.

You can run the build with the command line option `--warning-mode=all` or the property `org.gradle.warning.mode=all` to have all warnings displayed as earlier version of Gradle.
Of course, you can use the command line option `--warning-mode=none` or the property `org.gradle.warning.mode=none` to suppress all warnings, including the one displayed at the end of the build.

### Provider API documentation

In this release, the Gradle team added a new chapter in the user guide documenting the [Provider API](userguide/lazy_configuration.html).

### Provider API improvements

A convenience for dealing with sets has been added. TBD - link to API

### Faster C/C++ compilation and builds

### Faster up-to-date builds

Configuration time, input snapshotting and dependency resolution have all been improved in this release. [Large Android projects](https://github.com/gradle/perf-android-large) will benefit the most from these enhancements, with up to 30% faster up-to-date builds. Java and native projects benefit as well, with an improvement of up to 20%.

#### Build Cache Support

We introduced [experimental C/C++ caching support](https://docs.gradle.org/4.3/release-notes.html#experimental-task-output-caching-for-c/c++-compilation) in Gradle 4.3, but this feature was hidden behind an additional flag until we had fixed some underlying correctness issues.

In this release, we have fixed some underlying issues with calculating the correct build cache key for C++ compilation and have removed the special flag.  If you [enable the build cache](userguide/build_cache.html#sec:build_cache_enable), Gradle will try to reuse task outputs from C/C++ compile tasks when all inputs (compiler flags, source, dependencies) are identical, which can [greatly reduce](https://blog.gradle.org/introducing-gradle-build-cache) build times.

Please note that there are [some caveats](userguide/build_cache.html#sec:task_output_caching_known_issues_caveats) when using the build cache.  In particular for C++, object files that contain absolute paths (e.g., object files with debug information) are reusable and cacheable, but may cause problems when debugging.

#### Incremental Compilation

Gradle's incremental C/C++ compilation works by analysing and understanding the dependencies between source files and the header files that they include. Gradle can use this information to compile only those source files that are affected by a change in a header file. In some cases, Gradle could not analyze all of these dependencies and would assume all source files depend on all header files. Changes to any header file would require recompiling all source files, regardless of whether the compiler output would change or not. This also affected how well the Gradle build cache could be used to skip compilation.

In this release, Gradle's incremental C/C++ compilation is now able to understand most dependencies between source files and header files. This means incremental compilation will occur more often and builds are more likely to see cache hits.

### Support for GnuPG's gpg-agent

Previously, Signing plugin can't sign generated artifacts via GnuPG's agent, which is very inconvenient. Now you can leverage your gpg agent easily with:

    signing {
        useGpgCmd()
        sign configurations.archives
    }

Please see [`signing` plugin](userguide/signing_plugin.html#sec:using_gpg_agent) for more details.    

### Arbitrary task property names

When registering task properties via the runtime API, property names are not required to be Java identifiers anymore, and can be any non-empty string.

### ANTLR task is now cacheable by default

When generating grammar sources with ANTLR, now the task's outputs are stored and retrieved from the build cache.

### The `init` task can now generate Kotlin DSL build scripts

It is now possible to generate new Gradle builds using the Kotlin DSL with the help of the `init` task and its new `--dsl` option:

    gradle init --dsl kotlin 

The new option defaults to `groovy` and is supported by all build setup types except migration from Maven builds.

See the user guide section on the [`init` plugin](userguide/build_init_plugin.html) for more information.

### Included builds of composite build now share build cache configuration

Previously, each build within a composite build used its own build cache configuration.
Now, included builds, and their `buildSrc` builds, automatically inherit the build cache configuration of the root build.
This makes managing build cache configuration for composite builds simpler and effectively allows better build cache utilization.

Included builds may still define build cache configuration in their `settings.gradle` file, it is just no longer respected.

The `buildSrc` build of the root project continues to use its own build cache configuration, due to technical constraints.
However, the `buildSrc` build of any included build will inherit the build cache configuration from the root build.
For more on configuring the build cache for the root `buildSrc` build, please see [the Userguide section on this topic](userguide/build_cache.html#buildCacheBuildSrc).

### Configure executable directory in distributions

Previously, executables in distributions would be placed in `bin` directory and couldn't be configured. Now you can configure this directory with `executableDir` property. 
See [`application` plugin](userguide/application_plugin.html) for more details.

### Default CodeNarc has been upgraded to 1.0

Now [CodeNarc](http://codenarc.sourceforge.net/)'s default version has been upgraded to 1.0, enjoy!

### Use of runtime types when declaring `@Nested` task inputs

When analyzing `@Nested` task properties for declared input and output sub-properties, Gradle used to only observe the declared type of the property.
This meant ignoring any sub-properties declared by a runtime sub-type.
Since Gradle 4.5, Gradle uses the [type of the actual value instead](userguide/more_about_tasks.html#sec:task_input_nested_inputs), and hence can discover all sub-properties declared this way.
This allows for a few new tricks.

#### Rich Java compiler arguments

When you have to expose a file location to your annotation processor, it is essential for Gradle to learn about this additional input (or output).
Without tracking the location and contents of the given file (or directory), features like incremental build and task output caching cannot function correctly.
Before Gradle 4.5 you had to let Gradle know about such inputs or outputs manually by calling `compileJava.inputs.file(...)` or similar.

Gradle 4.5 introduces a better way to handle this situation by modeling the annotation processor as a [`CompilerArgumentProvider`](javadoc/org/gradle/api/tasks/compile/CompilerArgumentProvider.html).
This approach allows the declaration of complex inputs and outputs, just like how you would declare `@InputFile` and `@OutputDirectory` properties on the task type.

For example, to declare annotation processor arguments, it is now be possible to do the following:

        class MyAnnotationProcessor implements CompilerArgumentProvider {
            @InputFile
            @PathSensitivite(NONE)
            File inputFile
            
            @OutputFile
            File outputFile
            
            MyAnnotationProcessor(File inputFile, File outputFile) {
                this.inputFile = inputFile
                this.outputFile = outputFile
            }
            
            @Override
            List<String> asArguments() {
                [
                    "-AinputFile=${inputFile.absolutePath}",
                    "-AoutputFile=${outputFile.absolutePath}"
                ]
            }
        }
        
        compileJava.options.compilerArgumentProviders << new MyAnnotationProcessor(inputFile, outputFile)

This models an annotation processor which requires an input file and generates an output file.

The approach is not limited to annotation processors, but can be used to declare any kind of command-line argument to the compiler.
The only thing you need to do is to add your custom `CompilerArgumentsProvider` to [`CompileJava.options.compilerArgumentProviders`](dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:compilerArgumentProviders).

#### `@Nested` on iterables

When applying the [`@Nested`](javadoc/org/gradle/api/tasks/Nested.html) to an iterable property, each element is now treated as a separate nested input.
[`CompileJava.options.compilerArgumentProviders`](dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:compilerArgumentProviders) shows this new behavior:

    @Nested
    @Incubating
    public List<CompilerArgumentProvider> getCompilerArgumentProviders() {
        return compilerArgumentProviders;
    }

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

### Deprecation of no-search-upward command line options 

The command line options for searching in parent directories for a `settings.gradle` file (`-u`/`--no-search-upward`) has been deprecated and will be removed in Gradle 5.0. A Gradle project should always define a `settings.gradle` file to avoid the performance overhead of walking parent directories.

## Potential breaking changes

* Two overloaded `ValidateTaskProperties.setOutputFile()` methods were removed. They are replaced with auto-generated setters when the task is accessed from a build script.

* The `maven-publish` plugin now produces more complete `maven-metadata.xml` files, including maintaining a list of `<snapshotVersion>` elements. Some older versions of Maven may not be able to consume this metadata.

<!--
### Example breaking change
-->

### HTTP build cache does not follow redirects

When connecting to an HTTP build cache backend via [HttpBuildCache](dsl/org.gradle.caching.http.HttpBuildCache.html), Gradle does not follow redirects any more, and treats them as errors instead.
Getting a redirect from the build cache backend is mostly a configuration error (e.g. using an http url instead of https), and has negative effects on performance.

### Build cache configuration of included builds no longer respected

In earlier versions of Gradle, each included build within a composite used its own build cache configuration.
Now, included builds inherit the configuration from the root build. 
Included builds may still define build cache configuration in their `settings.gradle` file, it is just no longer used.

This change will not cause build breakage and does not require any change in build logic to adapt to.

### Incubating `Depend` task removed

TBD - removed `Depend` task, this capability has been merged into the compile tasks.

### Gradle no longer tracks the canonical path of input file tree roots

Gradle was inconsistently handling symlinks when snapshotting inputs. For the root of a file tree it would take the canonical path into account. For individual files and contents of trees,
it would only consider the normalized path instead. Gradle will now always use the normalized path. This means that a task will not rerun if a directory is replaced with a symlink to the exact same contents.
If you have a use case that requires reacting to the canonical path of inputs, please open an issue and we'll consider an opt-in API that will canonicalize all inputs, not just tree roots.

### Project.file() no longer normalizes case

The `Project.file()` and related methods used to normalize the case on case-insensitive file systems. This means that the method would check whether any parts of the hierarchy of a given file already
existed in a different case and would adjust the given file accordingly. This lead to lots of IO during configuration time without a strong benefit. 

The `Project.file()` method will now ignore case and only normalize redundant segments like `/../`. It will not touch the file system.

### ListProperty no longer extends Property

TBD - `ListProperty` now extends `HasMultipleValues` and `Provider` instead of `Property`. The `Property` interface represents a property whose incoming and outgoing types are the same. However, a `List` can be assembled from any `Iterable` rather than just any `List` and this new arrangement reflects this, allowing a `ListProperty<T>` to be set using any `Iterable<T>`. This also applies to the DSL, where the Groovy DSL will allow any `Iterable` to be used to set the value.

The new `SetProperty` type also follows this pattern.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

<!--
 - [Some person](https://github.com/some-person) - fixed some issue (gradle/gradle#1234)
-->

- [Nikita Skvortsov](https://github.com/nskvortsov) — Optimize generated IDEA dependencies (gradle/gradle#3460)
- [Theodore Ni](https://github.com/tjni) — Ignored TestNG tests should not throw an exception (gradle/gradle#3570)
- [James Wald](https://github.com/jameswald) — Introduce command line option for Wrapper task to set distribution SHA256 sum (gradle/gradle#1777)
- [zosrothko](https://github.com/zosrothko) — Restore Eclipse contribution instructions (gradle/gradle#3715)
- [Kevin Macksamie](https://github.com/k-mack) — Fix link to Lazy Configuration in docs (gradle/gradle#3848)
- [Jason Tedor](https://github.com/jasontedor) - Adapt Java version detection to support JEP-322 (gradle/gradle#3892)
- [S K](https://github.com/xz64) - Add support for configurable start script directory (gradle/gradle#2977)
- [Jokubas Dargis](https://github.com/eleventigerssc) - Improve performance of resource list operation in GCS repositories (gradle/gradle#3023)
- [Christoph Böhme](https://github.com/cboehme) - Support for GnuPG's gpg-agent (gradle/gradle#1703)
- [George Thomas](https://github.com/smoothreggae) - Fix apostrophe abuse in dependency management section of user guide (gradle/gradle#3895)

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
