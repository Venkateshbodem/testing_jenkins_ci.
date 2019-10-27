/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal

import org.gradle.util.TextUtil

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ManifestWriterTest extends Specification {
    // The values here are printed with ManifestWriter#entry
    StringWriter sw = new StringWriter()
    ManifestWriter mw = new ManifestWriter(sw)
    // The values here are printed with ManifestWriter#write(char[],...)
    StringWriter sw2 = new StringWriter()
    ManifestWriter mw2 = new ManifestWriter(sw2)
    // The values here are printed with ManifestWriter#write(char)
    StringWriter sw3 = new StringWriter()
    ManifestWriter mw3 = new ManifestWriter(sw3)

    String expected
    String printAsEntry
    String printAsCharArray
    String printAsChars

    private int longestLine(String s) {
        s.lines()
            .mapToInt { it.getBytes(StandardCharsets.UTF_8).length }
            .max()
            .getAsInt()
    }

    private def writeEntryAsCharArray(String name, String value) {
        def chars = (name + ": " + value).toCharArray()
        mw2.write(chars)
        mw2.newLine()
        for (char c : chars) {
            mw3.write(c)
        }
        mw3.newLine()
    }

    private def print(String value) {
        // The intention here is that each value will be "cut" at different byte position
        // Thus multi-byte characters will be examined at different splits
        mw.entry("1", value)
        mw.entry("22", value)
        mw.entry("333", value)
        mw.entry("4444", value)
        mw.entry("55555", value)
        mw.entry("666666", value)
        mw.flush()
        printAsEntry = sw.toString()
        writeEntryAsCharArray("1", value)
        writeEntryAsCharArray("22", value)
        writeEntryAsCharArray("333", value)
        writeEntryAsCharArray("4444", value)
        writeEntryAsCharArray("55555", value)
        writeEntryAsCharArray("666666", value)
        mw2.flush()
        printAsCharArray = sw2.toString()
        mw3.flush()
        printAsChars = sw3.toString()
    }

    def "German: ä, 1 char, 1 codepoint, 2 bytes"() {
        when:
        print("äääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääääää")
        expected =
            TextUtil.convertLineSeparators(
                '''|1: ääääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | ääääääääääääääääääääääääääääää
                   |22: ääääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | ääääääääääääääääääääääääääääää
                   |333: äääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääää
                   |4444: äääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääää
                   |55555: ääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | ääääääääääääääääääääääääääääääää
                   |666666: ääääääääääääääääääääääääääääääää
                   | äääääääääääääääääääääääääääääääääää
                   | ääääääääääääääääääääääääääääääää
                   |'''.stripMargin(), "\r\n")

        then:
        longestLine(printAsEntry) == 72

        and:
        printAsEntry == expected
        printAsCharArray == expected
        printAsChars == expected
    }

    def "Cyrillic: й, 1 char, 1 codepoint, 2 bytes"() {
        when:
        print("йййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййййй")
        expected =
            TextUtil.convertLineSeparators(
                '''|1: йййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййй
                   |22: йййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййй
                   |333: ййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | йййййййййййййййййййййййййййййй
                   |4444: ййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | йййййййййййййййййййййййййййййй
                   |55555: йййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййй
                   |666666: йййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййййййй
                   | ййййййййййййййййййййййййййййййй
                   |'''.stripMargin(), "\r\n")

        then:
        longestLine(printAsEntry) == 72

        and:
        printAsEntry == expected
        printAsCharArray == expected
        printAsChars == expected
    }

    def "Kanji: 丈, 1 char, 1 codepoint, 3 bytes"() {
        when:
        print("丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈")
        expected =
            TextUtil.convertLineSeparators(
                '''|1: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈
                   |22: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈
                   |333: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈
                   |4444: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈
                   |55555: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈
                   |666666: 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈丈
                   | 丈丈丈丈丈丈丈丈丈丈
                   |'''.stripMargin(), "\r\n")

        then:
        longestLine(printAsEntry) == 72

        and:
        printAsEntry == expected
        printAsCharArray == expected
        printAsChars == expected
    }

    def "Emoji: 😃, 2 chars, 1 codepoint, 4 bytes"() {
        when:
        print("😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃")
        expected =
            TextUtil.convertLineSeparators(
                '''|1: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |22: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |333: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |4444: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |55555: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |666666: 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   | 😃😃😃😃😃😃😃😃😃😃😃😃😃😃😃
                   |'''.stripMargin(), "\r\n")

        then:
        longestLine(printAsEntry) == 72

        and:
        printAsEntry == expected
        printAsCharArray == expected
        printAsChars == expected
    }

    def "Grapheme cluster: न + ि == नि, 2 chars, 2 codepoints, 6 bytes"() {
        when:
        print("निनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनिनि")
        expected =
            TextUtil.convertLineSeparators(
                '''|1: निनिनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनि
                   |22: निनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनि
                   |333: निनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनि
                   |4444: निनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनि
                   |55555: निनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनि
                   |666666: निनिनिनिनिनिनिनिनिनिन
                   | िनिनिनिनिनिनिनिनिनिनिनि
                   | निनिनिनिनिनिनिनि
                   |'''.stripMargin(), "\r\n")

        then:
        longestLine(printAsEntry) == 72

        and:
        // Splitting the grapheme to multiple lines is OK, as each bit is still a valid UTF-8 sequence
        printAsEntry == expected
        printAsCharArray == expected
        printAsChars == expected
    }
}
