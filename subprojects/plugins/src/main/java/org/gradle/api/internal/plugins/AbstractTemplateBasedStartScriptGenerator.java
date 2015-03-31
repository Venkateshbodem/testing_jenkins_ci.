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

package org.gradle.api.internal.plugins;

import com.google.common.base.Joiner;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.UncheckedException;
import org.gradle.jvm.application.scripts.JavaAppStartScriptGenerationDetails;
import org.gradle.jvm.application.scripts.TemplateBasedScriptGenerator;
import org.gradle.util.TextUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractTemplateBasedStartScriptGenerator implements TemplateBasedScriptGenerator {
    private final TemplateEngine templateEngine;
    private Reader template;

    public AbstractTemplateBasedStartScriptGenerator() {
        this(new GroovySimpleTemplateEngine());
        template = createDefaultTemplate(getDefaultTemplateFilename());
    }

    public AbstractTemplateBasedStartScriptGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            Map<String, String> binding = createBinding(details);
            String scriptContent = generateStartScriptContentFromTemplate(binding);
            writeStartScriptContent(scriptContent, destination);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setTemplate(TextResource template) {
        this.template = template.asReader();
    }

    public Reader getTemplate() {
        return template;
    }

    private String generateStartScriptContentFromTemplate(Map<String, String> binding) {
        String content = templateEngine.generate(getTemplate(), binding);
        return TextUtil.convertLineSeparators(content, getLineSeparator());
    }

    private void writeStartScriptContent(String scriptContent, Writer destination) throws IOException {
        try {
            destination.write(scriptContent);
            destination.flush();
        } finally {
            destination.close();
        }
    }

    private Reader createDefaultTemplate(String filename) {
        InputStream stream = getClass().getResourceAsStream(filename);

        try {
            return new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        } catch(UnsupportedEncodingException e) {
            throw new UncheckedException(e);
        }
    }

    String createJoinedAppHomeRelativePath(String scriptRelPath) {
        int depth = StringUtils.countMatches(scriptRelPath, "/");
        if (depth == 0) {
            return "";
        }

        List<String> appHomeRelativePath = new ArrayList<String>();
        for(int i = 0; i < depth; i++) {
            appHomeRelativePath.add("..");
        }

        Joiner slashJoiner = Joiner.on("/");
        return slashJoiner.join(appHomeRelativePath);
    }

    public static enum ScriptBindingParameter {
        APP_NAME("applicationName"),
        OPTS_ENV_VAR("optsEnvironmentVar"),
        EXIT_ENV_VAR("exitEnvironmentVar"),
        MAIN_CLASSNAME("mainClassName"),
        DEFAULT_JVM_OPTS("defaultJvmOpts"),
        APP_NAME_SYS_PROP("appNameSystemProperty"),
        APP_HOME_REL_PATH("appHomeRelativePath"),
        CLASSPATH("classpath");

        private final String key;

        private ScriptBindingParameter(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    abstract String getDefaultTemplateFilename();
    abstract String getLineSeparator();
    abstract Map<String, String> createBinding(JavaAppStartScriptGenerationDetails details);
}
