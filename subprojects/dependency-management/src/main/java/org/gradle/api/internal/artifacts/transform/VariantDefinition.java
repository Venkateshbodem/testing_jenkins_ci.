/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;

/**
 * Defines a variant that is the result of applying a transformation chain to produce a variant with the given attributes.
 */
public interface VariantDefinition {
    /**
     * @return This variant's attributes after transformations are applied.
     */
    ImmutableAttributes getTargetAttributes();

    /**
     * @return The transformation chain which transforms the root variant to this variant.
     */
    Transformation getTransformation();

    /**
     * The single transformation step which transforms the previous variant to this variant.
     */
    TransformationStep getTransformationStep();

    /**
     * @return The previous variant in the transformation chain. If null, this variant uses the producer variant as its root.
     */
    @Nullable
    VariantDefinition getPrevious();
}
