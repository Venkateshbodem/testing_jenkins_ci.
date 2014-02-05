/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration

import org.gradle.api.internal.initialization.ScriptCompileScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.groovy.scripts.*
import org.gradle.groovy.scripts.internal.StatementExtractingScriptTransformer
import org.gradle.internal.Factory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.logging.LoggingManagerInternal
import org.gradle.plugin.internal.PluginResolverFactory
import spock.lang.Specification

public class DefaultScriptPluginFactoryTest extends Specification {

    def scriptCompilerFactory = Mock(ScriptCompilerFactory)
    def importsReader = Mock(ImportsReader)
    def scriptCompiler = Mock(ScriptCompiler)
    def scriptSource = Mock(ScriptSource)
    def scriptRunner = Mock(ScriptRunner)
    def script = Mock(BasicScript)
    def instantiator = Mock(Instantiator)
    def parentScope = Mock(ScriptCompileScope)
    def baseClassLoader = new URLClassLoader(new URL[0])
    def scriptClassLoader = new URLClassLoader(new URL[0])
    def scriptHandlerFactory = Mock(ScriptHandlerFactory)
    def pluginResolverFactory = Mock(PluginResolverFactory)
    def scriptHandler = Mock(ScriptHandlerInternal)
    def classPathScriptRunner = Mock(ScriptRunner)
    def classPathScript = Mock(BasicScript)
    def loggingManagerFactory = Mock(Factory) as Factory<LoggingManagerInternal>
    def pluginParentClassLoader = Mock(ClassLoader)
    def sourceWithImports = Mock(ScriptSource)
    def loggingManager = Mock(LoggingManagerInternal)

    def factory = new DefaultScriptPluginFactory(scriptCompilerFactory, importsReader, loggingManagerFactory, instantiator, scriptHandlerFactory, pluginResolverFactory, pluginParentClassLoader)

    void configuresATargetObjectUsingScript() {
        when:
        final Object target = new Object()

        1 * loggingManagerFactory.create() >> loggingManager
        1 * importsReader.withImports(scriptSource) >> sourceWithImports
        1 * scriptCompilerFactory.createCompiler(sourceWithImports) >> scriptCompiler
        1 * scriptHandler.getBaseCompilationClassLoader() >> baseClassLoader
        1 * scriptCompiler.setClassloader(baseClassLoader)
        1 * scriptCompiler.setTransformer(_ as StatementExtractingScriptTransformer)
        1 * scriptCompiler.compile(DefaultScript) >> classPathScriptRunner
        1 * classPathScriptRunner.getScript() >> classPathScript
        1 * classPathScript.init(target, _ as ServiceRegistry)
        1 * classPathScriptRunner.run()
        1 * scriptHandler.updateClassPath()
        1 * scriptHandler.getScriptCompileClassLoader() >> scriptClassLoader
        1 * scriptCompiler.setClassloader(scriptClassLoader)
        1 * scriptCompiler.setTransformer(!null)
        1 * scriptCompiler.compile(DefaultScript) >> scriptRunner
        1 * scriptRunner.getScript() >> script
        1 * script.init(target, _ as ServiceRegistry)
        1 * scriptRunner.run()

        then:
        ScriptPlugin configurer = factory.create(scriptSource, scriptHandler, "buildscript", DefaultScript)
        configurer.apply(target)
    }

    void configuresAScriptAwareObjectUsingScript() {
        when:
        def target = Mock(ScriptAware)

        1 * loggingManagerFactory.create() >> loggingManager
        1 * importsReader.withImports(scriptSource) >> sourceWithImports
        1 * scriptCompilerFactory.createCompiler(sourceWithImports) >> scriptCompiler
        1 * scriptHandler.getBaseCompilationClassLoader() >> baseClassLoader
        1 * scriptCompiler.setClassloader(baseClassLoader)
        1 * scriptCompiler.setTransformer(_ as StatementExtractingScriptTransformer)
        1 * scriptCompiler.compile(DefaultScript) >> classPathScriptRunner
        1 * classPathScriptRunner.getScript() >> classPathScript
        1 * classPathScript.init(target, _ as ServiceRegistry)
        1 * classPathScriptRunner.run()
        1 * scriptHandler.updateClassPath()
        1 * scriptHandler.getScriptCompileClassLoader() >> scriptClassLoader
        1 * scriptCompiler.setClassloader(scriptClassLoader)
        1 * scriptCompiler.setTransformer(!null)
        1 * scriptCompiler.compile(DefaultScript) >> scriptRunner
        1 * scriptRunner.getScript() >> script
        1 * script.init(target, _ as ServiceRegistry)
        1 * scriptRunner.run()

        then:
        ScriptPlugin configurer = factory.create(scriptSource, scriptHandler, "buildscript", DefaultScript)
        configurer.apply(target)
    }
}