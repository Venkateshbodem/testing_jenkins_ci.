/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.RuleHelper
import org.gradle.integtests.fixtures.internal.IntegrationTestHint
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.gradle.integtests.fixtures.DaemonGradleExecuter

class ToolingApi implements MethodRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApi)
    File projectDir
    private GradleDistribution dist
    private final List<Closure> connectorConfigurers = []

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        dist = RuleHelper.getField(target, GradleDistribution.class)
        return base
    }

    def withConnector(Closure cl) {
        connectorConfigurers << cl
    }

    def withConnection(Closure cl) {
        GradleConnector connector = connector()
        try {
            withConnectionRaw(connector, cl)
        } catch (UnsupportedVersionException e) {
            throw new IntegrationTestHint(e);
        }
    }

    def maybeFailWithConnection(Closure cl) {
        GradleConnector connector = connector()
        try {
            withConnectionRaw(connector, cl)
        } catch (Throwable e) {
            return e
        }
        return null
    }

    private def withConnectionRaw(GradleConnector connector, Closure cl) {
        ProjectConnection connection = connector.connect()
        try {
            return cl.call(connection)
        } finally {
            connection.close()
        }
    }

    def File getProjectDir() {
        return projectDir ?: dist.testDir
    }

    private def connector() {
        GradleConnector connector = GradleConnector.newConnector()
        connector.useGradleUserHomeDir(new File(dist.userHomeDir.absolutePath))
        connector.forProjectDirectory(new File(getProjectDir().absolutePath))
        connector.searchUpwards(false)
        if (GradleDistributionExecuter.executer == GradleDistributionExecuter.Executer.embedded) {
            LOGGER.info("Using embedded tooling API provider");
            connector.useClasspathDistribution()
            connector.embedded(true)
        } else {
            LOGGER.info("Using daemon tooling API provider");
            connector.useInstallation(new File(dist.gradleHomeDir.absolutePath))
            connector.embedded(false)
            connector.daemonMaxIdleTime(300, TimeUnit.SECONDS)
            DaemonGradleExecuter.registerDaemon(dist.userHomeDir)
        }
        connectorConfigurers.each {
            it.call(connector)
        }
        return connector
    }
}
