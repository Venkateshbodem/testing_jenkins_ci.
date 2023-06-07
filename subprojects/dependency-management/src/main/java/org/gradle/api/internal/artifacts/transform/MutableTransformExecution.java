/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.io.File;
import java.util.Map;

class MutableTransformExecution extends AbstractTransformExecution {
    public MutableTransformExecution(
        Transform transform,
        File inputArtifact,
        TransformDependencies dependencies,
        TransformStepSubject subject,

        TransformExecutionListener transformExecutionListener,
        BuildOperationExecutor buildOperationExecutor,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        TransformWorkspaceServices workspaceServices
    ) {
        super(
            transform, inputArtifact, dependencies, subject,
            transformExecutionListener, buildOperationExecutor, fileCollectionFactory, inputFingerprinter, workspaceServices
        );
    }

    @Override
    public Identity identify(Map<String, ValueSnapshot> identityInputs, Map<String, CurrentFileCollectionFingerprint> identityFileInputs) {
        return new MutableTransformWorkspaceIdentity(
            inputArtifact.getAbsolutePath(),
            identityInputs.get(AbstractTransformExecution.SECONDARY_INPUTS_HASH_PROPERTY_NAME),
            identityFileInputs.get(AbstractTransformExecution.DEPENDENCIES_PROPERTY_NAME).getHash()
        );
    }
}
