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

package org.gradle.composite.internal;

import org.gradle.StartParameter;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.composite.CompositeContextBuildActionRunner;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.composite.CompositeContextBuilder;
import org.gradle.internal.composite.IncludedBuild;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.GradleBuildController;

public class DefaultCompositeContextBuilder implements CompositeContextBuilder {
    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DefaultCompositeContextBuilder.class);
    private final StartParameter buildStartParam;
    private final ServiceRegistry sharedServices;

    public DefaultCompositeContextBuilder(StartParameter startParameter, ServiceRegistry services) {
        this.buildStartParam = startParameter;
        this.sharedServices = services;
    }

    @Override
    public void addToCompositeContext(Iterable<IncludedBuild> includedBuilds, boolean propagateFailures) {
        doAddToCompositeContext(includedBuilds, null, propagateFailures);
    }

    @Override
    public void addToCompositeContext(Iterable<IncludedBuild> includedBuilds, BuildRequestContext requestContext, boolean propagateFailures) {
        doAddToCompositeContext(includedBuilds, requestContext, propagateFailures);
    }

    private void doAddToCompositeContext(Iterable<IncludedBuild> includedBuilds, BuildRequestContext requestContext, boolean propagateFailures) {
        GradleLauncherFactory gradleLauncherFactory = sharedServices.get(GradleLauncherFactory.class);
        CompositeBuildContext context = sharedServices.get(CompositeBuildContext.class);
        CompositeContextBuildActionRunner contextBuilder = new CompositeContextBuildActionRunner(context, propagateFailures);

        for (IncludedBuild build : includedBuilds) {
            StartParameter includedBuildStartParam = buildStartParam.newBuild();
            includedBuildStartParam.setProjectDir(build.getProjectDir());
            includedBuildStartParam.setSearchUpwards(false);
            includedBuildStartParam.setConfigureOnDemand(false);

            LOGGER.lifecycle("[composite-build] Configuring build: " + build.getProjectDir());

            GradleLauncher gradleLauncher = createGradleLauncher(includedBuildStartParam, requestContext, gradleLauncherFactory);

            contextBuilder.run(new GradleBuildController(gradleLauncher));
        }
    }

    private GradleLauncher createGradleLauncher(StartParameter participantStartParam, BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory) {
        if (requestContext == null) {
            return gradleLauncherFactory.nestedInstance(participantStartParam, sharedServices);
        }

        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(participantStartParam, requestContext, sharedServices);
        gradleLauncher.addStandardOutputListener(requestContext.getOutputListener());
        gradleLauncher.addStandardErrorListener(requestContext.getErrorListener());
        return gradleLauncher;
    }
}
