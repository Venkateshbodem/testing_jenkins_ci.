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

package org.gradle.model.internal.core;

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.Factory;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ActionModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

@NotThreadSafe
public class DefaultCollectionBuilder<T> implements CollectionBuilder<T> {
    private final ModelType<T> elementType;
    private final NamedEntityInstantiator<? super T> instantiator;
    private final Collection<? super T> target;
    private final ModelRuleDescriptor sourceDescriptor;
    private final MutableModelNode modelNode;

    public DefaultCollectionBuilder(ModelType<T> elementType, NamedEntityInstantiator<? super T> instantiator, Collection<? super T> target, ModelRuleDescriptor sourceDescriptor,
                                    MutableModelNode modelNode) {
        this.elementType = elementType;
        this.instantiator = instantiator;
        this.target = target;
        this.sourceDescriptor = sourceDescriptor;
        this.modelNode = modelNode;
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Override
    public int size() {
        return modelNode.getLinkCount(elementType);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public <S> CollectionBuilder<S> withType(Class<S> type) {
        if (type.equals(elementType.getConcreteClass())) {
            return uncheckedCast(this);

        }
        if (elementType.getConcreteClass().isAssignableFrom(type)) {
            NamedEntityInstantiator<? super S> castInstantiator = uncheckedCast(instantiator);
            Collection<? super S> castTarget = uncheckedCast(target);
            return new DefaultCollectionBuilder<S>(ModelType.of(type), castInstantiator, castTarget, sourceDescriptor, modelNode);
        }
        return new DefaultCollectionBuilder<S>(ModelType.of(type), new NamedEntityInstantiator<S>() {
            @Override
            public <U extends S> U create(String name, Class<U> type) {
                throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type.getName(), elementType.toString()));
            }
        }, ImmutableList.<S>of(), sourceDescriptor, modelNode);
    }

    @Nullable
    @Override
    public T get(Object name) {
        return get((String) name);
    }

    @Nullable
    @Override
    public T get(String name) {
        // TODO - lock this down
        MutableModelNode link = modelNode.getLink(name);
        if (link == null) {
            return null;
        }
        link.ensureUsable();
        return link.asWritable(elementType, sourceDescriptor, null).getInstance();
    }

    @Override
    public boolean containsKey(Object name) {
        return name instanceof String && modelNode.hasLink((String) name, elementType);
    }

    @Override
    public Set<String> keySet() {
        return modelNode.getLinkNames(elementType);
    }

    @Override
    public boolean containsValue(Object item) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void create(final String name) {
        doCreate(name, elementType);
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        doCreate(name, elementType, configAction);
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type) {
        doCreate(name, ModelType.of(type));
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type, final Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), configAction);
    }

    private <S extends T> void doCreate(final String name, final ModelType<S> type) {
        doCreate(name, type, new Factory<S>() {
            @Override
            public S create() {
                return instantiator.create(name, type.getConcreteClass());
            }
        }, new Action<S>() {
            @Override
            public void execute(S s) {
                target.add(s);
            }
        });
    }

    private <S extends T> void doCreate(final String name, final ModelType<S> type, final Action<? super S> configAction) {
        doCreate(name, type, new Factory<S>() {
            @Override
            public S create() {
                return instantiator.create(name, type.getConcreteClass());
            }
        }, new Action<S>() {
            @Override
            public void execute(S s) {
                configAction.execute(s);
                target.add(s);
            }
        });
    }

    private <S extends T> void doCreate(final String name, ModelType<S> type, Factory<? extends S> factory, Action<? super S> initAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("create(").append(name).append(")");
            }
        }));

        ModelReference<S> subject = ModelReference.of(modelNode.getPath().child(name), type);
        modelNode.addLink(ModelCreators.unmanagedInstance(subject, factory).descriptor(descriptor).build());
        modelNode.applyToLink(ModelActionRole.Initialize, new ActionBackedModelAction<S>(subject, initAction, descriptor));
    }

    @Override
    public void named(final String name, Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("named(").append(name).append(")");
            }
        }));
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.applyToLink(ModelActionRole.Mutate, new ActionBackedModelAction<T>(subject, configAction, descriptor));
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        modelNode.applyToLink(name, ruleSource);
    }

    @Override
    public void all(final Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("all()");
            }
        }));
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, new ActionBackedModelAction<T>(subject, configAction, descriptor));
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("withType()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, new ActionBackedModelAction<S>(subject, configAction, descriptor));
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        doBeforeEach(elementType, configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        doBeforeEach(ModelType.of(type), configAction);
    }

    private <S> void doBeforeEach(ModelType<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("beforeEach()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Defaults, new ActionBackedModelAction<S>(subject, configAction, descriptor));
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        doFinalizeAll(elementType, configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        doFinalizeAll(ModelType.of(type), configAction);
    }

    private <S> void doFinalizeAll(ModelType<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("afterEach()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Finalize, new ActionBackedModelAction<S>(subject, configAction, descriptor));
    }

    public static <I> ModelType<CollectionBuilder<I>> typeOf(ModelType<I> type) {
        return new ModelType.Builder<CollectionBuilder<I>>() {
        }.where(
                new ModelType.Parameter<I>() {
                }, type
        ).build();
    }
}
