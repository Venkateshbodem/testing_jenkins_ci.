/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.play.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.*;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.tasks.Jar;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.model.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.DefaultPlatformRequirement;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.play.JvmClasses;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.PublicAssets;
import org.gradle.play.internal.*;
import org.gradle.play.internal.platform.PlayPlatformInternal;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.PlayRun;
import org.gradle.play.tasks.RoutesCompile;
import org.gradle.play.tasks.TwirlCompile;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

/**
 * Plugin for Play Framework component support. Registers the {@link org.gradle.play.PlayApplicationSpec} component type for the {@link org.gradle.platform.base.ComponentSpecContainer}.
 */

@Incubating
public class PlayApplicationPlugin implements Plugin<Project> {
    private final static String DEFAULT_PLAY_VERSION = "2.3.7";
    public static final int DEFAULT_HTTP_PORT = 9000;

    @Override
    public void apply(Project project) {
        project.getExtensions().create("playConfigurations", PlayPluginConfigurations.class, project.getConfigurations(), project.getDependencies());
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {
        @Model
        PlayPluginConfigurations configurations(ExtensionContainer extensions) {
            return extensions.getByType(PlayPluginConfigurations.class);
        }

        @Model
        PlayToolChainInternal playToolChain(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(PlayToolChainInternal.class);
        }

        @Model
        FileResolver fileResolver(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(FileResolver.class);
        }

        @ComponentType
        void register(ComponentTypeBuilder<PlayApplicationSpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationSpec.class);
        }

        @Mutate
        public void registerPlatformResolver(PlatformResolvers platformResolvers) {
            platformResolvers.register(new PlayPlatformResolver());
        }

        @Mutate
        void createDefaultPlayApp(CollectionBuilder<PlayApplicationSpec> builder) {
            builder.create("play");
        }

        @BinaryType
        void registerApplication(BinaryTypeBuilder<PlayApplicationBinarySpec> builder) {
            builder.defaultImplementation(DefaultPlayApplicationBinarySpec.class);
        }

        @Mutate
        void configureDefaultPlaySources(ComponentSpecContainer components, ServiceRegistry serviceRegistry) {
            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            components.withType(PlayApplicationSpec.class).all(new Action<PlayApplicationSpec>() {
                public void execute(PlayApplicationSpec playComponent) {
                    // TODO:DAZ Scala source set type should be registered via scala-lang plugin
                    ScalaLanguageSourceSet appSources = BaseLanguageSourceSet.create(DefaultScalaLanguageSourceSet.class, "appSources", playComponent.getName(), fileResolver, instantiator);

                    // Compile scala/java sources under /app\
                    // TODO:DAZ Should be selecting 'controllers/**' and 'model/**' I think, allowing user to add more includes
                    appSources.getSource().srcDir("app");
                    appSources.getSource().include("**/*.scala");
                    appSources.getSource().include("**/*.java");
                    ((ComponentSpecInternal) playComponent).getSources().add(appSources);
                }
            });
        }

        @Validate
        void failOnMultiplePlayComponents(CollectionBuilder<PlayApplicationSpec> container) {
            if (container.size() >= 2) {
                throw new GradleException("Multiple components of type 'PlayApplicationSpec' are not supported.");
            }
        }

        @Finalize
        void failOnMultipleTargetPlatforms(ComponentSpecContainer container) {
            for (PlayApplicationSpecInternal playApplicationSpec : container.withType(PlayApplicationSpecInternal.class)) {
                if (playApplicationSpec.getTargetPlatforms().size() > 1) {
                    throw new GradleException("Multiple target platforms for 'PlayApplicationSpec' is not (yet) supported.");
                }
            }
        }

        @ComponentBinaries
        void createBinaries(CollectionBuilder<PlayApplicationBinarySpec> binaries, final PlayApplicationSpec componentSpec,
                            final PlatformResolvers platforms, final PlayToolChainInternal playToolChainInternal, final PlayPluginConfigurations configurations,
                            final ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir, final ProjectIdentifier projectIdentifier) {

            final FileResolver fileResolver = serviceRegistry.get(FileResolver.class);
            final Instantiator instantiator = serviceRegistry.get(Instantiator.class);
            final String binaryName = String.format("%sBinary", componentSpec.getName());

            binaries.create(binaryName, new Action<PlayApplicationBinarySpec>() {
                public void execute(PlayApplicationBinarySpec playBinary) {
                    PlayApplicationBinarySpecInternal playBinaryInternal = (PlayApplicationBinarySpecInternal) playBinary;
                    final File binaryBuildDir = new File(buildDir, binaryName);

                    final PlayPlatform chosenPlatform = resolveTargetPlatform(componentSpec, platforms, configurations);
                    initialiseConfigurations(configurations, chosenPlatform);

                    playBinaryInternal.setTargetPlatform(chosenPlatform);
                    playBinaryInternal.setToolChain(playToolChainInternal);

                    File mainJar = new File(binaryBuildDir, String.format("lib/%s.jar", projectIdentifier.getName()));
                    File assetsJar = new File(binaryBuildDir, String.format("lib/%s-assets.jar", projectIdentifier.getName()));
                    playBinaryInternal.setJarFile(mainJar);
                    playBinaryInternal.setAssetsJarFile(assetsJar);

                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", null, new Date(), mainJar, playBinaryInternal));
                    configurations.getPlay().addArtifact(new DefaultPublishArtifact(projectIdentifier.getName(), "jar", "jar", "assets", new Date(), assetsJar, playBinaryInternal));

                    JvmClasses classes = playBinary.getClasses();
                    classes.setClassesDir(new File(binaryBuildDir, "classes"));

                    // TODO:DAZ These should be configured on the component
                    classes.addResourceDir(new File(projectIdentifier.getProjectDir(), "conf"));

                    PublicAssets assets = playBinary.getAssets();
                    assets.addAssetDir(new File(projectIdentifier.getProjectDir(), "public"));

                    ScalaLanguageSourceSet genSources = BaseLanguageSourceSet.create(DefaultScalaLanguageSourceSet.class, "genSources", binaryName, fileResolver, instantiator);
                    playBinaryInternal.setGeneratedScala(genSources);

                    playBinaryInternal.setClasspath(configurations.getPlay().getFileCollection());
                }
            });
        }

        private PlayPlatform resolveTargetPlatform(PlayApplicationSpec componentSpec, final PlatformResolvers platforms, PlayPluginConfigurations configurations) {
            PlatformRequirement targetPlatform = getTargetPlatform((PlayApplicationSpecInternal) componentSpec);
            return platforms.resolve(PlayPlatform.class, targetPlatform);
        }

        private PlatformRequirement getTargetPlatform(PlayApplicationSpecInternal playApplicationSpec) {
            if (playApplicationSpec.getTargetPlatforms().isEmpty()) {
                String defaultPlayPlatform = String.format("play-%s", DEFAULT_PLAY_VERSION);
                return DefaultPlatformRequirement.create(defaultPlayPlatform);
            }
            if (playApplicationSpec.getTargetPlatforms().size() == 1) {
                return playApplicationSpec.getTargetPlatforms().get(0);
            }
            throw new InvalidUserDataException("Play application can only target a single platform");
        }

        private void initialiseConfigurations(PlayPluginConfigurations configurations, PlayPlatform playPlatform) {
            configurations.getPlayPlatform().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play"));
            configurations.getPlayTest().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-test"));
            configurations.getPlayRun().addDependency(((PlayPlatformInternal) playPlatform).getDependencyNotation("play-docs"));
        }

        @BinaryTasks
        void createTwirlCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String twirlCompileTaskName = String.format("twirlCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(twirlCompileTaskName, TwirlCompile.class, new Action<TwirlCompile>() {
                public void execute(TwirlCompile twirlCompile) {
                    twirlCompile.setPlatform(binary.getTargetPlatform());
                    twirlCompile.setSourceDirectory(new File(projectIdentifier.getProjectDir(), "app"));
                    twirlCompile.include("**/*.html");

                    File twirlCompilerOutputDirectory = new File(buildDir, String.format("%s/src/%s", binary.getName(), twirlCompileTaskName));
                    twirlCompile.setOutputDirectory(twirlCompilerOutputDirectory);

                    binary.getGeneratedScala().getSource().srcDir(twirlCompilerOutputDirectory);
                    binary.getGeneratedScala().builtBy(twirlCompile);
                }
            });
        }

        @BinaryTasks
        void createRoutesCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String routesCompileTaskName = String.format("routesCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(routesCompileTaskName, RoutesCompile.class, new Action<RoutesCompile>() {
                public void execute(RoutesCompile routesCompile) {
                    routesCompile.setPlatform(binary.getTargetPlatform());
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(new File(projectIdentifier.getProjectDir(), "conf"));
                    routesCompile.include("*.routes");
                    routesCompile.include("routes");

                    final File routesCompilerOutputDirectory = new File(buildDir, String.format("%s/src/%s", binary.getName(), routesCompileTaskName));
                    routesCompile.setOutputDirectory(routesCompilerOutputDirectory);

                    binary.getGeneratedScala().getSource().srcDir(routesCompilerOutputDirectory);
                    binary.getGeneratedScala().builtBy(routesCompile);
                }
            });
        }

        @BinaryTasks
        void createScalaCompile(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary,
                                FileResolver fileResolver, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
            final String scalaCompileTaskName = String.format("scalaCompile%s", StringUtils.capitalize(binary.getName()));
            tasks.create(scalaCompileTaskName, PlatformScalaCompile.class, new Action<PlatformScalaCompile>() {
                public void execute(PlatformScalaCompile scalaCompile) {
                    scalaCompile.setDestinationDir(binary.getClasses().getClassesDir());
                    scalaCompile.setPlatform(binary.getTargetPlatform().getScalaPlatform());
                    //infer scala classpath
                    String targetCompatibility = binary.getTargetPlatform().getJavaPlatform().getTargetCompatibility().getMajorVersion();
                    scalaCompile.setSourceCompatibility(targetCompatibility);
                    scalaCompile.setTargetCompatibility(targetCompatibility);

                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", scalaCompileTaskName)));

                    for (LanguageSourceSet appSources : binary.getSource().withType(ScalaLanguageSourceSet.class)) {
                        scalaCompile.source(appSources.getSource());
                        scalaCompile.dependsOn(appSources);
                    }
                    scalaCompile.source(binary.getGeneratedScala().getSource());
                    scalaCompile.dependsOn(binary.getGeneratedScala());

                    scalaCompile.setClasspath(binary.getClasspath());

                    binary.getClasses().builtBy(scalaCompile);
                }
            });
        }

        @BinaryTasks
        void createJarTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpecInternal binary) {
            String jarTaskName = String.format("create%sJar", StringUtils.capitalize(binary.getName()));
            tasks.create(jarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getJarFile().getParentFile());
                    jar.setArchiveName(binary.getJarFile().getName());
                    jar.from(binary.getClasses().getClassesDir());
                    jar.from(binary.getClasses().getResourceDirs());
                    jar.dependsOn(binary.getClasses());
                }
            });

            String assetsJarTaskName = String.format("create%sAssetsJar", StringUtils.capitalize(binary.getName()));
            tasks.create(assetsJarTaskName, Jar.class, new Action<Jar>() {
                public void execute(Jar jar) {
                    jar.setDestinationDir(binary.getAssetsJarFile().getParentFile());
                    jar.setArchiveName(binary.getAssetsJarFile().getName());
                    jar.setClassifier("assets");
                    CopySpecInternal newSpec = jar.getRootSpec().addChild();
                    newSpec.from(binary.getAssets().getAssetDirs());
                    newSpec.into("public");
                    jar.dependsOn(binary.getAssets());
                }
            });
        }

        // TODO:DAZ Need a nice way to create tasks that are associated with a binary but not part of _building_ it.
        @Mutate
        void createPlayRunTask(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, final PlayToolChainInternal toolChain, final PlayPluginConfigurations configurations) {
            for (final PlayApplicationBinarySpecInternal binary : binaryContainer.withType(PlayApplicationBinarySpecInternal.class)) {
                String runTaskName = String.format("run%s", StringUtils.capitalize(binary.getName()));
                tasks.create(runTaskName, PlayRun.class, new Action<PlayRun>() {
                    public void execute(PlayRun playRun) {
                        playRun.setHttpPort(DEFAULT_HTTP_PORT);
                        playRun.setToolProvider(toolChain.select(binary.getTargetPlatform()));
                        playRun.setApplicationJar(binary.getJarFile());
                        playRun.setAssetsJar(binary.getAssetsJarFile());
                        playRun.setRuntimeClasspath(configurations.getPlayRun().getFileCollection());
                        playRun.dependsOn(binary.getBuildTask());
                    }
                });
            }
        }
    }
}
