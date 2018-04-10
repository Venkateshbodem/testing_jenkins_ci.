/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

public class CompositeResolvedArtifactSet implements ResolvedArtifactSet {
    private final List<ResolvedArtifactSet> sets;

    private CompositeResolvedArtifactSet(List<ResolvedArtifactSet> sets) {
        this.sets = sets;
    }

    public static List<ResolvedArtifactSet> unpack(ResolvedArtifactSet initialArtifactSet) {
        List<ResolvedArtifactSet> artifactSets = new ArrayList<ResolvedArtifactSet>();
        Deque<ResolvedArtifactSet> queue = new ArrayDeque<ResolvedArtifactSet>();
        queue.add(initialArtifactSet);

        while (!queue.isEmpty()) {
            ResolvedArtifactSet artifactSet = queue.remove();
            if (artifactSet instanceof CompositeResolvedArtifactSet) {
                queue.addAll(((CompositeResolvedArtifactSet) artifactSet).getSets());
            } else {
                artifactSets.add(artifactSet);
            }
        }
        return artifactSets;
    }

    public static ResolvedArtifactSet of(Collection<? extends ResolvedArtifactSet> sets) {
        List<ResolvedArtifactSet> filtered = new ArrayList<ResolvedArtifactSet>(sets.size());
        for (ResolvedArtifactSet set : sets) {
            if (set != ResolvedArtifactSet.EMPTY) {
                filtered.add(set);
            }
        }
        if (filtered.isEmpty()) {
            return EMPTY;
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return new CompositeResolvedArtifactSet(filtered);
    }

    @Override
    public Completion startVisit(BuildOperationQueue<RunnableBuildOperation> actions, AsyncArtifactListener listener) {
        List<Completion> results = new ArrayList<Completion>(sets.size());
        for (ResolvedArtifactSet set : sets) {
            results.add(set.startVisit(actions, listener));
        }
        return new CompositeResult(results);
    }

    @Override
    public void collectBuildDependencies(BuildDependenciesVisitor visitor) {
        for (ResolvedArtifactSet set : sets) {
            set.collectBuildDependencies(visitor);
        }
    }

    public List<ResolvedArtifactSet> getSets() {
        return sets;
    }

    private static class CompositeResult implements Completion {
        private final List<Completion> results;

        CompositeResult(List<Completion> results) {
            this.results = results;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            for (Completion result : results) {
                result.visit(visitor);
            }
        }
    }
}
