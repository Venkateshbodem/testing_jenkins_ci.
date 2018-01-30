/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.provider.Provider;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.swift.SwiftApplication;
import org.gradle.language.swift.SwiftLibrary;
import org.gradle.language.swift.SwiftVersion;
import org.gradle.nativeplatform.Linkage;
import org.gradle.swiftpm.Package;
import org.gradle.swiftpm.internal.AbstractProduct;
import org.gradle.swiftpm.internal.DefaultExecutableProduct;
import org.gradle.swiftpm.internal.DefaultLibraryProduct;
import org.gradle.swiftpm.internal.DefaultPackage;
import org.gradle.swiftpm.internal.DefaultTarget;
import org.gradle.swiftpm.internal.Dependency;
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest;
import org.gradle.vcs.VcsMapping;
import org.gradle.vcs.VersionControlSpec;
import org.gradle.vcs.git.GitVersionControlSpec;
import org.gradle.vcs.internal.VcsMappingFactory;
import org.gradle.vcs.internal.VcsMappingInternal;
import org.gradle.vcs.internal.VcsMappingsStore;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * A plugin that produces a Swift Package Manager manifests from the Gradle model.
 *
 * <p>This plugin should only be applied to the root project of a build.</p>
 *
 * @since 4.6
 */
@Incubating
public class SwiftPackageManagerExportPlugin implements Plugin<Project> {
    private final VcsMappingsStore vcsMappingsStore;
    private final VcsMappingFactory vcsMappingFactory;

    @Inject
    public SwiftPackageManagerExportPlugin(VcsMappingsStore vcsMappingsStore, VcsMappingFactory vcsMappingFactory) {
        this.vcsMappingsStore = vcsMappingsStore;
        this.vcsMappingFactory = vcsMappingFactory;
    }

    @Override
    public void apply(final Project project) {
        final GenerateSwiftPackageManagerManifest manifestTask = project.getTasks().create("generateSwiftPmManifest", GenerateSwiftPackageManagerManifest.class);
        manifestTask.getManifestFile().set(project.getLayout().getProjectDirectory().file("Package.swift"));

        // Defer attaching the model until all components have been (most likely) configured
        // TODO - make this relationship explicit to make this more reliable and offer better diagnostics
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {
                Provider<Package> products = project.getProviders().provider(new MemoizingCallable(new PackageFactory(project)));
                manifestTask.getPackage().set(products);
            }
        });
    }

    private static class MemoizingCallable implements Callable<Package> {
        private Package result;
        private Callable<Package> delegate;

        MemoizingCallable(Callable<Package> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Package call() throws Exception {
            if (result == null) {
                result = delegate.call();
                delegate = null;
            }
            return result;
        }
    }

    private class PackageFactory implements Callable<Package> {
        private final Project project;

        PackageFactory(Project project) {
            this.project = project;
        }

        @Override
        public Package call() {
            Set<AbstractProduct> products = new LinkedHashSet<AbstractProduct>();
            List<DefaultTarget> targets = new ArrayList<DefaultTarget>();
            List<Dependency> dependencies = new ArrayList<Dependency>();
            SwiftVersion swiftLanguageVersion = null;
            for (Project p : project.getAllprojects()) {
                for (CppApplication application : p.getComponents().withType(CppApplication.class)) {
                    DefaultTarget target = new DefaultTarget(application.getBaseName().get(), p.getProjectDir(), application.getCppSource());
                    collectDependencies(application.getImplementationDependencies(), dependencies, target);
                    DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), target);
                    // TODO - set header dir for applications
                    products.add(product);
                    targets.add(target);
                }
                for (CppLibrary library : p.getComponents().withType(CppLibrary.class)) {
                    DefaultTarget target = new DefaultTarget(library.getBaseName().get(), p.getProjectDir(), library.getCppSource());
                    collectDependencies(library.getImplementationDependencies(), dependencies, target);
                    Set<File> headerDirs = library.getPublicHeaderDirs().getFiles();
                    if (!headerDirs.isEmpty()) {
                        // TODO - deal with more than one directory
                        target.setPublicHeaderDir(headerDirs.iterator().next());
                    }
                    targets.add(target);

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(new DefaultLibraryProduct(p.getName(), target, Linkage.SHARED));
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(new DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC));
                    }
                }
                for (SwiftApplication application : p.getComponents().withType(SwiftApplication.class)) {
                    DefaultTarget target = new DefaultTarget(application.getModule().get(), p.getProjectDir(), application.getSwiftSource());
                    swiftLanguageVersion = max(swiftLanguageVersion, application.getSourceCompatibility().getOrNull());
                    collectDependencies(application.getImplementationDependencies(), dependencies, target);
                    DefaultExecutableProduct product = new DefaultExecutableProduct(p.getName(), target);
                    products.add(product);
                    targets.add(target);
                }
                for (SwiftLibrary library : p.getComponents().withType(SwiftLibrary.class)) {
                    DefaultTarget target = new DefaultTarget(library.getModule().get(), p.getProjectDir(), library.getSwiftSource());
                    swiftLanguageVersion = max(swiftLanguageVersion, library.getSourceCompatibility().getOrNull());
                    collectDependencies(library.getImplementationDependencies(), dependencies, target);
                    targets.add(target);

                    if (library.getLinkage().get().contains(Linkage.SHARED)) {
                        products.add(new DefaultLibraryProduct(p.getName(), target, Linkage.SHARED));
                    }
                    if (library.getLinkage().get().contains(Linkage.STATIC)) {
                        products.add(new DefaultLibraryProduct(p.getName() + "Static", target, Linkage.STATIC));
                    }
                }
            }
            return new DefaultPackage(products, targets, dependencies, swiftLanguageVersion);
        }

        private SwiftVersion max(SwiftVersion v1, SwiftVersion v2) {
            if (v1 == null) {
                return v2;
            }
            if (v2 == null) {
                return v1;
            }
            if (v1.compareTo(v2) > 0) {
                return v1;
            }
            return v2;
        }

        private void collectDependencies(Configuration configuration, Collection<Dependency> dependencies, DefaultTarget target) {
            // TODO - should use publication service to do this lookup, deal with ambiguous reference and caching of the mappings
            Action<VcsMapping> mappingRule = vcsMappingsStore.getVcsMappingRule();
            for (org.gradle.api.artifacts.Dependency dependency : configuration.getAllDependencies()) {
                if (dependency instanceof ProjectDependency) {
                    ProjectDependency projectDependency = (ProjectDependency) dependency;
                    for (SwiftLibrary library : projectDependency.getDependencyProject().getComponents().withType(SwiftLibrary.class)) {
                        target.getRequiredTargets().add(library.getModule().get());
                    }
                    for (CppLibrary library : projectDependency.getDependencyProject().getComponents().withType(CppComponent.class).withType(CppLibrary.class)) {
                        target.getRequiredTargets().add(library.getBaseName().get());
                    }
                } else if (dependency instanceof ExternalModuleDependency) {
                    ExternalModuleDependency externalDependency = (ExternalModuleDependency) dependency;
                    VcsMappingInternal mapping = vcsMappingFactory.create(DefaultModuleComponentSelector.newSelector(externalDependency));
                    mappingRule.execute(mapping);
                    VersionControlSpec vcsSpec = mapping.getRepository();
                    if (vcsSpec == null || !(vcsSpec instanceof GitVersionControlSpec)) {
                        throw new InvalidUserDataException(String.format("Cannot determine the Git URL for dependency on %s:%s.", dependency.getGroup(), dependency.getName()));
                    }
                    // TODO - need to map version selector to Swift PM selector
                    String versionSelector = externalDependency.getVersion();
                    GitVersionControlSpec gitSpec = (GitVersionControlSpec) vcsSpec;
                    dependencies.add(new Dependency(gitSpec.getUrl(), versionSelector));
                    target.getRequiredProducts().add(externalDependency.getName());
                }
            }
        }
    }
}
