/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.internal.CurrentProcess;
import org.gradle.process.internal.JvmOptions;

import java.util.Set;

public class DaemonJvmOptions extends JvmOptions {

    public static final String SSL_KEYSTORE_KEY = "javax.net.ssl.keyStore";
    public static final String SSL_KEYSTOREPASSWORD_KEY = "javax.net.ssl.keyStorePassword";
    public static final String SSL_KEYSTORETYPE_KEY = "javax.net.ssl.keyStoreType";
    public static final String SSL_TRUSTSTORE_KEY = "javax.net.ssl.trustStore";
    public static final String SSL_TRUSTPASSWORD_KEY = "javax.net.ssl.trustStorePassword";
    public static final String SSL_TRUSTSTORETYPE_KEY = "javax.net.ssl.trustStoreType";

    public static final Set<String> IMMUTABLE_DAEMON_SYSTEM_PROPERTIES = ImmutableSet.of(
        SSL_KEYSTORE_KEY, SSL_KEYSTOREPASSWORD_KEY, SSL_KEYSTORETYPE_KEY, SSL_TRUSTPASSWORD_KEY, SSL_TRUSTSTORE_KEY, SSL_TRUSTSTORETYPE_KEY
    );

    public DaemonJvmOptions(PathToFileResolver resolver) {
        super(resolver);
        systemProperties(new CurrentProcess().getJvmOptions().getImmutableSystemProperties());
    }

    public void systemProperty(String name, Object value) {
        if (IMMUTABLE_DAEMON_SYSTEM_PROPERTIES.contains(name)) {
            immutableSystemProperties.put(name, value);
        } else {
            super.systemProperty(name, value);
        }
    }
}
