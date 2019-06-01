/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.plugins;

import groovy.lang.Closure;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.plugins.PluginAware;
import org.gradle.configuration.ScriptPlugin;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.verifier.HttpRedirectVerifier;
import org.gradle.util.ConfigureUtil;
import org.gradle.internal.verifier.HttpRedirectVerifierFactory;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GUtil;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultObjectConfigurationAction implements ObjectConfigurationAction {

    private final FileResolver resolver;
    private final ScriptPluginFactory configurerFactory;
    private final ScriptHandlerFactory scriptHandlerFactory;
    private final Set<Object> targets = new LinkedHashSet<Object>();
    private final Set<Runnable> actions = new LinkedHashSet<Runnable>();
    private final ClassLoaderScope parentClassLoaderScope;
    private final ClassLoaderScope scriptClassLoaderScope;
    private final TextUriResourceLoader.Factory textUriFileResourceLoaderFactory;
    private final Object defaultTarget;

    public DefaultObjectConfigurationAction(FileResolver resolver, ScriptPluginFactory configurerFactory,
                                            ScriptHandlerFactory scriptHandlerFactory, ClassLoaderScope parentClassLoaderScope,
                                            ClassLoaderScope scriptClassLoaderScope,
                                            TextUriResourceLoader.Factory textUriFileResourceLoaderFactory, Object defaultTarget) {
        this.resolver = resolver;
        this.configurerFactory = configurerFactory;
        this.scriptHandlerFactory = scriptHandlerFactory;
        this.parentClassLoaderScope = parentClassLoaderScope;
        this.scriptClassLoaderScope = scriptClassLoaderScope;
        this.textUriFileResourceLoaderFactory = textUriFileResourceLoaderFactory;
        this.defaultTarget = defaultTarget;
    }

    @Override
    public ObjectConfigurationAction to(Object... targets) {
        GUtil.flatten(targets, this.targets);
        return this;
    }

    @Override
    public ObjectConfigurationAction from(final Object script) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyScript(script);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction plugin(final Class<? extends Plugin> pluginClass) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyPlugin(pluginClass);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction plugin(final String pluginId) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyType(pluginId);
            }
        });
        return this;
    }

    @Override
    public ObjectConfigurationAction type(final Class<?> pluginClass) {
        actions.add(new Runnable() {
            @Override
            public void run() {
                applyType(pluginClass);
            }
        });
        return this;
    }

    private HttpRedirectVerifier createHttpRedirectVerifier(URI scriptUri) {
        return  HttpRedirectVerifierFactory.create(
            scriptUri,
            false,
            () -> DeprecationLogger
                .nagUserOfDeprecated(
                    "Applying script plugins from insecure URIs",
                        String.format("Use '%s' instead or try 'apply from: resources.text.fromInsecureUri(\"%s\")' to silence the warning.", GUtil.toSecureUrl(scriptUri), scriptUri),
                        String.format("The provided URI '%s' uses an insecure protocol (HTTP).", scriptUri)

                ),
            redirect -> DeprecationLogger
                .nagUserOfDeprecated(
                    "Applying script plugins from an insecure redirect",
                    "Switch to HTTPS or use TextResourceFactory.fromInsecureUri(Object) to silence the warning.",
                    String.format(
                        "'%s' redirects to insecure '%s'.",
                        scriptUri,
                        redirect
                    )
                )
        );
    }

    private void applyScript(Object script) {
        URI scriptUri = resolver.resolveUri(script);
        TextResource resource;
        if (script instanceof TextResource) {
            resource = (TextResource) script;
        } else {
            HttpRedirectVerifier redirectVerifier = createHttpRedirectVerifier(scriptUri);
            resource = textUriFileResourceLoaderFactory.create(redirectVerifier).loadUri("script", scriptUri);
        }
        ScriptSource scriptSource = new TextResourceScriptSource(resource);
        ClassLoaderScope classLoaderScopeChild = parentClassLoaderScope.createChild("script-" + scriptUri.toString());
        ScriptHandler scriptHandler = scriptHandlerFactory.create(scriptSource, classLoaderScopeChild);
        ScriptPlugin configurer = configurerFactory.create(scriptSource, scriptHandler, classLoaderScopeChild, parentClassLoaderScope, false);
        for (Object target : targets) {
            configurer.apply(target);
        }
    }

    private void applyPlugin(Class<? extends Plugin> pluginClass) {
        applyType(pluginClass);
    }

    private void applyType(String pluginId) {
        ClassLoaderScope pluginClassLoaderScope = scriptClassLoaderScope;

        // use the classloader scope of the owner (the script) of the currently executing closure (if any)
        // for doing the plugin lookup by id
        Closure currentConfigureClosure = ConfigureUtil.WrappedConfigureAction.getCurrentConfigureClosure();
        if (currentConfigureClosure != null && currentConfigureClosure.getOwner() instanceof DefaultScript) {
            ScriptHandler scriptHandler = ((DefaultScript) currentConfigureClosure.getOwner()).getBuildscript();
            if (scriptHandler instanceof ScriptHandlerInternal) {
                pluginClassLoaderScope = ((ScriptHandlerInternal) scriptHandler).getClassLoaderScope();
            }
        }

        for (Object target : targets) {
            if (target instanceof PluginAware) {
                PluginManagerInternal pluginManager = (PluginManagerInternal) ((PluginAware) target).getPluginManager();
                pluginManager.apply(pluginClassLoaderScope, pluginId);
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin with id '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginId, target.toString(), target.getClass().getName()));
            }
        }
    }

    private void applyType(Class<?> pluginClass) {
        for (Object target : targets) {
            if (target instanceof PluginAware) {
                ((PluginAware) target).getPluginManager().apply(pluginClass);
            } else {
                throw new UnsupportedOperationException(String.format("Cannot apply plugin of class '%s' to '%s' (class: %s) as it does not implement PluginAware", pluginClass.getName(), target.toString(), target.getClass().getName()));
            }
        }
    }

    public void execute() {
        if (targets.isEmpty()) {
            to(defaultTarget);
        }

        for (Runnable action : actions) {
            action.run();
        }
    }
}
