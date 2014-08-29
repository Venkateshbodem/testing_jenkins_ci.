/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.Platform;
import org.gradle.nativeplatform.platform.internal.PlatformInternal;
import org.gradle.nativeplatform.toolchain.ToolChain;
import org.gradle.util.TreeVisitor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultToolChainRegistry extends DefaultPolymorphicDomainObjectContainer<ToolChain> implements ToolChainRegistryInternal {
    private final Map<String, Class<? extends ToolChain>> registeredDefaults = new LinkedHashMap<String, Class<? extends ToolChain>>();
    private final List<ToolChainInternal> searchOrder = new ArrayList<ToolChainInternal>();

    public DefaultToolChainRegistry(Instantiator instantiator) {
        super(ToolChain.class, instantiator);
        whenObjectAdded(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.add((ToolChainInternal) toolChain);
            }
        });
        whenObjectRemoved(new Action<ToolChain>() {
            public void execute(ToolChain toolChain) {
                searchOrder.remove(toolChain);
            }
        });
    }

    @Override
    protected void handleAttemptToAddItemWithNonUniqueName(ToolChain toolChain) {
        throw new InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()));
    }

    public void registerDefaultToolChain(String name, Class<? extends ToolChain> type) {
        registeredDefaults.put(name, type);
    }

    public void addDefaultToolChains() {
        for (String name : registeredDefaults.keySet()) {
            create(name, registeredDefaults.get(name));
        }
    }

    public ToolChainInternal getForPlatform(PlatformInternal targetPlatform) {
        for (ToolChainInternal toolChain : searchOrder) {
            if (toolChain.select(targetPlatform).isAvailable()) {
                return toolChain;
            }
        }

        // No tool chains can build for this platform. Assemble a description of why
        Map<String, PlatformToolProvider> candidates = new LinkedHashMap<String, PlatformToolProvider>();
        for (ToolChainInternal toolChain : searchOrder) {
            candidates.put(toolChain.getDisplayName(), toolChain.select(targetPlatform));
        }

        return new UnavailableToolChain(new UnavailableToolChainDescription(targetPlatform, candidates));
    }

    private static class UnavailableToolChainDescription implements ToolSearchResult {
        private final Platform targetPlatform;
        private final Map<String, PlatformToolProvider> candidates;

        private UnavailableToolChainDescription(Platform targetPlatform, Map<String, PlatformToolProvider> candidates) {
            this.targetPlatform = targetPlatform;
            this.candidates = candidates;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(String.format("No tool chain is available to build for platform '%s'", targetPlatform.getName()));
            visitor.startChildren();
            for (Map.Entry<String, PlatformToolProvider> entry : candidates.entrySet()) {
                visitor.node(entry.getKey());
                visitor.startChildren();
                entry.getValue().explain(visitor);
                visitor.endChildren();
            }
            if (candidates.isEmpty()) {
                visitor.node("No tool chain plugin applied.");
            }
            visitor.endChildren();
        }
    }

    private static class UnavailableToolChain implements ToolChainInternal {
        private final ToolSearchResult failure;

        UnavailableToolChain(ToolSearchResult failure) {
            this.failure = failure;
        }

        public String getDisplayName() {
            return getName();
        }

        public String getName() {
            return "unavailable";
        }

        public PlatformToolProvider select(PlatformInternal targetPlatform) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure);
        }

        public String getOutputType() {
            return "unavailable";
        }
    }
}