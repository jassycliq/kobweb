plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("com.varabyte.kobweb.internal.publish")
}

group = "com.varabyte.kobweb"
version = libs.versions.kobweb.libs.get()

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(compose.web.core)
                implementation(compose.runtime)

                api(project(":frontend:kobweb-compose"))
            }
        }
    }
}

kobwebPublication {
    artifactId.set("kobweb-silk-icons-fa")
    description.set("A collection of Kobweb Silk components that directly wrap Font Awesome icons")
}

enum class IconCategory {
    SOLID,
    REGULAR,
    BRAND,
}

val regenerateIconsTask = tasks.register("regenerateIcons") {
    val srcFile = layout.projectDirectory.file("fa-icon-list.txt")
    val dstFile =
        layout.projectDirectory.file("src/jsMain/kotlin/com/varabyte/kobweb/silk/components/icons/fa/FaIcons.kt")

    inputs.files(srcFile, layout.projectDirectory.file("build.gradle.kts"))
    outputs.file(dstFile)

    // {SOLID=[ad, address-book, address-card, ...], REGULAR=[address-book, address-card, angry, ...], ... }
    val iconRawNames = srcFile.asFile
        .readLines().asSequence()
        .filter { line -> !line.startsWith("#") }
        .map { line ->
            // Convert icon name to function name, e.g.
            // align-left -> FaAlignLeft
            line.split("=", limit = 2).let { parts ->
                val category = when (parts[0]) {
                    "fas" -> IconCategory.SOLID
                    "far" -> IconCategory.REGULAR
                    "fab" -> IconCategory.BRAND
                    else -> throw GradleException("Unexpected category string: ${parts[0]}")
                }
                val names = parts[1]

                category to names.split(",")
            }
        }
        .toMap()

    // For each icon name, figure out what categories they are in. This will affect the function signature we generate.
    // {ad=[SOLID], address-book=[SOLID, REGULAR], address-card=[SOLID, REGULAR], ...
    val iconCategories = mutableMapOf<String, MutableSet<IconCategory>>()
    iconRawNames.forEach { entry ->
        val category = entry.key
        entry.value.forEach { rawName ->
            iconCategories.computeIfAbsent(rawName, { mutableSetOf() }).add(category)
        }
    }

    // Sanity check results
    iconCategories
        .filterNot { entry ->
            val categories = entry.value
            categories.size == 1 ||
                (categories.size == 2 && categories.contains(IconCategory.SOLID) && categories.contains(IconCategory.REGULAR))
        }
        .let { invalidGroupings ->
            if (invalidGroupings.isNotEmpty()) {
                throw GradleException("Found unexpected groupings: $invalidGroupings")
            }
        }

    // Generate four types of functions: solid only, regular only, solid or regular, and brand
    val iconMethodEntries = iconCategories
        .map { entry ->
            val rawName = entry.key
            // Convert e.g. "align-items" to "FaAlignItems"
            val methodName = "Fa${rawName.split("-").joinToString("") { it.capitalize() }}"
            val categories = entry.value

            when {
                categories.size == 2 -> {
                    "@Composable fun $methodName(modifier: Modifier = Modifier, style: IconStyle = IconStyle.OUTLINE) = FaIcon(\"$rawName\", modifier, style.category)"
                }
                categories.contains(IconCategory.SOLID) -> {
                    "@Composable fun $methodName(modifier: Modifier = Modifier) = FaIcon(\"$rawName\", modifier, IconCategory.SOLID)"
                }
                categories.contains(IconCategory.REGULAR) -> {
                    "@Composable fun $methodName(modifier: Modifier = Modifier) = FaIcon(\"$rawName\", modifier, IconCategory.REGULAR)"
                }
                categories.contains(IconCategory.BRAND) -> {
                    "@Composable fun $methodName(modifier: Modifier = Modifier) = FaIcon(\"$rawName\", modifier, IconCategory.BRAND)"
                }
                else -> GradleException("Unhandled icon entry: $entry")
            }
        }

    val iconsCode = """
@file:Suppress("unused", "SpellCheckingInspection")

// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
// THIS FILE IS AUTOGENERATED.
//
// Do not edit this file by hand. Instead, update `fa-icon-list.txt` in the module root and run the Gradle
// task "regenerateIcons"
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

package com.varabyte.kobweb.silk.components.icons.fa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.asAttributesBuilder
import com.varabyte.kobweb.compose.ui.graphics.Color
import com.varabyte.kobweb.compose.ui.graphics.toCssColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.dom.Div

enum class IconCategory(internal val className: String) {
    REGULAR("far"),
    SOLID("fas"),
    BRAND("fab");
}

enum class IconStyle(internal val category: IconCategory) {
    FILLED(IconCategory.SOLID),
    OUTLINE(IconCategory.REGULAR);
}

@Composable
fun FaIcon(
    name: String,
    modifier: Modifier,
    style: IconCategory = IconCategory.REGULAR,
) {
    Div(
        attrs = modifier.asAttributesBuilder {
            classes(style.className, "fa-${'$'}name")
        }
    )
}

${iconMethodEntries.joinToString("\n")}
    """.trimIndent()

    println(dstFile.asFile.writeText(iconsCode))
}

tasks.named("compileKotlinJs") {
    dependsOn(regenerateIconsTask)
}

tasks.named("sourcesJar") {
    dependsOn(regenerateIconsTask)
}

tasks.named("jsSourcesJar") {
    dependsOn(regenerateIconsTask)
}