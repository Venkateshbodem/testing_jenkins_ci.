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

package org.gradle.integtests.tooling.r47;

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;

public class ResultHandlerCollector implements ResultHandler<String> {
    private String result = null;
    private GradleConnectionException failure = null;

    @Override
    public void onComplete(String result) {
        this.result = result;
    }

    @Override
    public void onFailure(GradleConnectionException failure) {
        this.failure = failure;
    }

    public String getResult() {
        return result;
    }

    public GradleConnectionException getFailure() {
        return failure;
    }
}
