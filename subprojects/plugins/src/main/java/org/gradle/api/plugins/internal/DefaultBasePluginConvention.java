/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.internal;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.internal.deprecation.DeprecationLogger;

public class DefaultBasePluginConvention extends BasePluginConvention {

    private BasePluginExtension extension;

    public DefaultBasePluginConvention(BasePluginExtension extension) {
        this.extension = extension;
    }

    @Override
    public DirectoryProperty getDistsDirectory() {
        DeprecationLogger.deprecate("Ujuj getDistsDirectory.").willBeRemovedInGradle8().undocumented().nagUser();
        return extension.getDistsDirectory();
    }

    @Override
    public DirectoryProperty getLibsDirectory() {
        DeprecationLogger.deprecate("Ujuj getLibsDirectory.").willBeRemovedInGradle8().undocumented().nagUser();
        return extension.getLibsDirectory();
    }

    @Override
    public String getDistsDirName() {
        DeprecationLogger.deprecate("Ujuj getDistsDirName.").willBeRemovedInGradle8().undocumented().nagUser();
        return extension.getDistsDirName();
    }

    @Override
    public void setDistsDirName(String distsDirName) {
        DeprecationLogger.deprecate("Ujuj setDistsDirName.").willBeRemovedInGradle8().undocumented().nagUser();
        extension.setDistsDirName(distsDirName);
    }

    @Override
    public String getLibsDirName() {
        DeprecationLogger.deprecate("Ujuj getLibsDirName.").willBeRemovedInGradle8().undocumented().nagUser();
        return extension.getLibsDirName();
    }

    @Override
    public void setLibsDirName(String libsDirName) {
        DeprecationLogger.deprecate("Ujuj setLibsDirName.").willBeRemovedInGradle8().undocumented().nagUser();
        extension.setLibsDirName(libsDirName);
    }

    @Override
    public String getArchivesBaseName() {
        DeprecationLogger.deprecate("Ujuj getArchivesBaseName.").willBeRemovedInGradle8().undocumented().nagUser();
        return extension.getArchivesBaseName();
    }

    @Override
    public void setArchivesBaseName(String archivesBaseName) {
        DeprecationLogger.deprecate("Ujuj setArchivesBaseName.").willBeRemovedInGradle8().undocumented().nagUser();
        extension.setArchivesBaseName(archivesBaseName);
    }
}
