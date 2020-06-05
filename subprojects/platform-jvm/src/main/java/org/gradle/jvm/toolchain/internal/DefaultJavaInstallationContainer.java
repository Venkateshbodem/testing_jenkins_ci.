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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.internal.AbstractNamedDomainObjectContainer;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.jvm.toolchain.JavaInstallationContainer;
import org.gradle.jvm.toolchain.LogicalJavaInstallation;

import javax.inject.Inject;

public class DefaultJavaInstallationContainer extends AbstractNamedDomainObjectContainer<LogicalJavaInstallation> implements JavaInstallationContainer {

    @Inject
    public DefaultJavaInstallationContainer(Instantiator instantiator, JavaInstallationRegistryShared registry) {
        // TODO: decorated needed for settings?
        super(LogicalJavaInstallation.class, instantiator, CollectionCallbackActionDecorator.NOOP);
        all(registry::add);
        createCurrentInstallation();
    }

    private void createCurrentInstallation() {
        create("current").setPath(Jvm.current().getJavaHome().getAbsolutePath());
    }

    @Override
    protected LogicalJavaInstallation doCreate(String name) {
        return new DefaultLogicalJavaInstallation(name);
    }

    @Override
    public NamedDomainObjectProvider<LogicalJavaInstallation> register(String name, Action<? super LogicalJavaInstallation> configurationAction) throws InvalidUserDataException {
        return super.register(name, configurationAction);
    }
}
