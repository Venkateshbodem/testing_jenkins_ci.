/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;

public class ComponentModelBaseServiceRegistry implements PluginServiceRegistry {

    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    public void registerBuildServices(ServiceRegistration registration){
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class GlobalScopeServices {
        MethodModelRuleExtractor createLanguageTypePluginInspector() {
            return new LanguageTypeModelRuleExtractor();
        }

        MethodModelRuleExtractor createComponentModelPluginInspector(Instantiator instantiator) {
            return new ComponentTypeModelRuleExtractor(instantiator);
        }

        MethodModelRuleExtractor createBinaryTypeModelPluginInspector(Instantiator instantiator) {
            return new BinaryTypeModelRuleExtractor(instantiator);
        }

        MethodModelRuleExtractor createComponentBinariesPluginInspector() {
            return new ComponentBinariesModelRuleExtractor();
        }
        MethodModelRuleExtractor createBinaryTaskPluginInspector() {
            return new BinaryTasksModelRuleExtractor();
        }

    }
}
