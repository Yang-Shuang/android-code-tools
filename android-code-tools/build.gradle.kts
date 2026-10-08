plugins {
    id("java")
    id("org.jetbrains.intellij.platform")
}

group = "com.ysh.tools"
version = "1.0.0"


// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.3")

        // Add plugin dependencies for compilation here:
        composeUI()
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.intellij.plugins.markdown")
    }

//    implementation("org.nanohttpd:nanohttpd:2.3.1")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.28294"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

tasks.buildSearchableOptions {
    enabled = false
}