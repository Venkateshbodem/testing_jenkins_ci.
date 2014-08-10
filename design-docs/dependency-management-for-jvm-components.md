
Currently, the JVM language plugins assume that a given set of source files is assembled into a single
output. For example, the `main` Java source is compiled and assembled into a JAR file. However, this is not
always a reasonable assumption. Here are some examples:

* When building for multiple runtimes, such as Scala 2.10 and Scala 2.11.
* When building multiple variants composed from various source sets, such as an Android application.
* When packaging the output in various different ways, such as in a JAR and a fat JAR.

By making this assumption, the language plugins force the build author to implement these cases in ways that
are not understood by other plugins that extend the JVM language plugins, such as the code quality and IDE
plugins.

This problem is also evident in non-JVM languages such as C++, where a given source file may be compiled and
linked into more than one binaries.

This spec describes some work to allow plugins to define the kinds of JVM components that they produce and consume,
and to allow plugins to define their own custom JVM based components.

# Use cases

## Multiple build types for Android applications

An Android application is assembled in to multiple _build types_, such as 'debug' or 'release'.

## Build a library for multiple Scala or Groovy runtimes

A library is compiled and published for multiple Scala or Groovy runtimes, or for multiple JVM runtimes.

# Build different variants of an application

An application is tailored for various purposes, with each purpose represented as a separate variant. For
each variant, some common source files and some variant specific source files are jointly compiled to
produce the application.

For example, when building against the Java 5 APIs do not include the Java 6 or Java 7 specific source files.

# Compose a library from source files compiled in different ways

For example, some source files are compiled using the aspectj compiler and some source files are
compiled using the javac compiler. The resulting class files are assembled into the library.

# Implement a library using multiple languages

A library is implemented using a mix of Java, Scala and Groovy and these source files are jointly compiled
to produce the library.

## Package a library in multiple ways

A library may be packaged as a classes directory, or a set of directories, or a single jar file, or a
far jar, or an API jar and an implementation jar.

## A note on terminology

There is currently a disconnect in the terminology used for the dependency management component model, and that used
for the component model provided by the native plugins.

The dependency management model uses the term `component instance` or `component` to refer to what is known as a `binary`
in the native model. A `component` in the native model doesn't really have a corresponding concept in the dependency
management model (a `module` is the closest we have, and this is not the same thing).

Part of the work for this spec is to unify the terminology. This is yet to be defined.

For now, this spec uses the terminology from the native component model, using `binary` to refer to what is also
known as a `component instance` or `variant`.

# Features

## Feature: Build author creates a JVM library with Java sources

### Story: Build author defines JVM library

#### DSL

Project defining single jvm libraries

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            main
        }
    }

Project defining multiple jvm libraries

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            main
            extra
        }
    }

Combining native and jvm libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'native-component'

    jvm {
        libraries {
            myJvmLib
        }
    }
    nativeRuntime {
        libraries {
            myNativeLib
        }
    }

#### Implementation plan

- Introduce `org.gradle.jvm.JvmLibrary`
- Rename `org.gradle.nativebinaries.Library` to `org.gradle.nativebinaries.NativeLibrary`
    - Similar renames for `Executable`, `TestSuite` and all related classes
- Introduce a common superclass for `Library`.
- Extract `org.gradle.nativebinaries.LibraryContainer` out of `nativebinaries` project into `language-base` project,
  and make it an extensible polymorphic container.
    - Different 'library' plugins will register a factory for library types.
- Add a `jvm-component` plugin, that:
    - Registers support for `JvmLibrary`.
    - Adds a single `JvmLibraryBinary` instance to the `binaries` container for every `JvmLibrary`
    - Creates a binary lifecycle task for generating this `JvmLibraryBinary`
    - Wires the binary lifecycle task into the main `assemble` task.
- Rename `NativeBinariesPlugin` to `NativeComponentPlugin` with id `native-component`.
- Move `Binary` and `ClassDirectoryBinary` to live with the runtime support classes (and not the language support classes)
- Extract a common supertype `Application` for `NativeExecutable`, and a common supertype `Component` for `Library` and `Application`
- Introduce a 'filtered' view of the ExtensiblePolymorphicDomainObjectContainer, such that only elements of a particular type are returned
  and any element created is given that type.
    - Add a backing `projectComponents` container extension that contains all Library and Application elements
        - Will later be merged with `project.components`.
    - Add 'jvm' and 'nativeRuntime' extensions for namespacing different library containers
    - Add 'nativeRuntime.libraries' and 'jvm.libraries' as filtered containers on 'components', with appropriate library type
    - Add 'nativeRuntime.executables' as filtered view on 'components
    - Use the 'projectComponents' container in native code where currently must iterate separately over 'libraries' and 'executables'

#### Test cases

- Can apply `jvm-component` plugin without defining library
    - No binaries defined
    - No lifecycle task added
- Define a jvm library component
    - `JvmLibraryBinary` added to binaries container
    - Lifecycle task available to build binary: skipped when no sources for binary
    - Binary is buildable: can add dependent tasks which are executed when building binary
- Define and build multiple java libraries in the same project
  - Build library jars individually using binary lifecycle task
  - `gradle assemble` builds single jar for each library
- Can combine native and JVM libraries in the same project
  - `gradle assemble` executes lifecycle tasks for each native library and each jvm library

### Story: Build author creates JVM library jar from Java sources

When a JVM library is defined with Java language support, then binary is built from conventional source set locations:

- Has a single Java source set hardcoded to `src/myLib/java`
- Has a single resources source set hardcoded to `src/myLib/resources`

#### DSL

Java library using conventional source locations

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib
        }
    }


Combining jvm-java and native (multi-lang) libraries in single project

    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    apply plugin: 'native-component'
    apply plugin: 'cpp-lang'
    apply plugin: 'c-lang'

    jvm {
        libraries {
            myJvmLib
        }
    }
    nativeRuntime {
        libraries {
            myNativeLib
        }
    }

#### Implementation plan

- Replace the current `java-lang` plugin with a simpler one that does not know about legacy conventions
- For each `JvmLibrary`:
    - Adds a single `ResourceSet` for `src/${component}/resources`
    - Adds a single `JavaSourceSet` for `src/${component}/java`
- Each created `JvmLibraryBinary` has the source sets of its `JvmLibrary`
- Create a `ProcessResources` task for each `ResourceSet` for a `JvmLibraryBinary`
    - copy resources to `build/classes/${binaryName}`
- Create a `CompileJava` task for each `JavaSourceSet` for a `JvmLibraryBinary`
    - compile classes to `build/classes/${binaryName}`
- Create a `Jar` task for each `JvmLibraryBinary`
    - produce jar file at `build/${binaryType}/${binaryName}/${componentName}.jar`
- Rejig the native language plugins so that '*-lang' + 'native-components' is sufficient to apply language support
    - Existing 'cpp', 'c', etc plugins will simply apply '*-lang' and 'native-components'

#### Test cases

- Define and build the jar for a java library (assert jar contents for each case)
    - With sources but no resources
    - With resources but no sources
    - With both sources and resources
- Creates empty jar with no sources or resources (a later story will make this a failure)
- Compiled sources and resources are available in a common directory
- Reports failure to compile source
- Incremental build for java library
    - Tasks skipped when all sources up-to-date
    - Class file is removed when source is removed
    - Copied resource is removed when resource is removed
- Can build native and JVM libraries in the same project
    - `gradle assemble` builds each native library and each jvm library
- Can combine old and new JVM plugins in the same project
    - `gradle assemble` builds both jars

## Feature: Custom plugin defines a custom library type

This features allows the development of a custom plugin that can contribute Library, Binary and Task instances to the language domain.

Development of this feature depends on the first 2 stories from the `unified-configuration-and-task-model` spec, namely:

- Story: Plugin declares a top level model to make available
- Story: Plugin configures tasks using model as input

### Story: plugin declares its own library type

Define a sample plugin that declares a custom library type:
    
    interface SampleLibrary extends LibrarySpec { ... }
    class DefaultSampleLibrary extends DefaultLibrarySpec implements SampleLibrary { ... }

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        @ComponentModel(SampleLibrary.class, DefaultSampleLibrary.class)
        static class ComponentModel {
            @Model("mySample")
            SampleExtension createSampleExtension() {
                ...
            }

            @Mutate
            void createSampleLibraryComponents(NamedItemCollectionBuilder<SampleLibrary> sampleLibraries, SampleExtension sampleExtension) {
                for (String libraryName : sampleExtension.getLibraryNames()) {
                    sampleLibraries.create(libraryName)
                }
            }
        }
    }

Libraries are then visible in libraries and components containers:

    // Library is visible in component container
    assert projectComponents.withType(SampleLibrary).size() == 2

A custom library type:
- Extends or implements the public base `LibrarySpec` type.
- Has no dependencies.
- Has no sources.
- Produces no artifacts.

A custom library implementation:
- Implements the custom library type
- Extends `DefaultLibrarySpec`
- Has a no-arg constructor

#### Implementation Plan

- ~~Rename the existing JVM and C++ model classes from `Project*` to `*Spec`.~~
- ~~Introduce a `LibrarySpec` interface that both `NativeLibrarySpec` and `JvmLibrarySpec` extend.~~
- ~~Add a default implementation of `LibrarySpec` named `DefaultLibrarySpec`. All custom library implementations extend this.~~
- ~~Replace `NamedProjectComponentIdentifier` with `ComponentSpecIdentifier` everywhere.~~
- ~~Add a new Sample for a custom plugin that uses model rules to add `SampleLibrary` instances to the `ComponentSpecContainer`~~
    - Should apply the `ComponentModelBasePlugin`
    - At the end of the story the sample will be adapted to use the new mechanism introduced
    - Add an integration test for the sample
- ~~Add a new incubating annotation to the 'language-base' project: `ComponentModel` with parameters defining the Library type and implementation classes~~
- ~~Add functionality to the 'language-base' plugin that registers a hook that inspects every applied plugin for a nested (static) class with the @ComponentModel annotation~~
    - Implement by making an `Action<? super PluginApplication>` available to the construction of `DefaultPluginContainer`, via `PluginServiceRegistry`.
- ~~When a plugin is applied that has a nested class with the `@ComponentModel(SampleLibrary)` annotation:~~
    - Automatically apply the `ComponentModelBasePlugin` before the plugin is applied
    - Register a factory with the `ComponentSpecContainer` for creating `SampleLibrary` instances with the supplied implementation
        - The factory implementation should generate a `ComponentSpecIdentifier` with the supplied name to instantiate the component
    - Add a `ModelCreator` to the `ModelRegistry` that can present a `NamedItemCollectionBuilder<SampleLibrary>` view of the `ComponentSpecContainer`.
- ~~Update `DefaultLibrarySpec` so that it has a public no-arg constructor~~
    - ~~Inject the ComponentSpecIdentifier into the constructed library using a ThreadLocal and static setter method (see AbstractTask).~~

#### Test cases

- ~~Can register a component model with @Library without any rules for creating components (does not create components)~~
- ~~Can create library instances via `NamedItemCollectionBuilder<LibrarySpec>` with a plugin that:~~
    - ~~Already has the `ComponentModelBasePlugin` applied~~
    - ~~Has a single nested class with both `@ComponentModel` and `@RuleSource` annotations~~
    - ~~Has separate nested classes with `@ComponentModel` and `@RuleSource` annotations~~
- ~~Rule for adding library instances can be in a separate plugin to the plugin declaring the component model~~
- ~~Can define and create multiple component types in the same plugin with multiple `@ComponentModel` annotations~~
- ~~Friendly error message when supplied library implementation:~~
    - ~~Does not have a public no-arg constructor~~
    - ~~Does not implement library type~~
    - ~~Does not extend `DefaultLibrarySpec`~~
- ~~Friendly error message when attempting to register the same library type with different implementations~~
- ~~Custom libraries show up in components report~~

### Story: Custom plugin uses rule to declare custom component type

To avoid a future explosion of nested annotations, this story switches the mechanism for declaring a custom library type to use an
annotated method, rather than a type annotation.

This story also expands on the previous one by expanding the functionality to include any component type, not just `LibrarySpec` subtypes.

When a rule method with the `@ComponentType` annotation is found, the method is inspected to determine the type based on the generic
type of the `ComponentTypeBuilder` input. The ComponentTypeBuilder implementation will then register a rule that will
register a factory with the `ComponentSpecContainer` when the default implementation is set.

#### User visible changes

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class Rules {
            @ComponentType
            void defineType(ComponentTypeBuilder<SampleLibrary> builder) {
                builder.setDefaultImplementation(DefaultSampleLibrary)
            }
        }
    }

#### Test cases

- ~~Can register type that is not a subtype of `LibrarySpec`~~
- ~~Fails if a method with @ComponentType does not have a single parameter of type `ComponentTypeBuilder`~~
- ~~Fails if a method with @ComponentType has a return type~~
- ~~Fails if `setDefaultImplementation` is called multiple times~~
- ~~Empty @ComponentType method implementation is ok: no factory registered~~

### Story: Plugin declares the binary types and default implementations for a custom component

This story provides a simple way for developers to specify the binary types that are relevant for a particular custom component.

#### User visible changes

Add a binary type to the sample plugin:

    // Define some binary types and reference them from the component
    interface SampleBinary extends BinarySpec {}
    interface OtherSampleBinary extends SampleBinary {}

    interface SampleComponent extends ComponentSpec {
        @Binaries
        Collection<SampleBinary> getSampleBinaries()

        @Binaries
        Collection<OtherSampleBinary> getOtherBinaries()
    }

    // Define implementations for the binary types - these will go away at some point
    class DefaultSampleBinary extends DefaultBinarySpec implements SampleBinary {}
    class DefaultOtherSampleBinary extends DefaultBinarySpec implements OtherSampleBinary {}

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @BinaryType
            void defineBinaryType(BinaryTypeBuilder<SampleBinary> builder) {
                builder.setDefaultImplementation(DefaultSampleBinary)
            }

            @BinaryType
            void defineBinarySubType(BinaryTypeBuilder<OtherSampleBinary> builder) {
                builder.setDefaultImplementation(DefaultOtherSampleBinary)
            }
        }
    }

A custom binary type:
- Extends public `BinarySpec` type.
- Has no sources.
- Is buildable.
- Has some lifecycle task to build its outputs.

A custom binary implementation:
- Implements the custom binary type.
- Extends `DefaultBinarySpec`.
- Has a public no-arg constructor.

#### Implementation Plan

- Add a `DefaultBinarySpec` implementation or `BinarySpec` that has a no-arg constructor.
- Introduce a `@BinaryType` rule type for registering a binary type and implementation
    - Assert that the implementation class extends `DefaultBinarySpec`, has a no-arg constructor and implements the type.
    - Register a factory for the type with the `BinaryContainer`.
- Introduce a `@Binaries` annotation that can be used to determine the allowable binary types for a component
    - This mechanism will be used to verify the binary definitions in the next story.
- Generify DefaultSampleLibrary so that the `getBinaries()` method can return a set of binary subtypes.
- Introduce `LibraryBinarySpec` to represent binaries for produced from a `LibrarySpec`.
    - Similarly, add `ApplicationBinarySpec`.

#### Test cases

- A rule that mutates `BinaryContainer` can create instances of registered type
- Friendly error message when annotated `@BinaryType` rule method:
    - Does not have a single parameter of type BinaryTypeBuilder
    - Parameter does not have a generic type
    - Has a non-void return value
- Friendly error message when supplied binary type:
    - Does not extend `BinarySpec`
    - Equals `BinarySpec` (must be a subtype)
- Friendly error message when supplied binary implementation:
    - Does not have a public no-arg constructor
    - Does not implement binary type
    - Does not extend `DefaultBinarySpec`
- Friendly error message when attempting to register the same binary type with different implementations

### Story: Custom plugin defines binaries for each custom component

This story introduces a mechanism by this a developer can define the binaries that should be built for a custom library.

These binaries are not visible to the build script author for configuration.

#### User visible changes

    class MySamplePlugin implements Plugin<Project> {
        @RuleSource
        static class ComponentModel {
            @ComponentBinaries
            void createBinariesForSampleLibrary(NamedItemCollectionBuilder<SampleBinary> binaries, SampleLibrary library) {
                binaries.create("${library.name}Binary")
                binaries.create("${library.name}OtherBinary", OtherSampleBinary)
            }

            @ComponentBinaries
            void createBinariesForSampleLibrary(NamedItemCollectionBuilder<OtherSampleBinary> binaries, SampleLibrary library) {
                binaries.create("${library.name}OtherBinary2")
            }
        }
    }

Binaries are now visible in the appropriate containers:

    // Binaries are visible in the appropriate containers
    // (assume 2 libraries & 2 binaries per library)
    assert binaries.withType(SampleBinary).size() == 4
    assert binaries.withType(OtherSampleBinary).size() == 2
    projectComponents.withType(SampleLibrary).each { assert binaries.size() == 2 }

Running `gradle assemble` will execute lifecycle task for each binary.

#### Implementation Plan

- Introduce a `@ComponentBinaries` rule type
    - Subject must be of type `NamedItemCollectionBuilder` with a generic type parameter extending `BinarySpec`
    - Exactly one input must be a type extending `ComponentSpec`
    - The binary type declared in the subject must be assignable to one of the binary types declared on the component input type
    - Other inputs are permitted
- For each `@ComponentBinaries` rule, register a mutate rule that iterates over all components conforming to the requested component type
    - Any created binaries should be added to the set of binaries for the component, as well as being added to the `BinaryContainer`
- For each created binary, create and register a lifecycle task with the same name as the binary
- Update the 'custom components' sample to demonstrate the new mechanism.

#### Test cases

- Can create binaries via rules that declare these as input:
    - `NamedItemCollectionBuilder<BinarySpec>`
    - `NamedItemCollectionBuilder<SampleBinary>`
    - `NamedItemCollectionBuilder<SampleBinarySubType>`
- Can execute lifecycle task of each created binary, individually and via 'assemble' task
- Can access lifecycle task of binary via BinarySpec.buildTask
- Friendly error message when annotated binary rule method:
    - Does not have a single parameter of type BinaryTypeBuilder
    - Parameter does not have a generic type
    - Has a non-void return value
- Friendly error message when supplied binary type:
    - Does not extend `LibraryBinarySpec`
    - Equals `LibraryBinarySpec`
- Friendly error message when supplied binary implementation:
    - Does not have a public no-arg constructor
    - Does not implement binary type
    - Does not extend `DefaultLibraryBinarySpec`
- Friendly error message when attempting to register the same binary type with different implementations

#### Open issues

- Could use a single `@ComponentModel` annotation for all methods, and inspect signature to determine type
- DefaultLibraryBinarySpec will expose internal api. Need a mechanism to provide binary implementation without subclassing.
- Add 'plugin declares custom platform' story.
- General mechanism to register a model collection and have rules that apply to each element of that collection.
- Migrate the JVM and natives plugins to use this.
    - Need to be able to declare the target platform for the component type.
    - Need to expose general DSL for defining components of a given type.
    - Need to attach source sets to components.
- Need to be able to specialise the `languages` and `binaries` collections in a subtype of `ComponentSpec`.

### Story: Build author uses `libraries { }` DSL to configure binaries for a custom component

Adds capability to configure a child object after the parent object has been configured.

### Story: Build author uses `binaries { }` DSL to configure binaries for multiple components

Adds capability for an object to appear in multiple locations in the model.

### Story: Custom plugin defines tasks from binaries

Add a rule to the sample plugin:

    class MySamplePlugin {
        ...

        @BinaryTasks
        void createTasksForSampleBinary(NamedItemCollectionBuilder<Task> tasks, SampleBinary binary) {
            ... Add tasks that create this binary. Create additional tasks where signing is required.
        }
    }

Running `gradle assemble` will execute tasks for each library binary.

#### Implementation Plan

- Introduce a `@BinaryTasks` rule type:
    - Subject must be of type `NamedItemCollectionBuilder<Tasks>`
    - Exactly one input must be a type extending `LibraryBinarySpec`
    - Other inputs are permitted
- For each `@BinaryTasks` rule, register a mutate rule that iterates over all binaries conforming to the requested binary type
    - Any created tasks should be added to the binary.tasks for this binary, and the binary will be `builtBy` those tasks
- The task-creation rule will be executed for each binary when closing the TaskContainer.
- Document in the user guide how to define a component, binaries and tasks for a custom model. Include some samples.

#### Open issues

- Needs to be easy to construct a task graph. The binary is 'builtBy' some assembling task, which then depend on a bunch of compile tasks.

### Story: Component, Binary and SourceSet names are limited to valid Java identifiers

### Story: Custom binary is built from Java sources

Change the sample plugin so that it compiles Java source to produce its binaries

- Uses same conventions as a Java library.
- No dependencies.

### Story: Reorganise 'cpp' project to more consistent with 'language-jvm' project

- ~~Move tasks/plugins/etc that are used to compile native languages for the native runtime into `org.gradle.language.*`~~
- ~~Move Visual Studio and CDE related classes into new subproject `ide-native`~~
    - ~~Move ide-specific integration tests as well~~
- ~~Move language-specific classes (`org.gradle.language.*`) out of `cpp` into a new subproject `language-native`~~
    - ~~Move language-related integration tests as well, breaking into a better package structure~~
- ~~Rename the remaining `cpp` subproject to `runtime-native`~~
    - Rename packages `org.gradle.nativebinaries.*` to `org.gradle.nativeruntime.*`
    - Move integration tests into `runtime-native`, breaking into a better package structure
- Move runtime-specific classes (`org.gradle.runtime.*`) out of `language-jvm` into new subproject `runtime-jvm`
- Add new `language-java` subproject and `language-groovy` subprojects: and move in any java/groovy-specific classes
    - `language-jvm` should be for common base infrastructure
- Miscellaneous
    - `runtime` subprojects should not depend on `language` subprojects
    - Split NativeSamplesIntegrationTest for subprojects
    - Reorganise samples?
    - verify that auto-tested samples are working for ide-native and language-native
    - Switch on strict compile for new subprojects
    - Remove all cycles for subprojects
    - Convert all production classes to java and use `src/main/java` instead of `src/main/groovy`

#### Open issues

- `language-native` integration tests require `ide-native` (testing visual studio project generation for particular cases)
- where should `cunit` support live?
    - production code and unit tests
    - `cunit` test fixtures

## Feature: Build author declares that a Java library depends on a Java library produced by another project

### Story: Legacy JVM language plugins declare a jvm library

- Rework the existing `SoftwareComponent` implementations so that they are `Component` implementations instead.
- Expose all native and jvm components through `project.components`.
- Don't need to support publishing yet. Attaching one of these components to a publication can result in a 'this isn't supported yet' exception.

```
apply plugin: 'java'

// The library is visible
assert jvm.libraries.main instanceof LegacyJvmLibrary
assert libraries.size() == 1
assert components.size() == 1

// The binary is visible
assert binaries.withType(ClassDirectoryBinary).size() == 1
assert binaries.withType(JarBinary).size() == 1
```

#### Test cases

- JVM library with name `main` is defined with any combination of `java`, `groovy` and `scala` plugins applied
- Web application with name `war` is defined when `war` plugin is applied.
- Can build legacy jvm library jar using standard lifecycle task
- Can mix legacy and new jvm libraries in the same project.

#### Open issues

- Expose through the DSL, or just through the APIs?
- Change `gradle dependencyInsight` to use the JVM library component to decide the default dependencies to report on.

### Story: Build author declares a dependency on another Java library

For example:

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            myLib {
                dependencies {
                    project 'other-project' // Infer the target library
                    project 'other-project' library 'my-lib'
                }
            }
        }
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one matching JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

When the project attribute refers to a project without a component plugin applied:

- At compile and runtime, include the artifacts and dependencies from the `default` configuration.

#### Open issues

- Should be able to depend on a library in the same project.
- Need an API to query the various classpaths.
- Need to be able to configure the resolution strategy for each usage.

## Feature: Build author declares that a Java library depends on an external Java library

For example:

    apply plugin: 'jvm-component'

    jvm {
        libraries {
            myLib {
                dependencies {
                    library "myorg:mylib:2.3"
                }
            }
        }
    }

This makes the jar of `myorg:mylib:2.3` and its dependencies available at both compile time and runtime.

### Open issues

- Using `library "some:thing:1.2"` will conflict with a dependency `library "someLib"` on a library declared in the same project.
Could potentially just assert that component names do not contain ':' (should do this anyway).

## Feature: Build author declares that legacy Java project depends on a Java library produced by another project

For example:

    apply plugin: 'java'

    dependencies {
        compile project: 'other-project'
    }

When the project attribute refers to a project with a component plugin applied:

- Select the target library from the libraries of the project. Assert that there is exactly one JVM library.
- At compile time, include the library's jar binary only.
- At runtime time, include the library's jar binary and runtime dependencies.

### Open issues

- Allow `library` attribute?

## Feature: Build user views the dependencies for the Java libraries of a project

The dependency reports show the dependencies of the Java libraries of a project:

- `dependencies` task
- `dependencyInsight` task
- HTML report

## Feature: Build author declares that a native component depends on a native library

Add the ability to declare dependencies directly on a native component, using a similar DSL as for Java libraries:

    apply plugin: 'cpp'

    libraries {
        myLib {
            dependencies {
                project 'other-project'
                library 'my-prebuilt'
                library 'local-lib' linkage 'api'
            }
        }
    }

Also reuse the dependency DSL at the source set level:

    apply plugin: 'cpp'

    libraries {
        myLib
    }

    sources {
        myLib {
            java {
                dependencies {
                    project 'other-project'
                    library 'my-lib' linkage 'api'
                }
            }
        }
    }

## Feature: Build author declares that the API of a Java library requires some Java library

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                api {
                    project 'other-project' library 'other-lib'
                }
            }
        }
    }

This makes the API of the library 'other-lib' available at compile time, and the runtime artifacts and dependencies of 'other-lib' available at
runtime.

It also exposes the API of the library 'other-lib' as part of the API for 'myLib', so that it is visible at compile time for any other component that
depends on 'myLib'.

The default API of a Java library is its Jar file and no dependencies.

### Open issues

- Add this to native libraries

## Feature: Build author declares that a Java library requires some Java library at runtime

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            dependencies {
                runtime {
                    project 'other-project' library 'other-lib'
                }
            }
        }
    }

### Open issues

- Add this to native libraries

## Feature: Build author declares the target JVM for a Java library

For example:

    apply plugin: 'new-java'

    platforms {
        // Java versions are visible here
    }

    libraries {
        myLib {
            buildFor platforms.java7
        }
    }

This declares that the bytecode for the binary should be generated for Java 7, and should be compiled against the Java 7 API.
Assume that the source also uses Java 7 language features.

When a library `a` depends on another library `b`, assert that the target JVM for `b` is compatible with the target JVM for `a` - that is
JVM for `a` is same or newer than the JVM for `b`.

The target JVM for a legacy Java library is the lowest of `sourceCompatibility` and `targetCompatibility`.

### Open issues

- Need to discover or configure the JDK installations.

## Feature: Build author declares a custom target platform for a Java library

For example:

    apply plugin: 'new-java'

    platforms {
        myContainer {
            runsOn platforms.java6
            provides {
                library 'myorg:mylib:1.2'
            }
        }
    }

    libraries {
        myLib {
            buildFor platforms.myContainer
        }
    }

This defines a custom container that requires Java 6 or later, and states that the library should be built for that container.

This includes the API of 'myorg:mylib:1.2' at compile time, but not at runtime. The bytecode for the library is compiled for java 6.

When a library `a` depends on another library `b`, assert that both libraries run on the same platform, or that `b` targets a JVM compatible with
the JVM for the platform of `a`.

### Open issues

- Rework the platform DSL for native component to work the same way.
- Retrofit into legacy java and web plugins.

## Feature: Build author declares dependencies for a Java source set

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            source {
                java {
                    runsOn platforms.java7
                    dependencies {
                        project 'some-project'
                        library 'myorg:mylib:1.2'
                        runtime {
                            ...
                        }
                    }
                }
            }
        }
    }


### Story: Configure the source sets of a component in the component definition

This story moves definition and configuration of the source sets for a component to live with the other component configuration.

1. Allow a component's source sets to be defined as part of the component definition:
    - Replace `ComponentSpec.getSource()` with a `getSources()` method return a `FunctionalSourceSet`. This should be the same instance that is added to the `project.sources { ... }` container.
    - Add a `ComponentSpec.source(Action<? super FunctionalSourceSet>)` method.
    - Change `ComponentModelBasePlugin.createLanguageSourceSets` to add source sets via the component's source container rather than the project's source container.
    - This step allows configuration via `component.source { ... }`.
1. Review samples to make use of this.

#### Example DSL

    nativeRuntime
        libraries {
            mylib {
                sources {
                    c {
                        lib libraries.otherlib
                    }
                    cpp {
                        include '**/*.CC'
                    }
                }
            }
        }
    }

    // Can also reach source sets via project.sources
    sources {
        mylib { ... }
    }

#### Open issues

- Merge `ProjectSourceSet` and `FunctionalSourceSet` into a more general `CompositeSourceSet`.
- Flatten out all source sets into `project.sources`. Would need to use something other than a named domain object container.
- Change `ComponentModelBasePlugin.createLanguageSourceSets` to a model rule
    - This means that source sets will not be created eagerly, which means that access to sources {} will need to be in a model block, or via ComponentSpec.sources()
    - In order for this to work, we need to be able to reference other model elements in a DSL model rule

### Story: Only attach source sets of relevant languages to component

- Don't attach Java source sets to native components.
- Don't attach native language source sets to jvm components.

This story will involve defining 'input-type' for each component type: e.g. JvmByteCode for a JvmLibraryBinary and ObjectFile for NativeBinary.
A language plugin will need to register the compiled output type for each source set. Then it will be possible for a component to only
attach to those language source sets that have an appropriate output type.

#### Test cases

- Fail when an unsupported language is used as input to a binary. eg Can't use a JavaSourceSet as input to a native binary.

#### Open issues

- Plugin should be able to declare the file types that a custom binary can be assembled from
    - Infrastructure would take care of linking up the appropriate source languages based on this.
- Plugin should to be able to declare custom language implementations.
- custom sourceSets declared via 'sources' DSL must always be declared with their type (even its type is obvious) e.g:

    apply plugin:'cpp'
    
    sources {
        lib {
            cpp(CppSourceSet)
        }
    }
        
## Feature: Build author declares dependencies for custom library

Change the sample plugin so that it allows Java and custom libraries to be used as dependencies:

    apply plugin: 'my-sample'

    libraries {
        myCustomLib {
            dependencies {
                project 'other-project'
                customUsage {
                    project 'other-project' library 'some-lib'
                }
            }
        }
    }

Allow a plugin to resolve the dependencies for a custom library, via some API. Target library must produce exactly
one binary of the target type.

Move the hard-coded Java library model out of the dependency management engine and have the jvm plugins define the
Java library type.

Resolve dependencies with inline notation:

    def compileClasspath = dependencies.newDependencySet()
                .withType(JvmLibrary.class)
                .withUsage(Usage.COMPILE)
                .forDependencies("org.group:module:1.0", ...) // Any dependency notation, or dependency instances
                .create()

    compileTask.classPath = compileClasspath.files
    assert compileClasspath.files == compileClasspath.artifactResolutionResult.files

Resolve dependencies based on a configuration:

    def testRuntimeUsage = dependencies.newDependencySet()
                .withType(JvmLibrary.class)
                .withUsage(Usage.RUNTIME)
                .forDependencies(configurations.test.incoming.dependencies)
                .create()
    copy {
        from testRuntimeUsage.artifactResolutionResult.artifactFiles
        into "libs"
    }

    testRuntimeUsage.resolutionResult.allDependencies { dep ->
        println dep.requested
    }

Resolve dependencies not added a configuration:

    dependencies {
        def lib1 = create("org.group:mylib:1.+") {
            transitive false
        }
        def projectDep = project(":foo")
    }
    def deps = dependencies.newDependencySet()
                .withType(JvmLibrary)
                .withUsage(Usage.RUNTIME)
                .forDependencies(lib1, projectDep)
                .create()
    deps.files.each {
        println it
    }

### Open issues

- Component type declares usages.
- Binary declares artifacts and dependencies for a given usage.

## Feature: Build user views the dependencies for the custom libraries of a project

Change the `dependencies`, `dependencyInsight` and HTML dependencies report so that it can report
on the dependencies of a custom component, plus whatever binaries the component happens to produce.

## Feature: Build author declares target platform for custom library

Change the sample plugin to allow a target JVM based platform to be declared:

    apply plugin: 'my-sample'

    platforms {
        // Several target platforms are visible here
    }

    libraries {
        myCustomLib {
            minSdk 12 // implies: buildFor platforms.mySdk12
        }
    }

## Feature: Java library produces multiple variants

For example:

    apply plugin: 'new-java'

    libraries {
        myLib {
            buildFor platforms.java6, platforms.java8
        }
    }

Builds a binary for Java 6 and Java 8.

Dependency resolution selects the best binary from each dependency for the target platform.

## Feature: Dependency resolution for native components

## Feature: Build user views the dependencies for the native components of a project

## Feature: User visualises project component model

### Story: User views outline of component model from command line

Present to the user some information about how a given project is composed.

- User runs `gradle components` and views report on console.
- Presents basic details of each project component:
    - JVM and native components
    - Legacy JVM library and application
    - War, Ear
    - Custom components
    - Test suites
    - Distribution
- Show source sets and binaries for each component.
- Show target platforms, flavors and build types for each component, where applicable.
- Show output files for each binary.

#### Implementation

- Add new task type to DSL guide.
- Rendering for custom types.
- Display install task for executables, test task for test suites.
- Display native language header directories.
- Display native tool locations and versions.
- Don't show the generated CUnit launcher source set as an input. Should be shown as an intermediate output, as should the object or class files.
- Add basic implementation for legacy component types.
- Add `description` to `ProjectComponent`.
- Sort things by name
- Move rendering of specific component types to live with the type and remove dependency on cpp project.
- Add some general 'show properties' rendering.
- Tweak report headers for single project builds.
- Don't show chrome when report task is the only task scheduled to run.
- Port HelpTasksPlugin and WrapperPluginAutoApplyAction to Java.
- Document in user guide.

#### Issues discovered

- Issue: language plugins add every language to every component, regardless of whether that language is supported for the component.
- Issue: TestSuite components are not included in ProjectComponentContainer.
- Issue: Display name for CUnit executable is 'C unit exe' instead of `CUnit executable'
- Issue: Better naming scheme for output directories, eg `executables/someThing/...` instead of `binaries/someThingExecutable/...`

### Story: User views component model as HTML report

TBD

# Open issues and Later work

## Component model

- Should use rules mechanism, so that this DSL lives under `model { ... }`
- Reuse the local component and binary meta-data for publication.
    - Version the meta-data schemas.
    - Source and javadoc artifacts.
- Test suites.
- Libraries declare an API that is used at compile time.
- Java component plugins support variants.
- Gradle runtime defines Gradle plugin as a type of jvm component, and Gradle as a container that runs-on the JVM.
- The `application` plugin should also declare a jvm application.
- The `war` plugin should also declare a j2ee web application.
- The `ear` plugin should also declare a j2ee application.
- Configure `JvmLibrary` and `JvmLibraryBinary`
    - Customise manifest
    - Customise compiler options
    - Customise output locations
    - Customise source directories
        - Handle layout where source and resources are in the same directory - need to filter source files

## Language support

- Need a better name for `TransformationFileType`.
- Support multiple input source sets for a component and binary.
    - Apply conflict resolution across all inputs source sets.
- Support for custom language implementations.
- Java language support
    - Java language level.
    - Source encoding.
    - Copy tests from `:plugins:org.gradle.java` and `:plugins:org.gradle.compile` into `:languageJvm` and convert to new component model
- Groovy and Scala language support, including joint compilation.
    - Language level.
- ANTLR language support.
    - Improve the generated source support from the native plugins
    - ANTLR runs on the JVM, but can target other platforms.

## Misc

- Consider splitting up `assemble` into various lifecycle tasks. There are several use cases:
    - As a developer, build me a binary I can play with or test in some way.
    - As part of some workflow, build all binaries that should be possible to build in this specific environment. Fail if a certain binary cannot be built.
      For example, if I'm on Windows build all the Windows variants and fail if the Windows SDK (with 64bit support) is not installed.
      Or, if I'm building for Android, fail if the SDK is not installed.
    - Build everything. Fail if a certain binary cannot be built.
- Lifecycle phase for binary to determine if binary can be built
    - Replace current `BinarySpec.buildable` flag
    - Attach useful error message explaining why the binary can't be built: no sources, no available toolchain, etc
    - Fail early when trying to build a binary that cannot be built
- Better cleanup when components, binaries and source sets are removed or renamed.
    - Clean up class files for binary when _all_ source files for a source set are removed.
    - Clean up class files for binary when source set is removed or renamed.
    - Clean up output files from components and binaries that have been removed or renamed.
- Expose the source and javadoc artifacts for local binaries.
- Deprecate and remove support for resolution via configurations.
- Add a report that shows the details for the components and binaries produced by a project.
- Bust up the 'plugins' project.
