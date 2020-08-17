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

package org.gradle.integtests.fixtures.jvm;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uses the Windows registry to find installed Sun/Oracle and AdoptOpenJDK JVMs
 */
public class WindowsJvmInstallationSupplier implements InstallationSupplier {

    private final WindowsRegistry windowsRegistry;
    private final OperatingSystem os;

    public WindowsJvmInstallationSupplier(NativeServices nativeServices, OperatingSystem os) {
        this.windowsRegistry = nativeServices.get(WindowsRegistry.class);
        this.os = os;
    }

    @Override
    public Set<InstallationLocation> get() {
        if (os.isWindows()) {
            return findInstallationsInRegistry();
        }
        return Collections.emptySet();
    }

    private Set<InstallationLocation> findInstallationsInRegistry() {
        List<String> jvms = new ArrayList<>();
        jvms.addAll(findJvms("SOFTWARE\\JavaSoft\\JDK"));
        jvms.addAll(findJvms("SOFTWARE\\JavaSoft\\Java Development Kit"));
        jvms.addAll(findJvms("SOFTWARE\\JavaSoft\\Java Runtime Environment"));
        jvms.addAll(findJvms("SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit"));
        jvms.addAll(findJvms("SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment"));
        jvms.addAll(findAdoptOpenJdk("SOFTWARE\\AdoptOpenJDK\\JDK"));
        return jvms.stream().map(javaHome -> new InstallationLocation(new File(javaHome), "windows registry")).collect(Collectors.toSet());
    }

    private List<String> find(String sdkSubkey, String path, String value) {
        try {
            return getVersions(sdkSubkey).stream()
                .map(version -> getValue(sdkSubkey, path, value, version)).collect(Collectors.toList());
        } catch (MissingRegistryEntryException e) {
            // Ignore
            return Collections.emptyList();
        }
    }

    private List<String> getVersions(String sdkSubkey) {
        return windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey);
    }

    private String getValue(String sdkSubkey, String path, String value, String version) {
        return windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey + '\\' + version + path, value);
    }

    private List<String> findAdoptOpenJdk(String sdkSubkey) {
        return find(sdkSubkey, "\\hotspot\\MSI", "Path");
    }

    private List<String> findJvms(String sdkSubkey) {
        return find(sdkSubkey, "", "JavaHome");
    }

}
