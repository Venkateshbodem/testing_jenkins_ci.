/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;

import java.util.Collections;
import java.util.Set;

public class RenderableUnresolvedDependencyResult extends AbstractRenderableDependencyResult {
    private final ComponentIdentifier actual;
    private final UnresolvedDependencyResult dependency;

    public RenderableUnresolvedDependencyResult(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;
        this.actual = ComponentIdentifierFactory.getInstance().createIdentifier(dependency.getAttempted());
    }

    @Override
    public boolean isResolvable() {
        return false;
    }

    @Override
    protected ComponentSelector getRequested() {
        return dependency.getRequested();
    }

    @Override
    protected ComponentIdentifier getActual() {
        return actual;
    }

    public Set<RenderableDependency> getChildren() {
        return Collections.emptySet();
    }
}
