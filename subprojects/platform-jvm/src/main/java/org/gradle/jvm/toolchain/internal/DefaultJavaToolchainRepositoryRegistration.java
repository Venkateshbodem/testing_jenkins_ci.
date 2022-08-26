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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaToolchainRepository;

public class DefaultJavaToolchainRepositoryRegistration implements JavaToolchainRepositoryRegistrationInternal {

    private final String name;
    private final Provider<? extends JavaToolchainRepository> provider;

    public DefaultJavaToolchainRepositoryRegistration(String name, Provider<? extends JavaToolchainRepository> provider) {
        this.name = name;
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return provider.get().getClass().getSimpleName(); //TODO (#21082): ok to have a String containing a JavaToolchainRepository implementation class name here?
    }

    public Provider<? extends JavaToolchainRepository> getProvider() {
        return provider;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(name: " + getName() + ", type: " + getType() + ")";
    }
}
