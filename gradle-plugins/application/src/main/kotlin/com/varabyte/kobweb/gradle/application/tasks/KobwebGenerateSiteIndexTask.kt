@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.application.tasks

import com.varabyte.kobweb.common.navigation.RoutePrefix
import com.varabyte.kobweb.common.toUnixSeparators
import com.varabyte.kobweb.gradle.application.BuildTarget
import com.varabyte.kobweb.gradle.application.extensions.app
import com.varabyte.kobweb.gradle.application.extensions.index
import com.varabyte.kobweb.gradle.application.templates.createIndexFile
import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import com.varabyte.kobweb.gradle.core.tasks.KobwebModuleTask
import com.varabyte.kobweb.gradle.core.util.hasTransitiveJsDependencyNamed
import com.varabyte.kobweb.gradle.core.util.isDescendantOf
import com.varabyte.kobweb.project.conf.KobwebConf
import kotlinx.html.link
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.util.prefixIfNot
import java.io.File
import javax.inject.Inject

abstract class KobwebGenerateSiteIndexTask @Inject constructor(
    private val kobwebConf: KobwebConf,
    config: KobwebBlock,
    @get:Input val buildTarget: BuildTarget
) : KobwebModuleTask(config, "Generate an index.html file for this Kobweb project") {

    private fun getGenResDir(): File = kobwebBlock.getGenJsResRoot(project)

    @InputFiles
    fun getResourceFiles() = run {
        // Don't let stuff we output force ourselves to run again
        val genIndexFile = getGenIndexFile()
        getResourceFilesJs()
            .filter { it.absolutePath != genIndexFile.absolutePath }
    }

    @OutputFile
    fun getGenIndexFile() = File(File(getGenResDir(), getPublicPath()), "index.html")

    @TaskAction
    fun execute() {
        if (project.hasTransitiveJsDependencyNamed("kobweb-silk-icons-fa")) {
            kobwebBlock.app.index.head.add {
                link {
                    rel = "stylesheet"
                    href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.2.0/css/all.min.css"
                }
            }
        }

        if (project.hasTransitiveJsDependencyNamed("kobweb-silk-icons-mdi")) {
            kobwebBlock.app.index.head.add {
                link {
                    rel = "stylesheet"
                    href = "https://cdnjs.cloudflare.com/ajax/libs/MaterialDesign-Webfont/7.0.96/css/materialdesignicons.min.css"
                }
            }
        }

        getResourceFilesJsWithRoots()
                .mapNotNull{ rootAndFile -> rootAndFile.file.takeIf { !it.isDescendantOf(project.buildDir) && rootAndFile.relativeFile.toUnixSeparators() == "public/index.html"} }
                .singleOrNull()
                ?.let { indexFile ->
                    project.logger.error("$indexFile: You are not supposed to define this file yourself. Kobweb provides its own. Use the kobweb.index { ... } block if you need to modify the generated index file.")
                }

        val routePrefix = RoutePrefix(kobwebConf.site.routePrefix)
         getGenIndexFile().let { indexFile ->
            indexFile.parentFile.mkdirs()
            indexFile.writeText(
                createIndexFile(
                    kobwebConf.site.title,
                    kobwebBlock.app.index.head.get(),
                    // Our script will always exist at the root folder, so be sure to ground it,
                    // e.g. "example.js" -> "/example.js", so the root will be searched even if we're visiting a page in
                    // a subdirectory.
                    routePrefix.prepend(kobwebConf.server.files.dev.script.substringAfterLast("/").prefixIfNot("/")),
                    buildTarget
                )
            )
        }
    }
}