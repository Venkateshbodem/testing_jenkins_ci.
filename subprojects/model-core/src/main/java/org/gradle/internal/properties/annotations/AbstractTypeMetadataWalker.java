/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.api.GradleException;
import org.gradle.api.Named;
import org.gradle.api.provider.Provider;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class AbstractTypeMetadataWalker<T, V extends TypeMetadataWalker.NodeMetadataVisitor<T>> implements TypeMetadataWalker<T, V> {
    private final TypeMetadataStore typeMetadataStore;
    private final Class<? extends Annotation> nestedAnnotation;
    private final Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory;

    private AbstractTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation, Supplier<Map<T, String>> nestedNodeToQualifiedNameMapFactory) {
        this.typeMetadataStore = typeMetadataStore;
        this.nestedAnnotation = nestedAnnotation;
        this.nestedNodeToQualifiedNameMapFactory = nestedNodeToQualifiedNameMapFactory;
    }

    @Override
    public void walk(T root, V visitor) {
        Class<?> nodeType = resolveType(root);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        visitor.visitRoot(typeMetadata, root);
        Map<T, String> nestedNodesOnPath = nestedNodeToQualifiedNameMapFactory.get();
        nestedNodesOnPath.put(root, "<root>");
        walkChildren(root, typeMetadata, null, visitor, nestedNodesOnPath);
    }

    private void walkNested(T node, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, Map<T, String> nestedNodesWalkedOnPath, boolean isElementOfCollection) {
        Class<?> nodeType = resolveType(node);
        TypeMetadata typeMetadata = typeMetadataStore.getTypeMetadata(nodeType);
        if (Provider.class.isAssignableFrom(nodeType)) {
            handleNestedProvider(node, qualifiedName, propertyMetadata, visitor, isElementOfCollection, child -> walkNested(child, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath, isElementOfCollection));
        } else if (Map.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            handleNestedMap(node, qualifiedName, (name, child) -> walkNested(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath, true));
        } else if (Iterable.class.isAssignableFrom(nodeType) && !typeMetadata.hasAnnotatedProperties()) {
            handleNestedIterable(node, qualifiedName, (name, child) -> walkNested(child, getQualifiedName(qualifiedName, name), propertyMetadata, visitor, nestedNodesWalkedOnPath, true));
        } else {
            handleNestedBean(node, typeMetadata, qualifiedName, propertyMetadata, visitor, nestedNodesWalkedOnPath);
        }
    }

    private void handleNestedBean(T node, TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, Map<T, String> nestedNodesOnPath) {
        String firstOccurrenceQualifiedName = nestedNodesOnPath.putIfAbsent(node, qualifiedName);
        if (firstOccurrenceQualifiedName != null) {
            onNestedNodeCycle(firstOccurrenceQualifiedName, qualifiedName);
            return;
        }

        visitor.visitNested(typeMetadata, qualifiedName, propertyMetadata, node);
        walkChildren(node, typeMetadata, qualifiedName, visitor, nestedNodesOnPath);
        nestedNodesOnPath.remove(node);
    }

    private void walkChildren(T node, TypeMetadata typeMetadata, @Nullable String parentQualifiedName, V visitor, Map<T, String> nestedNodesOnPath) {
        typeMetadata.getPropertiesMetadata().forEach(propertyMetadata -> {
            String childQualifiedName = getQualifiedName(parentQualifiedName, propertyMetadata.getPropertyName());
            if (propertyMetadata.getPropertyType() == nestedAnnotation) {
                Optional<T> childOptional = getNestedChild(node, childQualifiedName, propertyMetadata, visitor);
                childOptional.ifPresent(child -> walkNested(child, childQualifiedName, propertyMetadata, visitor, nestedNodesOnPath, false));
            } else {
                visitor.visitLeaf(childQualifiedName, propertyMetadata, () -> getChild(node, propertyMetadata).orElse(null));
            }
        });
    }

    abstract protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName);

    abstract protected void handleNestedProvider(T node, String qualifiedName, PropertyMetadata propertyMetadata, V visitor, boolean isElementOfCollection, Consumer<T> handler);

    abstract protected void handleNestedMap(T node, String qualifiedName, BiConsumer<String, T> handler);

    abstract protected void handleNestedIterable(T node, String qualifiedName, BiConsumer<String, T> handler);

    abstract protected Class<?> resolveType(T type);

    abstract protected Optional<T> getNestedChild(T parent, String childQualifiedName, PropertyMetadata propertyMetadata, V visitor);

    abstract protected Optional<T> getChild(T parent, PropertyMetadata property);

    private static String getQualifiedName(@Nullable String parentPropertyName, String childPropertyName) {
        return parentPropertyName == null
            ? childPropertyName
            : parentPropertyName + "." + childPropertyName;
    }

    static class InstanceTypeMetadataWalker extends AbstractTypeMetadataWalker<Object, InstanceMetadataVisitor> implements InstanceMetadataWalker {
        public InstanceTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
            super(typeMetadataStore, nestedAnnotation, IdentityHashMap::new);
        }

        @Override
        protected Class<?> resolveType(Object value) {
            return value.getClass();
        }

        @Override
        protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
            throw new IllegalStateException(String.format("Cycles between nested beans are not allowed. Cycle detected between: '%s' and '%s'.", firstOccurrenceQualifiedName, secondOccurrenceQualifiedName));
        }

        @Override
        protected void handleNestedProvider(Object node, String qualifiedName, PropertyMetadata propertyMetadata, InstanceMetadataVisitor visitor, boolean isElementOfCollection, Consumer<Object> handler) {
            Optional<Object> value = tryUnpackNested(
                qualifiedName,
                visitor,
                () -> visitMissingNestedProvider(visitor, qualifiedName, propertyMetadata, isElementOfCollection),
                () -> Optional.ofNullable(((Provider<?>) node).getOrNull())
            );
            value.ifPresent(handler);
        }

        private void visitMissingNestedProvider(InstanceMetadataVisitor visitor, String qualifiedName, PropertyMetadata propertyMetadata, boolean isElementOfCollect) {
            if (isElementOfCollect) {
                throw new IllegalStateException(getNullNestedCollectionValueExceptionMessage(null, qualifiedName));
            } else {
                visitMissingNested(visitor, qualifiedName, propertyMetadata);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleNestedMap(Object node, String qualifiedName, BiConsumer<String, Object> handler) {
            ((Map<String, Object>) node).forEach((key, value) -> {
                checkNotNull(key, "Null keys in nested map '%s' are not allowed.", qualifiedName);
                checkNotNullNestedCollectionValue(qualifiedName, key, value);
                handler.accept(key, value);
            });
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleNestedIterable(Object node, String qualifiedName, BiConsumer<String, Object> handler) {
            int counter = 0;
            for (Object o : (Iterable<Object>) node) {
                String prefix = o instanceof Named ? ((Named) o).getName() : "";
                String name = prefix + "$" + counter++;
                checkNotNullNestedCollectionValue(qualifiedName, name, o);
                handler.accept(name, o);
            }
        }

        private void checkNotNullNestedCollectionValue(@Nullable String parentQualifiedName, String name, @Nullable Object value) {
            if (value == null) {
                throw new IllegalStateException(getNullNestedCollectionValueExceptionMessage(parentQualifiedName, name));
            }
        }

        private String getNullNestedCollectionValueExceptionMessage(@Nullable String parentQualifiedName, String name) {
            return String.format("Null or absent is not allowed for the nested property '%s', since it's an element of a collection", getQualifiedName(parentQualifiedName, name));
        }

        @Override
        protected Optional<Object> getNestedChild(Object parent, String childQualifiedName, PropertyMetadata propertyMetadata, InstanceMetadataVisitor visitor) {
            return tryUnpackNested(
                childQualifiedName,
                visitor,
                () -> visitMissingNested(visitor, childQualifiedName, propertyMetadata),
                () -> getChild(parent, propertyMetadata)
            );
        }

        private void visitMissingNested(InstanceMetadataVisitor visitor, String qualifiedName, PropertyMetadata propertyMetadata) {
            visitor.visitMissingNested(qualifiedName, propertyMetadata);
        }

        @Override
        protected Optional<Object> getChild(Object parent, PropertyMetadata property) {
            Method method = property.getGetterMethod();
            try {
                // TODO: Move method.setAccessible(true) to PropertyMetadata
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(parent));
            } catch (InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e.getCause());
            } catch (Exception e) {
                throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), parent), e);
            }
        }

        private Optional<Object> tryUnpackNested(String childQualifiedName, InstanceMetadataVisitor visitor, Runnable onMissingValue, Supplier<Optional<Object>> unpacker) {
            Optional<Object> value;
            try {
                value = unpacker.get();
            } catch (Exception e) {
                visitor.visitUnpackNestedError(childQualifiedName, e);
                return Optional.empty();
            }

            if (!value.isPresent()) {
                onMissingValue.run();
            }
            return value;
        }
    }

    static class StaticTypeMetadataWalker extends AbstractTypeMetadataWalker<TypeToken<?>, StaticMetadataVisitor> implements StaticMetadataWalker {
        public StaticTypeMetadataWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
            super(typeMetadataStore, nestedAnnotation, HashMap::new);
        }

        @Override
        protected Class<?> resolveType(TypeToken<?> type) {
            return type.getRawType();
        }

        @Override
        protected void onNestedNodeCycle(@Nullable String firstOccurrenceQualifiedName, String secondOccurrenceQualifiedName) {
            // For Types walk we don't need to do anything on a cycle
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleNestedProvider(TypeToken<?> node, String qualifiedName, PropertyMetadata propertyMetadata, StaticMetadataVisitor visitor, boolean isElementOfCollection, Consumer<TypeToken<?>> handler) {
            handler.accept(extractNestedType((TypeToken<Provider<?>>) node, Provider.class, 0));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleNestedMap(TypeToken<?> node, String qualifiedName, BiConsumer<String, TypeToken<?>> handler) {
            handler.accept(
                "<key>",
                extractNestedType((TypeToken<Map<?, ?>>) node, Map.class, 1));
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void handleNestedIterable(TypeToken<?> node, String qualifiedName, BiConsumer<String, TypeToken<?>> handler) {
            TypeToken<?> nestedType = extractNestedType((TypeToken<? extends Iterable<?>>) node, Iterable.class, 0);
            handler.accept(determinePropertyName(nestedType), nestedType);
        }

        @Override
        protected Optional<TypeToken<?>> getNestedChild(TypeToken<?> parent, String childQualifiedName, PropertyMetadata propertyMetadata, StaticMetadataVisitor visitor) {
            return getChild(parent, propertyMetadata);
        }

        @Override
        protected Optional<TypeToken<?>> getChild(TypeToken<?> parent, PropertyMetadata property) {
            return Optional.of(TypeToken.of(property.getGetterMethod().getGenericReturnType()));
        }

        private static String determinePropertyName(TypeToken<?> nestedType) {
            return Named.class.isAssignableFrom(nestedType.getRawType())
                ? "<name>"
                : "*";
        }

        private static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedSuperClass, int typeParameterIndex) {
            ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedSuperClass).getType();
            return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
        }
    }
}
