/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.jvm.application.tasks;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.internal.plugins.UnixStartScriptGenerator;
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.application.scripts.ScriptGenerator;
import org.gradle.util.internal.GUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType.GETTER;

/**
 * Creates start scripts for launching JVM applications.
 * <p>
 * Example:
 * <pre class='autoTested'>
 * task createStartScripts(type: CreateStartScripts) {
 *   outputDir = file('build/sample')
 *   mainClass = 'org.gradle.test.Main'
 *   applicationName = 'myApp'
 *   classpath = files('path/to/some.jar')
 * }
 * </pre>
 * <p>
 * Note: the Gradle {@code "application"} plugin adds a pre-configured task of this type named {@code "startScripts"}.
 * <p>
 * The task generates separate scripts targeted at Microsoft Windows environments and UNIX-like environments (e.g. Linux, macOS).
 * The actual generation is implemented by the {@link #getWindowsStartScriptGenerator()} and {@link #getUnixStartScriptGenerator()} properties, of type {@link ScriptGenerator}.
 * <p>
 * Example:
 * <pre class='autoTested'>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
 *   windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
 * }
 *
 * class CustomUnixStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 *
 * class CustomWindowsStartScriptGenerator implements ScriptGenerator {
 *   void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
 *     // implementation
 *   }
 * }
 * </pre>
 * <p>
 * The default generators are of the type {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}, with default templates.
 * This templates can be changed via the {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator#setTemplate(org.gradle.api.resources.TextResource)} method.
 * <p>
 * The default implementations used by this task use <a href="https://docs.groovy-lang.org/latest/html/documentation/template-engines.html#_simpletemplateengine">Groovy's SimpleTemplateEngine</a>
 * to parse the template, with the following variables available:
 *
 * <ul>
 * <li>{@code applicationName}</li>
 * <li>{@code optsEnvironmentVar}</li>
 * <li>{@code exitEnvironmentVar}</li>
 * <li>{@code mainModule}</li>
 * <li>{@code mainClass}</li>
 * <li>{@code executableDir}</li>
 * <li>{@code defaultJvmOpts}</li>
 * <li>{@code appNameSystemProperty}</li>
 * <li>{@code appHomeRelativePath}</li>
 * <li>{@code classpath}</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * task createStartScripts(type: CreateStartScripts) {
 *   unixStartScriptGenerator.template = resources.text.fromFile('customUnixStartScript.txt')
 *   windowsStartScriptGenerator.template = resources.text.fromFile('customWindowsStartScript.txt')
 * }
 * </pre>
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class CreateStartScripts extends ConventionTask {

    private final Property<String> mainModule;
    private final Property<String> mainClass;
    private final Property<String> optsEnvironmentVar;
    private final Property<String> exitEnvironmentVar;
    private final Property<String> executableDir;
    private final ModularitySpec modularity;
    private ScriptGenerator unixStartScriptGenerator = new UnixStartScriptGenerator();
    private ScriptGenerator windowsStartScriptGenerator = new WindowsStartScriptGenerator();
    private final DirectoryProperty outputDir;
    private final Property<String> applicationName;

    public CreateStartScripts() {
        this.mainModule = getObjectFactory().property(String.class);
        this.mainClass = getObjectFactory().property(String.class);
        this.modularity = getObjectFactory().newInstance(DefaultModularitySpec.class);
        this.applicationName = getObjectFactory().property(String.class);
        this.outputDir = getObjectFactory().directoryProperty();
        this.exitEnvironmentVar = getObjectFactory().property(String.class).convention(getApplicationName().map(appName -> GUtil.toConstant(appName) + "_EXIT_CONSOLE"));
        this.optsEnvironmentVar = getObjectFactory().property(String.class).convention(getApplicationName().map(appName -> GUtil.toConstant(appName) + "_OPTS"));
        this.executableDir = getObjectFactory().property(String.class).convention("bin");
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaModuleDetector getJavaModuleDetector() {
        throw new UnsupportedOperationException();
    }

    /**
     * The environment variable to use to provide additional options to the JVM.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public Property<String> getOptsEnvironmentVar() {
        return optsEnvironmentVar;
    }

    /**
     * The environment variable to use to control exit value (Windows only).
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public Property<String> getExitEnvironmentVar() {
        return exitEnvironmentVar;
    }

    /**
     * Returns the full path to the Unix script. The target directory is represented by the output directory, the file name is the application name without a file extension.
     * TODO: This should be Provider[RegularFile], but we don't support such upgrade with @ReplacesEagerProperty
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getUnixScript"))
    public RegularFileProperty getUnixScript() {
        return getObjectFactory().fileProperty().value(
            getOutputDir().zip(getApplicationName(), Directory::file)
        );
    }

    /**
     * Returns the full path to the Windows script. The target directory is represented by the output directory, the file name is the application name plus the file extension .bat.
     * TODO: This should be Provider[RegularFile], but we don't support such upgrade with @ReplacesEagerProperty
     */
    @Internal
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = GETTER, name = "getWindowsScript"))
    public RegularFileProperty getWindowsScript() {
        return getObjectFactory().fileProperty().value(
            getOutputDir().zip(getApplicationName(), (outputDir, applicationName) -> outputDir.file(applicationName + ".bat"))
        );
    }

    /**
     * The directory to write the scripts into.
     */
    @OutputDirectory
    @ReplacesEagerProperty
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    /**
     * The directory to write the scripts into in the distribution.
     * @since 4.5
     */
    @Input
    @ReplacesEagerProperty
    public Property<String> getExecutableDir() {
        return executableDir;
    }

    /**
     * The main module name used to start the modular Java application.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getMainModule() {
        return mainModule;
    }

    /**
     * The main class name used to start the Java application.
     *
     * @since 6.4
     */
    @Optional
    @Input
    public Property<String> getMainClass() {
        return mainClass;
    }

    /**
     * The main class name used to start the Java application.
     */
    @Input
    @Optional
    @Nullable
    @Deprecated
    public String getMainClassName() {
        DeprecationLogger.deprecateProperty(CreateStartScripts.class, "mainClassName")
            .replaceWith("mainClass")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();

        return mainClass.getOrNull();
    }

    @Deprecated
    public void setMainClassName(@Nullable String mainClassName) {
        DeprecationLogger.deprecateProperty(CreateStartScripts.class, "mainClassName")
            .replaceWith("mainClass")
            .willBeRemovedInGradle9()
            .withDslReference()
            .nagUser();

        this.mainClass.set(mainClassName);
    }

    /**
     * The application's default JVM options. Defaults to an empty list.
     */
    @Optional
    @Input
    @ReplacesEagerProperty(originalType = Iterable.class)
    public abstract ListProperty<String> getDefaultJvmOpts();

    /**
     * The application's name.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public Property<String> getApplicationName() {
        return applicationName;
    }

    /**
     * The class path for the application.
     */
    @Classpath
    @Optional
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the module path handling for executing the main class.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * The UNIX-like start script generator.
     * <p>
     * Defaults to an implementation of {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
     */
    @Internal
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    public ScriptGenerator getUnixStartScriptGenerator() {
        return unixStartScriptGenerator;
    }

    public void setUnixStartScriptGenerator(ScriptGenerator unixStartScriptGenerator) {
        this.unixStartScriptGenerator = unixStartScriptGenerator;
    }

    /**
     * The Windows start script generator.
     * <p>
     * Defaults to an implementation of {@link org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator}.
     */
    @Internal
    @ToBeReplacedByLazyProperty(comment = "Should this be lazy?")
    public ScriptGenerator getWindowsStartScriptGenerator() {
        return windowsStartScriptGenerator;
    }

    public void setWindowsStartScriptGenerator(ScriptGenerator windowsStartScriptGenerator) {
        this.windowsStartScriptGenerator = windowsStartScriptGenerator;
    }

    @TaskAction
    public void generate() {
        StartScriptGenerator generator = new StartScriptGenerator(unixStartScriptGenerator, windowsStartScriptGenerator);
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        generator.setApplicationName(getApplicationName().getOrNull());
        generator.setMainClassName(fullMainArgument());
        generator.setDefaultJvmOpts(getDefaultJvmOpts().getOrElse(Collections.emptyList()));
        generator.setOptsEnvironmentVar(getOptsEnvironmentVar().getOrNull());
        generator.setExitEnvironmentVar(getExitEnvironmentVar().getOrNull());
        generator.setClasspath(getRelativePath(javaModuleDetector.inferClasspath(mainModule.isPresent(), getClasspath())));
        generator.setModulePath(getRelativePath(javaModuleDetector.inferModulePath(mainModule.isPresent(), getClasspath())));
        String executableDir = getExecutableDir().getOrNull();
        if (StringUtils.isEmpty(executableDir)) {
            generator.setScriptRelPath(getUnixScript().getAsFile().get().getName());
        } else {
            generator.setScriptRelPath(executableDir + "/" + getUnixScript().getAsFile().get().getName());
        }
        generator.generateUnixScript(getUnixScript().getAsFile().get());
        generator.generateWindowsScript(getWindowsScript().getAsFile().get());
    }

    private String fullMainArgument() {
        String main = "";
        if (mainModule.isPresent()) {
            main += "--module ";
            main += mainModule.get();
            if (mainClass.isPresent()) {
                main += "/";
            }
        }
        if (mainClass.isPresent()) {
            main += mainClass.get();
        }
        return main;
    }

    /**
     * TODO: Remove with Gradle 9, we anyway track classpath via {@link #getClasspath()}, this looks unnecessary
     */
    @Input
    @ToBeReplacedByLazyProperty(unreported = true, comment = "Skipped for report since method is protected")
    protected Iterable<String> getRelativeClasspath() {
        //a list instance is needed here, as org.gradle.internal.snapshot.ValueSnapshotter.processValue() does not support
        //serializing Iterators directly
        final FileCollection classpathNullable = getClasspath();
        if (classpathNullable == null) {
            return Collections.emptyList();
        }
        return getRelativePath(classpathNullable);
    }

    private Iterable<String> getRelativePath(FileCollection path) {
        return path.getFiles().stream().map(input -> "lib/" + input.getName()).collect(Collectors.toCollection(Lists::newArrayList));
    }

}
