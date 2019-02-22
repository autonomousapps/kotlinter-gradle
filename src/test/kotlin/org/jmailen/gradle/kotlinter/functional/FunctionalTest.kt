package org.jmailen.gradle.kotlinter.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FunctionalTest {

    @get:Rule
    val testProjectDir = TemporaryFolder()

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var sourceDir: File
    private lateinit var sourceFile: File

    @Before
    fun setup() {
        settingsFile = testProjectDir.newFile("settings.gradle")
        buildFile = testProjectDir.newFile("build.gradle")
        sourceDir = testProjectDir.newFolder("src", "main", "kotlin")
        sourceFile = File(sourceDir, "KotlinClass.kt")
    }

    @Test
    fun `successful linting`() {
        settingsFile.writeText("rootProject.name = 'hello-world'")

        buildFile.writeText("""
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.3.20'
                id 'org.jmailen.kotlinter'
            }

            repositories {
                jcenter()
            }
        """.trimIndent())

        sourceFile.writeText("""
            class KotlinClass {
                private fun(){
                    println ("hi")
                }
            }
        """.trimIndent())

        val result: BuildResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("lintKotlinMain", "--stacktrace", "--debug")
                .withPluginClasspath()
                .build()

//        assertTrue(result.output.contains("Hello world!"))
        assertEquals(SUCCESS, result.task(":lintKotlinMain")?.outcome)
    }

    @Ignore
    @Test
    fun `hello world test`() {
        settingsFile.writeText("""
            rootProject.name = 'hello-world'
        """.trimIndent())

        buildFile.writeText("""
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """.trimIndent())

        val result: BuildResult = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments("helloWorld")
                .build()

        assertTrue(result.output.contains("Hello world!"))
        assertEquals(SUCCESS, result.task(":helloWorld")?.outcome)
    }
}