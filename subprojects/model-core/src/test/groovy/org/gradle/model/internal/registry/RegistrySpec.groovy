/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.registry

import org.gradle.api.Nullable
import org.gradle.internal.BiActions
import org.gradle.model.RuleSource
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class RegistrySpec extends Specification {
    protected static class TestNode extends ModelNodeInternal {
        def links = []

        TestNode(String creationPath, Class<?> type) {
            super(toBinder(creationPath, type))
        }

        private static CreatorRuleBinder toBinder(String creationPath, Class<?> type) {
            def creator = ModelCreators.of(ModelPath.path(creationPath), BiActions.doNothing()).descriptor("test").withProjection(new UnmanagedModelProjection(ModelType.of(type))).build()
            def binder = new CreatorRuleBinder(creator, [], [])
            binder
        }

        @Override
        ModelNodeInternal getTarget() {
            return this
        }

        @Override
        Iterable<? extends ModelNodeInternal> getLinks() {
            return links
        }

        @Override
        ModelNodeInternal addLink(ModelNodeInternal node) {
            links << node
            return node
        }

        @Override
        def <T> ModelView<? extends T> asWritable(ModelType<T> type, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies) {
            return null
        }

        @Override
        void addReference(ModelCreator creator) {

        }

        @Override
        void addLink(ModelCreator creator) {

        }

        @Override
        void removeLink(String name) {

        }

        @Override
        def <T> void applyToSelf(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToAllLinks(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToAllLinksTransitive(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToLink(ModelActionRole type, ModelAction<T> action) {

        }

        @Override
        def <T> void applyToLinks(Class<T> type, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToLink(String name, Class<? extends RuleSource> rules) {

        }

        @Override
        void applyToSelf(Class<? extends RuleSource> rules) {

        }

        @Override
        MutableModelNode getLink(String name) {
            return null
        }

        @Override
        int getLinkCount(ModelType<?> type) {
            return 0
        }

        @Override
        Set<String> getLinkNames(ModelType<?> type) {
            return null
        }

        @Override
        Iterable<? extends MutableModelNode> getLinks(ModelType<?> type) {
            return null
        }

        @Override
        boolean hasLink(String name) {
            return false
        }

        @Override
        boolean hasLink(String name, ModelType<?> type) {
            return false
        }

        @Override
        def <T> void setPrivateData(ModelType<? super T> type, T object) {

        }

        @Override
        def <T> T getPrivateData(ModelType<T> type) {
            return null
        }

        @Override
        Object getPrivateData() {
            return null
        }

        @Override
        void setTarget(ModelNode target) {

        }

        @Override
        void ensureUsable() {

        }

        @Override
        def <T> ModelView<? extends T> asReadOnly(ModelType<T> type, @Nullable ModelRuleDescriptor ruleDescriptor) {
            return null
        }

        @Override
        MutableModelNode getParent() {
            return null
        }
    }

    protected static class RuleBinderTestBuilder {

        private ModelRuleDescriptor descriptor
        private ModelReference<?> subjectReference
        private String subjectReferenceBindingPath
        private List<ModelReference<?>> inputReferences = []
        private Map<Integer, String> boundInputReferencePaths = [:]

        void subjectReference(ModelReference<?> reference) {
            subjectReference = reference
        }

        void subjectReference(Class type) {
            subjectReference = ModelReference.of(ModelType.of(type))
        }

        void subjectReference(String path, Class type) {
            subjectReference = ModelReference.of(new ModelPath(path), ModelType.of(type))
        }

        void bindSubjectReference(String path) {
            subjectReferenceBindingPath = path
        }

        void inputReference(ModelReference<?> reference) {
            inputReferences.add(reference)
        }

        void inputReference(Class type) {
            inputReferences.add(ModelReference.of(ModelType.of(type)).inScope(ModelPath.ROOT))
        }

        void inputReference(Class type, ModelNode.State state) {
            inputReferences.add(ModelReference.of(null, ModelType.of(type), state).inScope(ModelPath.ROOT))
        }

        void inputReference(String path, ModelNode.State state) {
            inputReferences.add(ModelReference.of(ModelPath.path(path), ModelType.untyped(), state))
        }

        void inputReference(String path, Class type) {
            inputReferences.add(ModelReference.of(new ModelPath(path), ModelType.of(type)))
        }

        void bindInputReference(int index, String path) {
            boundInputReferencePaths[index] = path
        }

        void descriptor(String descriptor) {
            this.descriptor = new SimpleModelRuleDescriptor(descriptor)
        }

        RuleBinder build() {
            def binder
            if (subjectReference == null) {
                def action = new ProjectionBackedModelCreator(null, descriptor, false, false, [], null, null)
                binder = new CreatorRuleBinder(action, inputReferences, [])
            } else {
                def action = ActionBackedModelAction.of(subjectReference, descriptor, {})
                binder = new MutatorRuleBinder<?>(subjectReference, inputReferences, action, [])
            }
            if (subjectReferenceBindingPath) {
                binder.subjectBinding.boundTo = new TestNode(subjectReferenceBindingPath, Object)
            }
            boundInputReferencePaths.each { index, path ->
                binder.inputBindings[index].boundTo = new TestNode(path, Object)
            }
            return binder
        }
    }
}
