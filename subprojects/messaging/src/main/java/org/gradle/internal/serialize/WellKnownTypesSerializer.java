/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.serialize;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import org.gradle.internal.Cast;
import org.gradle.internal.io.ClassLoaderObjectInputStream;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.*;

/**
 * An optimized serializer for a value which type is not known in advance. It will try
 * to use an optimized serializer for a set of known types, and resort on Java serialization
 * if it cannot find one. It also has support for `Set`, `List` and `Map` serialization, in
 * which case it will recursively use itself for serializing values (or map keys). However,
 * for those interfaces, it is *not* guaranteed that the deserialized type will be the same,
 * so one should not rely on this serializer if the type of the deserialized collection matters.
 */
public class WellKnownTypesSerializer<T> extends AbstractSerializer<T> {
    private static final Serializer<?>[] SERIALIZERS = new Serializer[]{
        NULL_SERIALIZER,
        STRING_SERIALIZER,
        BOOLEAN_SERIALIZER,
        BYTE_SERIALIZER,
        SHORT_SERIALIZER,
        INTEGER_SERIALIZER,
        LONG_SERIALIZER,
        FLOAT_SERIALIZER,
        DOUBLE_SERIALIZER,
        FILE_SERIALIZER,
        BYTE_ARRAY_SERIALIZER,
        HASHCODE_SERIALIZER
    };
    private static final Class<?>[] SERIALIZER_INDEX = new Class[]{
        null,
        String.class,
        Boolean.class,
        Byte.class,
        Short.class,
        Integer.class,
        Long.class,
        Float.class,
        Double.class,
        File.class,
        byte[].class,
        HashCode.class
    };

    private int serializerFor(Object obj) {
        Class<?> clazz = obj == null ? null : obj.getClass();
        for (int i = 0; i < SERIALIZER_INDEX.length; i++) {
            if (clazz == SERIALIZER_INDEX[i]) {
                return i;
            }
        }
        if (obj instanceof List) {
            return -2;
        }
        if (obj instanceof Map) {
            return -3;
        }
        if (obj instanceof Enum) {
            return -4;
        }
        if (obj instanceof Set) {
            return -5;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private Serializer<Object> serializerFor(int idx) {
        if (idx < 0) {
            switch (idx) {
                case -1:
                    return null;
                case -2:
                    return (Serializer<Object>) listSerializer;
                case -3:
                    return (Serializer<Object>) mapSerializer;
                case -4:
                    return (Serializer<Object>) enumSerializer;
                case -5:
                    return (Serializer<Object>) setSerializer;
            }
        }
        return Cast.uncheckedCast(SERIALIZERS[idx]);
    }

    private final ClassLoader classLoader;
    private final Serializer<?> listSerializer;
    private final Serializer<?> mapSerializer;
    private final Serializer<?> setSerializer;
    private final Serializer<?> enumSerializer;

    public WellKnownTypesSerializer(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : getClass().getClassLoader();
        Serializer<Object> objectSerializer = Cast.uncheckedCast(this);
        this.listSerializer = new ListSerializer<Object>(objectSerializer);
        this.setSerializer = new SetSerializer<Object>(objectSerializer);
        this.mapSerializer = new MapSerializer<Object, Object>(objectSerializer, objectSerializer);
        this.enumSerializer = new EnumSerializer<Enum>(classLoader);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public T read(Decoder decoder) throws Exception {
        try {
            int idx = decoder.readByte();
            if (idx == -1) {
                return (T) new ClassLoaderObjectInputStream(decoder.getInputStream(), classLoader).readObject();
            }
            return Cast.uncheckedCast(serializerFor(idx).read(decoder));
        } catch (StreamCorruptedException e) {
            return null;
        }
    }

    public void write(Encoder encoder, T value) throws IOException {
        int idx = serializerFor(value);
        encoder.writeByte((byte) idx);
        if (idx == -1) {
            ObjectOutputStream objectStr = new ObjectOutputStream(encoder.getOutputStream());
            objectStr.writeObject(value);
            objectStr.flush();
        } else {
            try {
                serializerFor(idx).write(encoder, value);
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        WellKnownTypesSerializer rhs = (WellKnownTypesSerializer) obj;
        return Objects.equal(classLoader, rhs.classLoader);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), classLoader);
    }

    private static class EnumSerializer<T extends Enum> extends AbstractSerializer<T> {
        private final ClassLoader cl;
        private final Map<String, Class<? extends Enum>> cachedClasses = Maps.newHashMap();

        private EnumSerializer(ClassLoader cl) {
            this.cl = cl;
        }

        @SuppressWarnings("unchecked")
        public T read(Decoder decoder) throws Exception {
            String cn = decoder.readString();
            Class<? extends Enum> en = getEnumClass(cn);
            return (T) en.getEnumConstants()[decoder.readSmallInt()];
        }

        @SuppressWarnings("unchecked")
        private Class<? extends Enum> getEnumClass(String cn) throws ClassNotFoundException {
            Class<? extends Enum> clazz = cachedClasses.get(cn);
            if (clazz == null) {
                clazz = (Class<? extends Enum>) cl.loadClass(cn);
                cachedClasses.put(cn, clazz);
            }
            return clazz;
        }

        public void write(Encoder encoder, T value) throws Exception {
            encoder.writeString(value.getClass().getName());
            encoder.writeSmallInt((byte) value.ordinal());
        }
    }
}
