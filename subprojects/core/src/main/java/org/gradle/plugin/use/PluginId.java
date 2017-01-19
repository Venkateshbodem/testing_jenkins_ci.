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

package org.gradle.plugin.use;

import org.gradle.api.Nullable;

/**
 * A description of a plugin.
 */
public interface PluginId {
    /**
     * @return true when plugin name has a dot in it.
     */
    boolean isQualified();

    /**
     * Takes an existing plugin, and add a qualifier.
     * @param qualification the qualifier to add.
     * @return a new PluginId when this is not qualified, otherwise this.
     */
    PluginId maybeQualify(String qualification);

    /**
     *
     * @return the substring of the plugin if before the last dot. Null when not qualified.
     */
    @Nullable
    String getNamespace();

    /**
     * Checks if this plugin is inside of a namespace.
     *
     * @param namespace the namespace to check
     * @return true when the namespaces match.
     */
    boolean inNamespace(String namespace);

    /**
     * @return The name of the plugin, without any qualifier.
     */
    String getName();

    /**
     * @return If this is not qualified, then this, otherwise a new instance of PluginId without the qualification
     */
    PluginId getUnqualified();

    /**
     * @return The fully qualified (if applicable) plugin.
     */
    String asString();
}
