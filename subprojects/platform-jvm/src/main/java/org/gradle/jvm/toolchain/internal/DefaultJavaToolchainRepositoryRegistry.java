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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;
import org.gradle.jvm.toolchain.JavaToolchainRepository;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class DefaultJavaToolchainRepositoryRegistry implements JavaToolchainRepositoryRegistryInternal {

    private static final Action<BuildServiceSpec<BuildServiceParameters.None>> EMPTY_CONFIGURE_ACTION = buildServiceSpec -> {
    };

    private final BuildServiceRegistry sharedServices;

    private final JavaToolchainRepositoryRegistrationListener registrationBroadcaster;

    private final Map<String, JavaToolchainRepositoryRegistrationInternal> registrations = new HashMap<>();

    private final List<JavaToolchainRepositoryRegistrationInternal> requests = new ArrayList<>();

    @Inject
    public DefaultJavaToolchainRepositoryRegistry(Gradle gradle, JavaToolchainRepositoryRegistrationListener registrationBroadcaster) {
        this.sharedServices = gradle.getSharedServices();
        this.registrationBroadcaster = registrationBroadcaster;
    }

    @Override
    public <T extends JavaToolchainRepository> void register(String name, Class<T> implementationType) {
        if (registrations.containsKey(name)) {
            throw new GradleException("Duplicate " + JavaToolchainRepository.class.getSimpleName() + " registration under the name '" + name + "'");
        }

        Provider<T> provider = sharedServices.registerIfAbsent(name, implementationType, EMPTY_CONFIGURE_ACTION);
        JavaToolchainRepositoryRegistrationInternal registration = new DefaultJavaToolchainRepositoryRegistration(name, provider);
        registrations.put(name, registration);

        registrationBroadcaster.onRegister(registration);
    }

    @Override
    public void request(String registrationName) {
        JavaToolchainRepositoryRegistrationInternal registration = registrations.get(registrationName);
        if (registration == null) {
            throw new GradleException("Unknown Java Toolchain registry: " + registrationName);
        }
        request(registration);
    }

    @Override
    public void request(JavaToolchainRepositoryRegistration registration) {
        requests.add((JavaToolchainRepositoryRegistrationInternal) registration);
    }

    @Override
    public List<JavaToolchainRepository> requestedRepositories() {
        return requests.stream()
                .map(JavaToolchainRepositoryRegistrationInternal::getProvider)
                .map(Provider::get)
                .collect(Collectors.toList());
    }

}
