/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.scripting;

import java.util.ArrayList;

/**
 * Details for generating Java-based application start scripts.
 */
public class JavaAppStartScriptGenerationDetails implements ScriptGenerationDetails {
    /**
     * The display name of the application
     */
    private String applicationName;

    /**
     * The environment variable to use to provide additional options to the JVM
     */
    private String optsEnvironmentVar;

    /**
     * The environment variable to use to control exit value (windows only)
     */
    private String exitEnvironmentVar;

    private String mainClassName;

    private Iterable<String> defaultJvmOpts = new ArrayList<String>();
    
    private Boolean escapeMetaCharactersInDefaultJvmOpts = false;

    /**
     * The classpath, relative to the application home directory.
     */
    private Iterable<String> classpath;

    /**
     * The path of the script, relative to the application home directory.
     */
    private String scriptRelPath;

    /**
     * This system property to use to pass the script name to the application. May be null.
     */
    private String appNameSystemProperty;

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getOptsEnvironmentVar() {
        return optsEnvironmentVar;
    }

    public void setOptsEnvironmentVar(String optsEnvironmentVar) {
        this.optsEnvironmentVar = optsEnvironmentVar;
    }

    public String getExitEnvironmentVar() {
        return exitEnvironmentVar;
    }

    public void setExitEnvironmentVar(String exitEnvironmentVar) {
        this.exitEnvironmentVar = exitEnvironmentVar;
    }

    public String getMainClassName() {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName) {
        this.mainClassName = mainClassName;
    }

    public Iterable<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts;
    }

    public Boolean getEscapeMetaCharactersInDefaultJvmOpts() {
      return escapeMetaCharactersInDefaultJvmOpts;
    }

    public void setEscapeMetaCharactersInDefaultJvmOpts(Boolean escapeMetaCharactersInDefaultJvmOpts) {
      this.escapeMetaCharactersInDefaultJvmOpts = escapeMetaCharactersInDefaultJvmOpts;
    }

    public Iterable<String> getClasspath() {
        return classpath;
    }

    public void setClasspath(Iterable<String> classpath) {
        this.classpath = classpath;
    }

    public String getScriptRelPath() {
        return scriptRelPath;
    }

    public void setScriptRelPath(String scriptRelPath) {
        this.scriptRelPath = scriptRelPath;
    }

    public String getAppNameSystemProperty() {
        return appNameSystemProperty;
    }

    public void setAppNameSystemProperty(String appNameSystemProperty) {
        this.appNameSystemProperty = appNameSystemProperty;
    }
}

