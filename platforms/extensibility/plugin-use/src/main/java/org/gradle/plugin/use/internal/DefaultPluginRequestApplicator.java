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

package org.gradle.plugin.use.internal;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.MutableClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.ClassloaderBackedPluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginDescriptorLocator;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.plugins.InvalidPluginException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.LocationAwareException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.resolve.internal.AlreadyOnClasspathIgnoringPluginResolver;
import org.gradle.plugin.use.resolve.internal.AlreadyOnClasspathPluginResolver;
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories;
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositoriesProvider;
import org.gradle.plugin.use.resolve.internal.PluginResolution;
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor;
import org.gradle.plugin.use.resolve.internal.PluginResolver;
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultPluginRequestApplicator implements PluginRequestApplicator {
    private final PluginRegistry pluginRegistry;
    private final PluginResolverFactory pluginResolverFactory;
    private final PluginArtifactRepositoriesProvider pluginRepositoriesProvider;
    private final PluginResolutionStrategyInternal pluginResolutionStrategy;
    private final PluginInspector pluginInspector;
    private final PluginVersionTracker pluginVersionTracker;
    private final PluginApplicationListener pluginApplicationListenerBroadcaster;

    public DefaultPluginRequestApplicator(
        PluginRegistry pluginRegistry,
        PluginResolverFactory pluginResolverFactory,
        PluginArtifactRepositoriesProvider pluginRepositoriesProvider,
        PluginResolutionStrategyInternal pluginResolutionStrategy,
        PluginInspector pluginInspector,
        PluginVersionTracker pluginVersionTracker,
        ListenerManager listenerManager
    ) {
        this.pluginRegistry = pluginRegistry;
        this.pluginResolverFactory = pluginResolverFactory;
        this.pluginRepositoriesProvider = pluginRepositoriesProvider;
        this.pluginResolutionStrategy = pluginResolutionStrategy;
        this.pluginInspector = pluginInspector;
        this.pluginVersionTracker = pluginVersionTracker;
        this.pluginApplicationListenerBroadcaster = listenerManager.getBroadcaster(PluginApplicationListener.class);
    }

    @Override
    public void applyPlugins(PluginRequests requests, PluginRequests autoAppliedPlugins, final ScriptHandlerInternal scriptHandler, @Nullable final PluginManagerInternal target, final ClassLoaderScope classLoaderScope) {
        if (target == null || noPluginsApplied(requests, autoAppliedPlugins)) {
            classLoaderScope.export(scriptHandler.getInstrumentedScriptClassPath());
            classLoaderScope.lock();
            return;
        }

        PluginArtifactRepositories resolveContext = pluginRepositoriesProvider.createPluginResolveRepositories();
        resolveContext.applyRepositoriesTo(scriptHandler.getRepositories());

        List<ApplyAction> pluginApplyActions = new ArrayList<>();

        PluginResolver pluginResolver = wrapInAlreadyInClasspathResolver(classLoaderScope, resolveContext);

        if (!requests.isEmpty()) {
            resolvePluginsAndBuildClasspath(requests, scriptHandler, classLoaderScope, pluginResolver, pluginApplyActions);
        }
        classLoaderScope.lock();

        if (!autoAppliedPlugins.isEmpty()) {
            PluginResolver alreadyOnClasspathIgnoringPluginResolver =
                new AlreadyOnClasspathIgnoringPluginResolver(
                    pluginResolver,
                    new ClassloaderBackedPluginDescriptorLocator(scriptHandler.getClassLoader())
                );

            // Reset class loader & script handler since we are re-resolving classpath
            MutableClassLoaderScope mutableClassLoaderScope = classLoaderScope.asMutable("-plugins");
            scriptHandler.dropResolvedClassPath();

            resolvePluginsAndBuildClasspath(autoAppliedPlugins, scriptHandler, mutableClassLoaderScope, alreadyOnClasspathIgnoringPluginResolver, pluginApplyActions);
        }
        classLoaderScope.lock();

        // Apply the plugins
        pluginApplyActions.forEach(action -> action.apply(target));
    }

    private void resolvePluginsAndBuildClasspath(
        PluginRequests requests,
        ScriptHandlerInternal scriptHandler,
        ClassLoaderScope classLoaderScope,
        PluginResolver pluginResolver,
        List<ApplyAction> pluginApplyActions
    ) {
        boolean resolvedPlugin = false;
        CollectingPluginRequestResolutionVisitor pluginDependencies = new CollectingPluginRequestResolutionVisitor();
        for (PluginRequestInternal originalRequest : requests) {
            PluginRequestInternal request = pluginResolutionStrategy.applyTo(originalRequest);

            PluginResolutionResult result = resolveToFoundResult(pluginResolver, request);
            if (result.isAlreadyApplied()) {
                continue;
            }

            resolvedPlugin = true;
            PluginResolution resolved = result.getFound();

            resolved.accept(pluginDependencies);

            if (request.isApply()) {
                pluginApplyActions.add(new ApplyAction(request, resolved));
            }

            String pluginVersion = resolved.getPluginVersion();
            if (pluginVersion != null) {
                pluginVersionTracker.setPluginVersionAt(
                    classLoaderScope,
                    resolved.getPluginId().getId(),
                    pluginVersion
                );
            }
        }

        // Only re-define the classpath if we resolved a new plugin, otherwise we break caching.
        if (resolvedPlugin) {
            pluginDependencies.getAdditionalDependencies().forEach(scriptHandler::addScriptClassPathDependency);
            classLoaderScope.export(scriptHandler.getInstrumentedScriptClassPath());
            pluginDependencies.getAdditionalClassloaders().forEach(classLoaderScope::export);
        }
    }

    private PluginResolver wrapInAlreadyInClasspathResolver(ClassLoaderScope classLoaderScope, PluginArtifactRepositories resolveContext) {
        ClassLoaderScope parentLoaderScope = classLoaderScope.getParent();
        PluginDescriptorLocator scriptClasspathPluginDescriptorLocator = new ClassloaderBackedPluginDescriptorLocator(parentLoaderScope.getExportClassLoader());
        PluginResolver pluginResolver = pluginResolverFactory.create(resolveContext);
        return new AlreadyOnClasspathPluginResolver(pluginResolver, pluginRegistry, parentLoaderScope, scriptClasspathPluginDescriptorLocator, pluginInspector, pluginVersionTracker);
    }

    /**
     * The action that applies a plugin.
     */
    private class ApplyAction {
        private final PluginRequestInternal request;
        private final PluginResolution resolved;

        public ApplyAction(PluginRequestInternal request, PluginResolution resolved) {
            this.request = request;
            this.resolved = resolved;
        }

        public void apply(PluginManagerInternal target) {
            try {
                try {
                    pluginApplicationListenerBroadcaster.pluginApplied(request);
                    resolved.applyTo(target);
                } catch (UnknownPluginException e) {
                    throw couldNotApply(request, request.getId(), e);
                } catch (Exception e) {
                    throw exceptionOccurred(request, e);
                }
            } catch (Exception e) {
                throw new LocationAwareException(e, request.getScriptDisplayName(), request.getLineNumber());
            }
        }
    }

    private static boolean noPluginsApplied(PluginRequests requests, PluginRequests autoAppliedPlugins) {
        return requests.isEmpty() && autoAppliedPlugins.isEmpty();
    }

    private static InvalidPluginException couldNotApply(PluginRequestInternal request, PluginId id, UnknownPluginException cause) {
        return new InvalidPluginException(
            String.format(
                "Could not apply requested plugin %s as it does not provide a plugin with id '%s'."
                    + " This is caused by an incorrect plugin implementation."
                    + " Please contact the plugin author(s).",
                request.getDisplayName(), id),
            cause);
    }

    private static InvalidPluginException exceptionOccurred(PluginRequestInternal request, Exception e) {
        return new InvalidPluginException(String.format("An exception occurred applying plugin request %s", request.getDisplayName()), e);
    }

    private static PluginResolutionResult resolveToFoundResult(PluginResolver effectivePluginResolver, PluginRequestInternal request) {
        PluginResolutionResult result;
        try {
            result = effectivePluginResolver.resolve(request);
        } catch (Exception e) {
            throw new LocationAwareException(
                new GradleException(String.format("Error resolving plugin %s", request.getDisplayName()), e),
                request.getScriptDisplayName(), request.getLineNumber());
        }

        result.assertSuccess(request);
        return result;
    }

    private static class CollectingPluginRequestResolutionVisitor implements PluginResolutionVisitor {
        private List<Dependency> additionalDependencies;
        private List<ClassLoader> additionalClassloaders;

        @Override
        public void visitDependency(Dependency dependency) {
            if (additionalDependencies == null) {
                additionalDependencies = new ArrayList<>();
            }
            additionalDependencies.add(dependency);
        }

        @Override
        public void visitClassLoader(ClassLoader classLoader) {
            if (additionalClassloaders == null) {
                additionalClassloaders = new ArrayList<>();
            }
            additionalClassloaders.add(classLoader);
        }

        public List<Dependency> getAdditionalDependencies() {
            if (additionalDependencies == null) {
                return Collections.emptyList();
            }
            return additionalDependencies;
        }

        public List<ClassLoader> getAdditionalClassloaders() {
            if (additionalClassloaders == null) {
                return Collections.emptyList();
            }
            return additionalClassloaders;
        }
    }

}
