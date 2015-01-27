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

package org.gradle.test.fixtures.server.s3

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.stub.HttpStub
import org.gradle.test.fixtures.server.stub.StubRequest
import org.mortbay.jetty.handler.AbstractHandler

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class S3StubServer extends HttpServer implements RepositoryServer {

    public static final String BUCKET_NAME = "tests3bucket"
    TestDirectoryProvider testDirectoryProvider

    S3StubServer(TestDirectoryProvider testDirectoryProvider) {
        super()
        this.testDirectoryProvider = testDirectoryProvider;
    }

    def expect(HttpStub httpStub) {
        add(httpStub, stubAction(httpStub))
    }

    HttpServer.ActionSupport stubAction(HttpStub httpStub) {
        new HttpServer.ActionSupport("Generic stub handler") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                httpStub.response?.headers?.each {
                    response.addHeader(it.key, it.value)
                }
                response.setStatus(httpStub.response.status)
                if (httpStub.response?.body) {
                    response.outputStream.bytes = httpStub.response.body
                }
            }
        }
    }

    private void add(HttpStub httpStub, HttpServer.ActionSupport action) {
        HttpExpectOne expectation = new HttpExpectOne(action, [httpStub.request.method], httpStub.request.path)
        expectations << expectation
        addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (requestMatches(httpStub, request)) {
                    assertRequest(httpStub, request)
                    if (expectation.run) {
                        return
                    }
                    expectation.run = true
                    action.handle(request, response)
                    request.handled = true
                }
            }
        })
    }

    void assertRequest(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        assert path.startsWith('/')
        assert path == request.pathInfo
        assert stubRequest.method == request.method
        if (stubRequest.body) {
            assert stubRequest.body == request.getInputStream().bytes
        }
        assert stubRequest.params.every {
            request.getParameterMap()[it.key] == it.value
        }
    }

    boolean requestMatches(HttpStub httpStub, HttpServletRequest request) {
        StubRequest stubRequest = httpStub.request
        String path = stubRequest.path
        assert path.startsWith('/')
        path == request.pathInfo && stubRequest.method == request.method
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo() {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible, String dirPattern, String ivyFilePattern, String artifactFilePattern) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME/ivy"), "/ivy", BUCKET_NAME, m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    @Override
    RemoteIvyRepository getRemoteIvyRepo(String contextPath) {
        new IvyS3Repository(this, testDirectoryProvider.testDirectory.file("$BUCKET_NAME$contextPath"), "$contextPath", BUCKET_NAME)
    }

    @Override
    String getValidCredentials() {
        return """
        credentials(AwsCredentials) {
            accessKey "someKey"
            secretKey "someSecret"
        }"""
    }
}
