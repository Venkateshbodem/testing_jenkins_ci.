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
package org.gradle.test.fixtures.ivy

import org.gradle.api.Action
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.file.TestFile

class IvyFileModule extends AbstractModule implements IvyModule {
    final String ivyPattern
    final String artifactPattern
    final TestFile moduleDir
    final String organisation
    final String module
    final String revision
    final boolean m2Compatible
    final List dependencies = []
    final Map<String, Map> configurations = [:]
    final List artifacts = []
    final Map extendsFrom = [:]
    final Map extraAttributes = [:]
    final Map extraInfo = [:]
    String status = "integration"
    boolean noMetaData
    int publishCount = 1
    XmlTransformer transformer = new XmlTransformer()

    IvyFileModule(String ivyPattern, String artifactPattern, TestFile moduleDir, String organisation, String module, String revision, boolean m2Compatible) {
        this.ivyPattern = ivyPattern
        this.artifactPattern = artifactPattern
        this.moduleDir = moduleDir
        this.organisation = organisation
        this.module = module
        this.revision = revision
        this.m2Compatible = m2Compatible
        configurations['runtime'] = [extendsFrom: [], transitive: true, visibility: 'public']
        configurations['default'] = [extendsFrom: ['runtime'], transitive: true, visibility: 'public']
    }

    IvyDescriptor getParsedIvy() {
        return new IvyDescriptor(ivyFile)
    }

    IvyFileModule configuration(Map<String, ?> options = [:], String name) {
        configurations[name] = [extendsFrom: options.extendsFrom ?: [], transitive: options.transitive ?: true, visibility: options.visibility ?: 'public']
        return this
    }

    IvyFileModule withXml(Closure action) {
        transformer.addAction(action);
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of name, type or classifier
     * @return this
     */
    IvyFileModule artifact(Map<String, ?> options = [:]) {
        artifacts << toArtifact(options)
        return this
    }

    IvyFileModule undeclaredArtifact(Map<String, ?> options) {
        artifact(options + [undeclared: true])
        return this
    }

    Map<String, ?> toArtifact(Map<String, ?> options = [:]) {
        return [name: options.name ?: module, type: options.type ?: 'jar',
                ext: options.ext ?: options.type ?: 'jar', classifier: options.classifier ?: null, conf: options.conf ?: '*']
    }

    IvyFileModule dependsOn(String organisation, String module, String revision) {
        dependsOn([organisation: organisation, module: module, revision: revision])
        return this
    }

    IvyFileModule dependsOn(Map<String, ?> attributes) {
        dependencies << attributes
        return this
    }

    IvyFileModule dependsOn(String... modules) {
        modules.each { dependsOn(organisation, it, revision) }
        return this
    }

    IvyFileModule extendsFrom(Map<String, ?> attributes) {
        this.extendsFrom.clear()
        this.extendsFrom.putAll(attributes)
        return this
    }

    IvyFileModule nonTransitive(String config) {
        configurations[config].transitive = false
        return this
    }

    IvyFileModule withStatus(String status) {
        this.status = status
        return this
    }

    IvyFileModule withNoMetaData() {
        noMetaData = true
        return this
    }

    IvyFileModule withExtraAttributes(Map extraAttributes) {
        this.extraAttributes.putAll(extraAttributes)
        return this
    }

    /**
     * Keys in extra info will be prefixed with namespace prefix "my" in this fixture.
     */
    IvyFileModule withExtraInfo(Map extraInfo) {
        this.extraInfo.putAll(extraInfo)
        return this
    }

    String getIvyFilePath() {
        return M2CompatibleIvyPatternHelper.substitute(ivyPattern, organisation, module, revision, m2Compatible)
    }

    String getJarFilePath() {
        return M2CompatibleIvyPatternHelper.substitute(artifactPattern, organisation, module, revision, null, "jar", "jar", m2Compatible)
    }

    TestFile getIvyFile() {
        return moduleDir.file(ivyFilePath)
    }

    TestFile getJarFile() {
        return moduleDir.file(jarFilePath)
    }

    /**
     * Publishes ivy.xml plus all artifacts with different content to previous publication.
     */
    IvyFileModule publishWithChangedContent() {
        publishCount++
        publish()
    }

    String getPublicationDate() {
        return String.format("2010010112%04d", publishCount)
    }

    /**
     * Publishes ivy.xml (if enabled) plus all artifacts
     */
    IvyFileModule publish() {
        moduleDir.createDir()

        if (artifacts.empty) {
            artifact([:])
        }

        artifacts.each { artifact ->
            def artifactFile = file(artifact)
            artifactFile.parentFile.createDir()
            publish(artifactFile) { Writer writer ->
                writer << "${artifactFile.name} : $artifactContent"
            }
        }
        if (noMetaData) {
            return this
        }

        ivyFile.parentFile.createDir()
        publish(ivyFile) { Writer writer ->
            transformer.transform(writer, new Action<Writer>() {
                void execute(Writer ivyFileWriter) {
                    ivyFileWriter << """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0" xmlns:m="http://ant.apache.org/ivy/maven"
${ extraAttributes ? 'xmlns:e="http://ant.apache.org/ivy/extra"' : ''}
${ extraInfo ? 'xmlns:my="http://my.extra.info"' : ''}>
    <!-- ${getArtifactContent()} -->
	<info organisation="${organisation}"
		module="${module}"
		revision="${revision}"
		status="${status}"
        publication="${getPublicationDate()}"
"""
        extraAttributes.each { key, val ->
            ivyFileWriter << """\
        e:$key="$val"
"""
        }
        ivyFileWriter << ">"
        if (extendsFrom) {
            ivyFileWriter << "<extends organisation='${extendsFrom.organisation}' module='${extendsFrom.module}' revision='${extendsFrom.revision}'"
            if (extendsFrom.location) {
                ivyFileWriter << " location='${extendsFrom.location}'"
            }
            ivyFileWriter << "/>"
        }
        extraInfo.each { key , val ->
            ivyFileWriter << """\
        <my:$key>$val</my:$key>
"""
        }
                    ivyFileWriter << """</info>
	<configurations>"""
            configurations.each { name, config ->
                ivyFileWriter << "<conf name='$name'"
                if (config.extendsFrom) {
                    ivyFileWriter << " extends='${config.extendsFrom.join(',')}'"
                }
                if (!config.transitive) {
                    ivyFileWriter << " transitive='false'"
                }
                ivyFileWriter << " visibility='$config.visibility'"
                ivyFileWriter << "/>"
            }
            ivyFileWriter << """</configurations>
	<publications>
"""
            artifacts.each { artifact ->
                if (!artifact.undeclared) {
                    ivyFileWriter << """<artifact name="${artifact.name}" type="${artifact.type}" ext="${artifact.ext}" conf="${artifact.conf}" m:classifier="${artifact.classifier ?: ''}"/>
"""
                }
            }
            ivyFileWriter << """
	</publications>
	<dependencies>
"""
            dependencies.each { dep ->
                def confAttribute = dep.conf == null ? "" : """ conf="${dep.conf}" """
                def revConstraint = dep.revConstraint == null ? "" : """ revConstraint="${dep.revConstraint}" """
                ivyFileWriter << """<dependency org="${dep.organisation}" name="${dep.module}" rev="${dep.revision}" ${confAttribute} ${revConstraint}/>
"""
            }
            ivyFileWriter << """
    </dependencies>
</ivy-module>
        """
                }
            })
        }
        return this
    }

    TestFile file(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def path = M2CompatibleIvyPatternHelper.substitute(artifactPattern, organisation, artifact.name, revision, null, artifact.type, artifact.ext, m2Compatible, artifact.conf, [classifier: artifact.classifier])
        return moduleDir.file(path)
    }

    @Override
    protected onPublish(TestFile file) {
        sha1File(file)
    }

    private String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    /**
     * Asserts that exactly the given artifacts have been published.
     */
    void assertArtifactsPublished(String... names) {
        Set allFileNames = []
        for (name in names) {
            allFileNames.addAll([name, "${name}.sha1"])
        }

        assert moduleDir.list() as Set == allFileNames
        for (name in names) {
            assertChecksumPublishedFor(moduleDir.file(name))
        }
    }

    void assertChecksumPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert new BigInteger(sha1File.text, 16) == getHash(testFile, "SHA1")
    }

    void assertNotPublished() {
        ivyFile.assertDoesNotExist()
    }

    void assertIvyAndJarFilePublished() {
        assertArtifactsPublished(ivyFile.name, jarFile.name)
        assertPublished()
    }

    void assertPublished() {
        assert ivyFile.assertIsFile()
        assert parsedIvy.organisation == organisation
        assert parsedIvy.module == module
        assert parsedIvy.revision == revision
    }

    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.jar", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "jar").hasAttributes("jar", "jar", ["runtime"], null)
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.war", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "war").hasAttributes("war", "war", ["master"])
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${module}-${revision}.ear", "ivy-${revision}.xml")
        parsedIvy.expectArtifact(module, "ear").hasAttributes("ear", "ear", ["master"])
    }
}
