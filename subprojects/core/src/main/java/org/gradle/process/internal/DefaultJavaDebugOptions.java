/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.internal.model.ExecObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.internal.JvmDebugOptions.DefaultJvmDebugOptions;

import javax.inject.Inject;
import java.util.Objects;

public class DefaultJavaDebugOptions implements JavaDebugOptions {
    private final Property<Boolean> enabled;
    private final Property<String> host;
    private final Property<Integer> port;
    private final Property<Boolean> server;
    private final Property<Boolean> suspend;

    @Inject
    public DefaultJavaDebugOptions(ExecObjectFactory execObjectFactory) {
        DefaultJvmDebugOptions defaultValues = new DefaultJvmDebugOptions();
        this.enabled = execObjectFactory.property(Boolean.class).convention(defaultValues.isEnabled());
        this.host = execObjectFactory.property(String.class).convention(defaultValues.getHost());
        this.port = execObjectFactory.property(Integer.class).convention(defaultValues.getPort());
        this.server = execObjectFactory.property(Boolean.class).convention(defaultValues.isServer());
        this.suspend = execObjectFactory.property(Boolean.class).convention(defaultValues.isSuspend());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEnabled().get(), getHost().getOrNull(), getPort().get(), getServer().get(), getSuspend().get());
    }

    @SuppressWarnings("BoxedPrimitiveEquality")
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJavaDebugOptions that = (DefaultJavaDebugOptions) o;
        return enabled.get() == that.enabled.get()
            && Objects.equals(host.getOrNull(), that.host.getOrNull())
            && port.get().equals(that.port.get())
            && server.get() == that.server.get()
            && suspend.get() == that.suspend.get();
    }

    @Override
    public Property<Boolean> getEnabled() {
        return enabled;
    }

    @Override
    @Optional
    public Property<String> getHost() {
        return host;
    }

    @Override
    public Property<Integer> getPort() {
        return port;
    }

    @Override
    public Property<Boolean> getServer() {
        return server;
    }

    @Override
    public Property<Boolean> getSuspend() {
        return suspend;
    }
}
