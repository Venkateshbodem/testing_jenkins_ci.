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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.MDArtifact;

import java.util.Set;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static java.util.Arrays.asList;

public class BuildableIvyModuleVersionMetaData extends DefaultIvyModuleVersionMetaData {
    private final DefaultModuleDescriptor module;

    public BuildableIvyModuleVersionMetaData(DefaultModuleDescriptor module) {
        super(module);
        this.module = module;
    }

    public void addArtifact(BuildableIvyArtifact newArtifact) {
        if (newArtifact.getConfigurations().isEmpty()) {
            throw new IllegalArgumentException("Artifact should be attached to at least one configuration.");
        }

        MDArtifact unattached = newArtifact.unattachedArtifact(module);
        //Adding the artifact will replace any existing artifact
        //This potentially leads to loss of information - the configurations of the replaced artifact are lost (see GRADLE-123)
        //Hence we attempt to find an existing artifact and merge the information
        Artifact[] allArtifacts = module.getAllArtifacts();
        for (Artifact existing : allArtifacts) {
            if (artifactsEqual(unattached, existing)) {
                if (!(existing instanceof MDArtifact)) {
                    throw new IllegalArgumentException("Cannot update an existing artifact (" + existing + ") in provided module descriptor (" + module + ")"
                            + " because the artifact is not an instance of MDArtifact." + module);
                }
                attachArtifact((MDArtifact) existing, newArtifact.getConfigurations(), module);
                return; //there is only one matching artifact
            }
        }
        attachArtifact(unattached, newArtifact.getConfigurations(), module);
    }

    private boolean artifactsEqual(Artifact a, Artifact b) {
        return new DefaultIvyArtifactName(a).equals(new DefaultIvyArtifactName(b));
    }

    private static void attachArtifact(MDArtifact artifact, Set<String> configurations, DefaultModuleDescriptor target) {
        //The existing artifact configurations will be first
        Set<String> existingConfigurations = newLinkedHashSet(asList(artifact.getConfigurations()));
        for (String c : configurations) {
            if (!existingConfigurations.contains(c)) {
                artifact.addConfiguration(c);
                target.addArtifact(c, artifact);
            }
        }
    }
}
