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

package org.gradle.initialization;

import org.gradle.api.Transformer;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.NoArgumentBuildOption;
import org.gradle.internal.buildoption.StringBuildOption;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BuildLayoutParametersBuildOptionFactory implements Factory<List<BuildOption<BuildLayoutParameters>>> {

    private final List<BuildOption<BuildLayoutParameters>> options = new ArrayList<BuildOption<BuildLayoutParameters>>();

    public BuildLayoutParametersBuildOptionFactory() {
        options.add(new GradleUserHomeOption());
        options.add(new ProjectDirOption());
        options.add(new NoSearchUpwardsOption());
    }

    @Override
    public List<BuildOption<BuildLayoutParameters>> create() {
        return options;
    }

    public static class GradleUserHomeOption extends StringBuildOption<BuildLayoutParameters> {
        public GradleUserHomeOption() {
            super("gradle.user.home", CommandLineOptionConfiguration.create("gradle-user-home", "g", "Specifies the gradle user home directory."));
        }

        @Override
        public void applyTo(String value, BuildLayoutParameters settings) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setGradleUserHomeDir(resolver.transform(value));
        }
    }

    public static class ProjectDirOption extends StringBuildOption<BuildLayoutParameters> {
        public ProjectDirOption() {
            super(null, CommandLineOptionConfiguration.create("project-dir", "p", "Specifies the start directory for Gradle. Defaults to current directory."));
        }

        @Override
        public void applyTo(String value, BuildLayoutParameters settings) {
            Transformer<File, String> resolver = new BasicFileResolver(settings.getCurrentDir());
            settings.setProjectDir(resolver.transform(value));
        }
    }

    public static class NoSearchUpwardsOption extends NoArgumentBuildOption<BuildLayoutParameters> {
        public NoSearchUpwardsOption() {
            super(null, CommandLineOptionConfiguration.create("no-search-upward", "u", "Don't search in parent folders for a " + Settings.DEFAULT_SETTINGS_FILE + " file."));
        }

        @Override
        public void applyTo(BuildLayoutParameters settings) {
            settings.setSearchUpwards(false);
        }
    }
}
