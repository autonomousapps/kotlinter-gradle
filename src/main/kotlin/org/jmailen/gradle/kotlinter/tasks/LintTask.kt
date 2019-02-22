package org.jmailen.gradle.kotlinter.tasks

import com.github.shyiko.ktlint.core.KtLint
import com.github.shyiko.ktlint.core.LintError
import com.github.shyiko.ktlint.core.RuleSet
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.*
import org.gradle.workers.WorkerExecutor
import org.jmailen.gradle.kotlinter.KotlinterExtension
import org.jmailen.gradle.kotlinter.support.reporterFor
import org.jmailen.gradle.kotlinter.support.resolveRuleSets
import org.jmailen.gradle.kotlinter.support.userData
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

@CacheableTask
open class LintTask @Inject constructor(private val workerExecutor: WorkerExecutor) : SourceTask() {

    @OutputFiles
    lateinit var reports: Map<String, File>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource() = super.getSource()

    @Input
    var ignoreFailures = KotlinterExtension.DEFAULT_IGNORE_FAILURES

    @Input
    var indentSize = KotlinterExtension.DEFAULT_INDENT_SIZE

    @Input
    var continuationIndentSize = KotlinterExtension.DEFAULT_CONTINUATION_INDENT_SIZE

    @Internal
    var sourceSetId = ""

    @TaskAction
    fun run() {
        val fileSet = source.files
        workerExecutor.submit(Work::class.java) {

            it.params(
                    name,
                    indentSize,
                    continuationIndentSize,
                    reports,
                    project.projectDir,
                    fileSet,
                    ignoreFailures
            )
        }
    }
}
class Work @Inject constructor(
        private val taskName: String,
        private val indentSize: Int,
        private val continuationIndentSize: Int,
        private val reports: Map<String, File>,
        private val projectDir: File,
        private val source: Set<File>,
        private val ignoreFailures: Boolean
): Runnable {

    override fun run() {
        val logger = LoggerFactory.getLogger(javaClass)

        val fileReporters = reports.map { (reporter, report) ->
            reporterFor(reporter, report)
        }
        fileReporters.forEach { it.beforeAll() }

        var hasErrors = false
        source.forEach { sourceFile ->
            val relativePath = sourceFile.toRelativeString(projectDir)
            fileReporters.forEach { it.before(relativePath) }
            logger.debug("$taskName linting: $relativePath")
            println("$taskName linting: $relativePath")

            val lintFunc = when (sourceFile.extension) {
                "kt" -> this::lintKt
                "kts" -> this::lintKts
                else -> {
                    logger.debug("$taskName ignoring non Kotlin file: $relativePath")
                    println("$taskName ignoring non Kotlin file: $relativePath")
                    null
                }
            }

            lintFunc?.invoke(sourceFile, resolveRuleSets()) { error ->
                fileReporters.forEach { it.onLintError(relativePath, error, false) }

                val errorStr = "$relativePath:${error.line}:${error.col}: ${error.detail}"
                logger.info("Lint error > $errorStr")
                println("Lint error > $errorStr")

                hasErrors = true
            }

            fileReporters.forEach { it.after(relativePath) }
        }
        fileReporters.forEach { it.afterAll() }

        if (hasErrors && !ignoreFailures) {
            throw GradleException("Kotlin source failed lint check.")
        }
        throw RuntimeException("source size = ${source.size}")
    }

    private fun lintKt(file: File, ruleSets: List<RuleSet>, onError: (error: LintError) -> Unit) =
            KtLint.lint(
                    file.readText().also { println("text: $it") },
                    ruleSets,
                    userData(
                            indentSize = indentSize,
                            continuationIndentSize = continuationIndentSize,
                            filePath = file.path
                    ), onError)

    private fun lintKts(file: File, ruleSets: List<RuleSet>, onError: (error: LintError) -> Unit) =
            KtLint.lintScript(
                    file.readText(),
                    ruleSets,
                    userData(
                            indentSize = indentSize,
                            continuationIndentSize = continuationIndentSize,
                            filePath = file.path
                    ), onError)
}
