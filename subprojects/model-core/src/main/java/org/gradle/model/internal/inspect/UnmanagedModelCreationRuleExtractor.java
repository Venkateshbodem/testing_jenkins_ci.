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

package org.gradle.model.internal.inspect;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.specs.Spec;
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

@ThreadSafe
public class UnmanagedModelCreationRuleExtractor extends AbstractModelCreationRuleExtractor {

    @Override
    public Spec<MethodRuleDefinition<?, ?>> getSpec() {
        final Spec<MethodRuleDefinition<?, ?>> superSpec = super.getSpec();
        return new Spec<MethodRuleDefinition<?, ?>>() {
            public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> element) {
                return superSpec.isSatisfiedBy(element) && !element.getReturnType().equals(ModelType.of(Void.TYPE));
            }
        };
    }

    public <R, S> ModelRuleRegistration registration(MethodRuleDefinition<R, S> ruleDefinition) {
        String modelName = determineModelName(ruleDefinition);

        ModelType<R> returnType = ruleDefinition.getReturnType();
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        ModelRuleDescriptor descriptor = ruleDefinition.getDescriptor();

        BiAction<MutableModelNode, Inputs> transformer = new ModelRuleInvokerBackedTransformer<R>(returnType, ruleDefinition.getRuleInvoker(), descriptor, references);
        ModelCreator modelCreator = ModelCreators.of(ModelReference.of(ModelPath.path(modelName), returnType), transformer)
                .withProjection(new UnmanagedModelProjection<R>(returnType, true, true))
                .descriptor(descriptor)
                .inputs(references)
                .build();

        return new ModelCreatorRegistration(modelCreator);
    }

    public String getDescription() {
        return String.format("%s and returning a model element", super.getDescription());
    }

    private static class ModelRuleInvokerBackedTransformer<T> implements BiAction<MutableModelNode, Inputs> {

        private final ModelType<T> type;
        private final ModelRuleDescriptor descriptor;
        private final ModelRuleInvoker<T> ruleInvoker;
        private final List<ModelReference<?>> inputReferences;

        private ModelRuleInvokerBackedTransformer(ModelType<T> type, ModelRuleInvoker<T> ruleInvoker, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputReferences) {
            this.type = type;
            this.descriptor = descriptor;
            this.ruleInvoker = ruleInvoker;
            this.inputReferences = inputReferences;
        }

        public void execute(MutableModelNode modelNode, Inputs inputs) {
            T instance;
            if (inputs.size() == 0) {
                instance = ruleInvoker.invoke();
            } else {
                Object[] args = new Object[inputs.size()];
                for (int i = 0; i < inputs.size(); i++) {
                    args[i] = inputs.get(i, inputReferences.get(i).getType()).getInstance();
                }

                instance = ruleInvoker.invoke(args);
            }
            if (instance == null) {
                throw new ModelRuleExecutionException(descriptor, "rule returned null");
            }
            modelNode.setPrivateData(type, instance);
        }
    }
}
