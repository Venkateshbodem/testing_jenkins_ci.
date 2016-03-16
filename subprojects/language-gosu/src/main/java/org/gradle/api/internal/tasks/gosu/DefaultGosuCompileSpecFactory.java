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

package org.gradle.api.internal.tasks.gosu;

import org.gradle.api.internal.tasks.compile.AbstractJavaCompileSpecFactory;
import org.gradle.api.internal.tasks.compile.CommandLineJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.ForkingJavaCompileSpec;
import org.gradle.api.tasks.compile.CompileOptions;

public class DefaultGosuCompileSpecFactory extends AbstractJavaCompileSpecFactory<DefaultGosuCompileSpec> {

    public DefaultGosuCompileSpecFactory(CompileOptions compileOptions) {
        super(compileOptions);
    }

    @Override
    protected DefaultGosuCompileSpec getCommandLineSpec() {
        return new DefaultCommandLineGosuCompileSpec();
    }

    @Override
    protected DefaultGosuCompileSpec getForkingSpec() {
        return new DefaultForkingGosuCompileSpec();
    }

    @Override
    protected DefaultGosuCompileSpec getDefaultSpec() {
        return new DefaultGosuCompileSpec();
    }

    private static class DefaultCommandLineGosuCompileSpec extends DefaultGosuCompileSpec implements CommandLineJavaCompileSpec {
    }

    private static class DefaultForkingGosuCompileSpec extends DefaultGosuCompileSpec implements ForkingJavaCompileSpec {
    }
}
