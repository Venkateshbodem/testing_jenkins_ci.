/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.component.local.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.Configuration;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData;
import org.gradle.internal.component.model.*;

import java.io.File;
import java.util.*;

public class DefaultLocalComponentMetaData implements MutableLocalComponentMetaData {
    private final Map<String, PublishArtifactSet> configurationArtifacts = Maps.newHashMap();
    private final Map<ComponentArtifactIdentifier, DefaultLocalArtifactMetaData> artifactsById = Maps.newHashMap();
    private final Map<String, Configuration> allIvyConfigurations = Maps.newHashMap();
    private final List<DependencyMetaData> dependencies = Lists.newArrayList();
    private final List<ExcludeRule> allExcludeRules = Lists.newArrayList();
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private final String status;
    private boolean artifactsResolved;

    public DefaultLocalComponentMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, String status) {
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.status = status;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public void addArtifacts(String configuration, PublishArtifactSet artifacts) {
        if (artifactsResolved) {
            throw new IllegalStateException("Cannot add artifacts after resolve");
        }
        configurationArtifacts.put(configuration, artifacts);
    }

    // TODO:DAZ We shouldn't need to 'resolve' the artifacts: just keep the configuration->PublishArtifactSet mapping for the life of this instance.
    // This mapping is the source of truth for the artifacts for this component
    private void resolveArtifacts() {
        if (!artifactsResolved) {
            for (String configuration : configurationArtifacts.keySet()) {
                PublishArtifactSet artifacts = configurationArtifacts.get(configuration);
                for (PublishArtifact artifact : artifacts) {
                    IvyArtifactName ivyArtifact = DefaultIvyArtifactName.forPublishArtifact(artifact, id.getName());
                    addArtifact(configuration, ivyArtifact, artifact.getFile());
                }
            }
            artifactsResolved = true;
        }
    }

    void addArtifact(String configuration, IvyArtifactName artifactName, File file) {
        DefaultLocalArtifactMetaData candidateArtifact = new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), artifactName, file);
        DefaultLocalArtifactMetaData artifact = artifactsById.get(candidateArtifact.getId());
        if (artifact == null) {
            artifactsById.put(candidateArtifact.id, candidateArtifact);
            artifact = candidateArtifact;
        }
        artifact.addConfiguration(configuration);
    }

    public void addConfiguration(String name, boolean visible, String description, String[] superConfigs, boolean transitive) {
        Configuration conf = new Configuration(name, visible ? Configuration.Visibility.PUBLIC : Configuration.Visibility.PRIVATE, description, superConfigs, transitive, null);
        allIvyConfigurations.put(name, conf);
    }

    public void addDependency(DependencyMetaData dependency) {
        dependencies.add(dependency);
    }

    public void addExcludeRule(ExcludeRule excludeRule) {
        allExcludeRules.add(excludeRule);
    }

    public ComponentResolveMetaData toResolveMetaData() {
        return new LocalComponentResolveMetaData();
    }

    public BuildableIvyModulePublishMetaData toPublishMetaData() {
        DefaultIvyModulePublishMetaData publishMetaData = new DefaultIvyModulePublishMetaData(id, status);
        for (Configuration configuration : allIvyConfigurations.values()) {
            publishMetaData.addConfiguration(configuration);
        }
        for (ExcludeRule excludeRule : allExcludeRules) {
            publishMetaData.addExcludeRule(excludeRule);
        }
        for (DependencyMetaData dependency : dependencies) {
            publishMetaData.addDependency(dependency);
        }
        for (String configuration : configurationArtifacts.keySet()) {
            PublishArtifactSet publishArtifacts = configurationArtifacts.get(configuration);
            for (PublishArtifact publishArtifact : publishArtifacts) {
                publishMetaData.addArtifact(configuration, publishArtifact);
            }
        }
        return publishMetaData;
    }

    private static class DefaultLocalArtifactMetaData implements LocalArtifactMetaData {
        private final DefaultLocalArtifactIdentifier id;
        private final File file;
        private final Set<String> configurations = Sets.newHashSet();

        private DefaultLocalArtifactMetaData(ComponentIdentifier componentIdentifier, String displayName, IvyArtifactName artifact, File file) {
            this.id = new DefaultLocalArtifactIdentifier(componentIdentifier, displayName, artifact, file);
            this.file = file;
        }

        void addConfiguration(String configuration) {
            configurations.add(configuration);
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public IvyArtifactName getName() {
            return id.getName();
        }

        public ComponentIdentifier getComponentId() {
            return id.getComponentIdentifier();
        }

        public ComponentArtifactIdentifier getId() {
            return id;
        }

        public File getFile() {
            return file;
        }

        public Set<String> getConfigurations() {
            return configurations;
        }
    }

    private class LocalComponentResolveMetaData implements ComponentResolveMetaData {
        private ModuleVersionIdentifier moduleVersionIdentifier;
        private Map<String, DefaultConfigurationMetaData> configurations = new HashMap<String, DefaultConfigurationMetaData>();

        public LocalComponentResolveMetaData() {
            this.moduleVersionIdentifier = id;
        }

        @Override
        public String toString() {
            return componentIdentifier.getDisplayName();
        }

        public ModuleVersionIdentifier getId() {
            return moduleVersionIdentifier;
        }

        public ModuleSource getSource() {
            return null;
        }

        public ComponentResolveMetaData withSource(ModuleSource source) {
            throw new UnsupportedOperationException();
        }

        public boolean isGenerated() {
            return false;
        }

        public boolean isChanging() {
            return false;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getStatusScheme() {
            return DEFAULT_STATUS_SCHEME;
        }

        public ComponentIdentifier getComponentId() {
            return componentIdentifier;
        }

        public List<DependencyMetaData> getDependencies() {
            return dependencies;
        }

        @Override
        public Set<String> getConfigurationNames() {
            return allIvyConfigurations.keySet();
        }

        public DefaultConfigurationMetaData getConfiguration(final String name) {
            DefaultConfigurationMetaData configuration = configurations.get(name);
            if (configuration == null) {
                configuration = populateConfigurationFromDescriptor(name);
            }
            return configuration;
        }

        private DefaultConfigurationMetaData populateConfigurationFromDescriptor(String name) {
            Configuration descriptorConfiguration = allIvyConfigurations.get(name);
            if (descriptorConfiguration == null) {
                return null;
            }
            Set<String> hierarchy = new LinkedHashSet<String>();
            hierarchy.add(name);
            for (String parent : descriptorConfiguration.getExtends()) {
                hierarchy.addAll(getConfiguration(parent).hierarchy);
            }
            DefaultConfigurationMetaData configuration = new DefaultConfigurationMetaData(name, descriptorConfiguration, hierarchy);
            configurations.put(name, configuration);
            return configuration;
        }

        private class DefaultConfigurationMetaData implements ConfigurationMetaData {
            private final String name;
            private final Configuration descriptor;
            private final Set<String> hierarchy;
            private List<DependencyMetaData> configurationDependencies;
            private Set<ComponentArtifactMetaData> configurationArtifacts;
            private LinkedHashSet<ExcludeRule> configurationExcludeRules;

            private DefaultConfigurationMetaData(String name, Configuration descriptor, Set<String> hierarchy) {
                this.name = name;
                this.descriptor = descriptor;
                this.hierarchy = hierarchy;
            }

            @Override
            public String toString() {
                return String.format("%s:%s", componentIdentifier.getDisplayName(), name);
            }

            public ComponentResolveMetaData getComponent() {
                return LocalComponentResolveMetaData.this;
            }

            public String getName() {
                return name;
            }

            public Set<String> getHierarchy() {
                return hierarchy;
            }

            public boolean isTransitive() {
                return descriptor.isTransitive();
            }

            public boolean isPublic() {
                return descriptor.getVisibility() == Configuration.Visibility.PUBLIC;
            }

            public List<DependencyMetaData> getDependencies() {
                if (configurationDependencies == null) {
                    configurationDependencies = new ArrayList<DependencyMetaData>();
                    for (DependencyMetaData dependency : dependencies) {
                        if (include(dependency)) {
                            configurationDependencies.add(dependency);
                        }
                    }
                }
                return configurationDependencies;
            }

            private boolean include(DependencyMetaData dependency) {
                String[] moduleConfigurations = dependency.getModuleConfigurations();
                for (int i = 0; i < moduleConfigurations.length; i++) {
                    String moduleConfiguration = moduleConfigurations[i];
                    if (moduleConfiguration.equals("%") || hierarchy.contains(moduleConfiguration)) {
                        return true;
                    }
                    if (moduleConfiguration.equals("*")) {
                        boolean include = true;
                        for (int j = i + 1; j < moduleConfigurations.length && moduleConfigurations[j].startsWith("!"); j++) {
                            if (moduleConfigurations[j].substring(1).equals(getName())) {
                                include = false;
                                break;
                            }
                        }
                        if (include) {
                            return true;
                        }
                    }
                }
                return false;
            }

            public Set<ExcludeRule> getExcludeRules() {
                if (configurationExcludeRules == null) {
                    configurationExcludeRules = new LinkedHashSet<ExcludeRule>();
                    for (ExcludeRule excludeRule : allExcludeRules) {
                        for (String config : excludeRule.getConfigurations()) {
                            if (hierarchy.contains(config)) {
                                configurationExcludeRules.add(excludeRule);
                                break;
                            }
                        }
                    }
                }
                return configurationExcludeRules;
            }

            public Set<ComponentArtifactMetaData> getArtifacts() {
                resolveArtifacts();
                if (configurationArtifacts == null) {
                    configurationArtifacts = Sets.newLinkedHashSet();
                    for (String configName : getHierarchy()) {
                        for (DefaultLocalArtifactMetaData artifactMetaData : artifactsById.values()) {
                            if (artifactMetaData.configurations.contains(configName)) {
                                configurationArtifacts.add(artifactMetaData);
                            }
                        }
                    }
                }
                return configurationArtifacts;
            }

            public ComponentArtifactMetaData artifact(IvyArtifactName ivyArtifactName) {
                for (ComponentArtifactMetaData candidate : getArtifacts()) {
                    if (candidate.getName().equals(ivyArtifactName)) {
                        return candidate;
                    }
                }

                return new DefaultLocalArtifactMetaData(componentIdentifier, id.toString(), ivyArtifactName, null);
            }

        }
    }
}
