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
package org.gradle.plugins.signing

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

import org.gradle.util.ConfigureUtil

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType

class SignOperation {

    SignatureType type
    Signatory signatory
    private final SigningSettings settings
    final private List<Signature> signatures = []
    
    SignOperation(SigningSettings settings) {
        this.settings = settings
    }
    
    String getDisplayName() {
        "SignOperation"
    }
    
    String toString() {
        getDisplayName()
    }
    
    SignOperation sign(PublishArtifact... toSign) {
        toSign.each {
            addSignature(it, it.file, it.classifier, it.buildDependencies)
        }
        this
    }
    
    SignOperation sign(File... toSign) {
        sign(null, *toSign)
    }
    
    SignOperation sign(String classifier, File... toSign) {
        toSign.each {
            addSignature(it, it, classifier)
        }
        this
    }
    
    Signature addSignature(Object source, File toSign, String classifier, Object[] dependsOn) {
        def type = getType()
        def file = type.fileFor(toSign)

        def artifact = new DefaultPublishArtifact(
            file.name,
            "Signature ($type.extension)",
            type.combinedExtension(toSign),
            classifier,
            null, // no specific date, use now
            file,
            dependsOn == null ? [] : dependsOn
        )
        
        def signature = new Signature(source, toSign, type, artifact)
        signatures << signature
        signature
    }
    
    SignOperation execute() {
        for (signature in signatures) {
            signature.type.sign(getSignatory(), signature.signed)
        }
        this
    }
    
    Signature getSingleSignature() {
        if (signatures.size() == 0) {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no signatures.")
        } else if (signatures.size() == 1) {
            signatures.first()
        } else {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no ${signature.size()} signatures.")
        }
    }
    
    FileCollection getSigned() {
        new SimpleFileCollection(*signatures*.signed)
    }
    
    FileCollection getFiles() {
        new SimpleFileCollection(*signatures*.file)
    }
    
    PublishArtifact[] getArtifacts() {
        signatures*.artifact as PublishArtifact[]
    }

    PublishArtifact getSingleArtifact() {
        getSingleSignature().artifact
    }
    
    void type(SignatureType type) {
        this.type = type
    }
    
    SignatureType getType() {
        type ?: settings.type
    }
    
    Signatory getSignatory() {
        signatory ?: settings.signatory
    }
    
    SignOperation signatory(Signatory signatory) {
        this.signatory = signatory
        this
    }
    
    SignOperation configure(Closure closure) {
        ConfigureUtil.configure(closure, this)
        execute()
    }
}
