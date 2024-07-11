/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.scala.internal;

import com.google.common.collect.Streams;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.internal.JavaToolchain;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Configures scala compile options to include the proper jvm target.
 * Gradle tool is not responsible for understanding if the version of Scala selected is compatible with the toolchain
 * Does not configure if Java Toolchain feature is not in use
 *
 * @since 7.3
 */
public class ScalaCompileOptionsConfigurer {

    private static final int FALLBACK_JVM_TARGET = 8;

    /**
     * Support for these flags in different minor releases of Scala varies,
     * but we need to detect as many variants as possible to avoid overriding the target or release.
     */
    private static final List<String> TARGET_DEFINING_PARAMETERS = Arrays.asList(
        // Scala 2
        "-target", "--target",
        // Scala 2 and 3
        "-release", "--release",
        // Scala 3
        "-java-output-version", "--java-output-version",
        "-Xunchecked-java-output-version", "--Xunchecked-java-output-version",
        "-Xtarget", "--Xtarget"
    );

    private static final VersionNumber PLAIN_TARGET_FORMAT_SINCE_VERSION = VersionNumber.parse("2.13.1");
    private static final VersionNumber RELEASE_REPLACES_TARGET_SINCE_VERSION = VersionNumber.parse("2.13.9");
    private static final VersionNumber TARGET_RENAMED_TO_XTARGET_SINCE_VERSION = VersionNumber.parse("3.0.0");

    public static void configure(ScalaCompileOptions scalaCompileOptions, JavaInstallationMetadata toolchain, Set<File> scalaClasspath) {
        if (toolchain == null) {
            return;
        }

        if (hasTargetDefiningParameter(scalaCompileOptions.getAdditionalParameters())) {
            return;
        }

        // When Scala 3 is used it appears on the classpath together with Scala 2
        Iterable<ScalaJar> scalaJars = ScalaJar.inspect(scalaClasspath, "library"::equals);
        VersionNumber scalaVersion = Streams.stream(scalaJars)
            .map(scalaJar -> VersionNumber.parse(scalaJar.getVersion()))
            .max(Comparator.naturalOrder())
            .orElse(VersionNumber.UNKNOWN);

        if (VersionNumber.UNKNOWN.equals(scalaVersion)) {
            return;
        }

        String targetParameter = determineTargetParameter(scalaVersion, (JavaToolchain) toolchain);
        scalaCompileOptions.getAdditionalParameters().add(targetParameter);
    }

    private static boolean hasTargetDefiningParameter(List<String> additionalParameters) {
        return additionalParameters.stream()
            .anyMatch(s -> TARGET_DEFINING_PARAMETERS.stream().anyMatch(param -> param.equals(s) || s.startsWith(param + ":")));
    }

    /**
     * Computes parameter to specify how Scala should handle Java APIs and produced bytecode version.
     * <p>
     * The exact result depends on the Scala version in use and if the toolchain is user specified or not.
     *
     * @param scalaVersion The detected scala version
     * @param javaToolchain The toolchain used to run compilation
     * @return a Scala compiler parameter
     */
    private static String determineTargetParameter(VersionNumber scalaVersion, JavaToolchain javaToolchain) {
        boolean explicitToolchain = !javaToolchain.isFallbackToolchain();
        int effectiveTarget = !explicitToolchain ? FALLBACK_JVM_TARGET : javaToolchain.getLanguageVersion().asInt();
        if (explicitToolchain && scalaVersion.compareTo(RELEASE_REPLACES_TARGET_SINCE_VERSION) >= 0) {
            return String.format("-release:%s", effectiveTarget);
        } else if (scalaVersion.compareTo(TARGET_RENAMED_TO_XTARGET_SINCE_VERSION) >= 0) {
            return String.format("-Xtarget:%s", effectiveTarget);
        } else if (scalaVersion.compareTo(PLAIN_TARGET_FORMAT_SINCE_VERSION) >= 0) {
            return String.format("-target:%s", effectiveTarget);
        } else {
            return String.format("-target:jvm-1.%s", effectiveTarget);
        }
    }
}
