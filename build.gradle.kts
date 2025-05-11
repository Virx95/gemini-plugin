// build.gradle.kts
plugins {
    id("java") // Indicates your plugin source code is Java
    id("org.jetbrains.intellij.platform") version "2.5.0" // Use the latest stable version from: https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform
}

group = "eu.technest.geminichatplugin"
version = "0.1.0" // Your plugin's version

repositories {
    mavenCentral() // For general dependencies like OkHttp, Gson

    // The intellijPlatform block handles repositories for IntelliJ Platform artifacts
    intellijPlatform {
        defaultRepositories() // Includes JetBrains releases, snapshots, and Marketplace ZIPs
    }
}

dependencies {
    // Defines the IntelliJ Platform product and version your plugin targets
    intellijPlatform {
        // For IntelliJ IDEA Community Edition version 2023.3
        // The plugin will automatically determine the specific build number (e.g., 233.11799.241)
        intellijIdeaCommunity("2023.3")

        // If your plugin specifically needs APIs from the Java plugin (most do implicitly if targeting IDEA):
        // bundledPlugin("com.intellij.java")

        // If you depended on other plugins, you'd add them here:
        // plugin("org.jetbrains.kotlin:1.9.22") // Example if you needed a specific Kotlin plugin version
    }

    // Your external dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0") // <-- ADD THIS LINE (use the same version as okhttp)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8") // Or the latest version


    // Test dependencies (if you add tests)
    // intellijPlatform {
    //     testFramework(IntelliJPlatformTestFrameworkType.JUnit5) // For JUnit5 based tests
    // }
    // testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    // testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

// Configure Java compilation settings
java {
    sourceCompatibility = JavaVersion.VERSION_11 // Or JavaVersion.VERSION_17 if you prefer
    targetCompatibility = JavaVersion.VERSION_11 // Or JavaVersion.VERSION_17

}

tasks {
    // Configure JUnit 5 for the 'test' task (if you have tests)
    test {
        useJUnitPlatform()
    }

    // The patchPluginXml task customizes the META-INF/plugin.xml file during the build
    patchPluginXml {
        // Automatically set the <version> tag in plugin.xml from project.version
        // project.version is typically an Any, which needs to be converted to a String
        // and then wrapped in a Provider if the property expects it.
        // However, for simple string properties, often a direct assignment works if the types align.
        // Let's try directly assigning the string value.
        version = project.version

        // ... (rest of your patchPluginXml configuration)
        sinceBuild.set("233") // If not automatically derived or you want to be specific
        untilBuild.set("241.*")

        changeNotes.set("""
          
              Initial release of the Gemini AI Chat plugin.<br/>
              - AI Chat panel on the right.<br/>
              - Settings to configure Gemini API Key.
           
        """.trimIndent())
    }

    // Optional: Configure the 'runIde' task (for running/debugging your plugin)
    runIde {
        // Example: Increase max heap size for the sandboxed IDE
        // maxHeapSize.set("2g")
        // jvmArgs.add("-Dsome.property=true")
    }

    // Optional: Configure the 'buildSearchableOptions' task if your plugin has settings
    // that should be searchable in the IDE's settings dialog.
    // buildSearchableOptions {
    //    enabled.set(true) // You'd typically enable this if you have an ApplicationConfigurable or ProjectConfigurable
    // }

    // Optional: Sign your plugin for publishing to JetBrains Marketplace
    // signPlugin {
    //    certificateChainFile.set(file("path/to/cert/chain.crt"))
    //    privateKeyFile.set(file("path/to/private/key.pem"))
    //    password.set(providers.environmentVariable("SIGNING_KEY_PASSWORD")) // Store password securely
    // }
}

//intellijPlatform {
//  // Global IntelliJ Platform settings can also be configured here if not in dependencies
//  // productCode.set("IC") // Redundant if using intellijIdeaCommunity()
//  // version.set("2023.3.4") // Can be more specific than just "2023.3"
//}