/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.CustomModel
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.ProjectVersion3
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ConnectionVersion4BackedConsumerConnectionTest extends Specification {
    final ConnectionMetaDataVersion1 metaData = Stub()
    final ConnectionVersion4 target = Mock() {
        getMetaData() >> metaData
    }
    final ConsumerOperationParameters parameters = Stub()
    final ModelMapping modelMapping = Stub()
    final ProtocolToModelAdapter adapter = Mock()

    def "describes capabilities of a 1.0-m3 provider"() {
        given:
        metaData.version >> "1.0-milestone-3"
        def connection = new ConnectionVersion4BackedConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        !details.supportsConfiguringJavaHome()
        !details.supportsConfiguringJvmArguments()
        !details.supportsConfiguringStandardInput()
        !details.supportsGradleProjectModel()
        !details.supportsRunningTasksWhenBuildingModel()

        and:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(IdeaProject)
        !details.isModelSupported(BasicIdeaProject)
        !details.isModelSupported(GradleProject)
        !details.isModelSupported(BuildEnvironment)
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)
    }

    def "describes capabilities of a 1.0-m5 provider"() {
        given:
        metaData.version >> "1.0-milestone-5"
        def connection = new ConnectionVersion4BackedConsumerConnection(target, modelMapping, adapter)
        def details = connection.versionDetails

        expect:
        details.supportsGradleProjectModel()

        and:
        !details.supportsConfiguringJavaHome()
        !details.supportsConfiguringJvmArguments()
        !details.supportsConfiguringStandardInput()
        !details.supportsRunningTasksWhenBuildingModel()

        and:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(BuildEnvironment)
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)
    }

    def "builds model using connection's getModel() method"() {
        ProjectVersion3 protocolModel = Mock()
        GradleProject model = Mock()
        metaData.version >> "1.0-milestone-5"
        def connection = new ConnectionVersion4BackedConsumerConnection(target, modelMapping, adapter)

        when:
        def result = connection.run(GradleProject.class, parameters)

        then:
        result == model

        and:
        _ * modelMapping.getProtocolType(GradleProject.class) >> ProjectVersion3.class
        1 * target.getModel(ProjectVersion3.class, parameters) >> protocolModel
        1 * adapter.adapt(GradleProject.class, protocolModel, _) >> model
        0 * target._
    }

    def "runs build using connection's executeBuild() method"() {
        metaData.version >> "1.0-milestone-5"
        def connection = new ConnectionVersion4BackedConsumerConnection(target, modelMapping, adapter)

        when:
        connection.run(Void.class, parameters)

        then:
        1 * target.executeBuild(parameters, parameters)
        0 * target._
    }
}
