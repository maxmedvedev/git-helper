import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.Duration

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val versionValue = "0.0.2"

group = "max.medvedev"
version = versionValue

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.1")
        testFramework(TestFrameworkType.Platform)

        // Git support from the IDE:
        bundledPlugin("Git4Idea")
    }
}

// End-to-end tests (`src/e2eTest`, docs/e2e-tests.md): IDE Starter starts a real IDE with the plugin zip as a separate
// process, and the tests drive it with the UI Driver. Their classpath has only Starter and the Driver, no platform.
val e2eTest: SourceSet = sourceSets.create("e2eTest")

// The build of the platform above (`intellijIdea("2025.3.1")`): Starter and the Driver are published per platform build
val starterVersion = "253.29346.138"

dependencies {
    // The products (`IdeProductProvider.IU`) are in `ide-starter-squashed` on 253, not in `ide-starter-product-*` yet.
    // Not `testFramework(TestFrameworkType.Starter)`: it excludes from these artifacts everything the IDE bundles (the
    // stdlib, coroutines, JUnit 4), as if the tests ran in the IDE, but Starter runs outside of it.
    for (artifact in listOf(
        "ide-starter-squashed",
        "ide-starter-junit5",
        "ide-starter-driver",
    )) {
        e2eTest.implementationConfigurationName("com.jetbrains.intellij.tools:$artifact:$starterVersion")
    }
    for (artifact in listOf("driver-client", "driver-sdk", "driver-model")) {
        e2eTest.implementationConfigurationName("com.jetbrains.intellij.driver:$artifact:$starterVersion")
    }
    // Starter's DI container, which a split-mode run rebinds (`IdeRunMode.apply`); the POMs have it at runtime only
    e2eTest.implementationConfigurationName("org.kodein.di:kodein-di-jvm:7.26.1")
    // The POMs have the stdlib at runtime only
    e2eTest.implementationConfigurationName(kotlin("stdlib"))
    e2eTest.implementationConfigurationName(platform("org.junit:junit-bom:5.13.4"))
    e2eTest.implementationConfigurationName("org.junit.jupiter:junit-jupiter")
    e2eTest.runtimeOnlyConfigurationName("org.junit.platform:junit-platform-launcher")
}

// Starter uses the IntelliJ fork of coroutines (`org.jetbrains.intellij.deps.kotlinx`); other libraries bring the
// original with the same classes
configurations.named(e2eTest.runtimeClasspathConfigurationName) {
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

// Not `intellijPlatformTesting.testIdeUi`: its task gets the IntelliJ Platform classpath, and these tests need only the
// plugin zip, which `TestIdeUiTask` passes as `path.to.build.plugin` as below.
tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests in a real IDE with the plugin (IDE Starter + UI Driver)."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    useJUnitPlatform {
        // Starter's JUnit 4 support (the vintage engine) runs no tests here
        excludeEngines("junit-vintage")
    }
    // Each test starts an IDE, and the first run downloads it
    timeout = Duration.ofHours(1)
    outputs.upToDateWhen { false }

    val pluginZip = tasks.buildPlugin.flatMap { it.archiveFile }
    inputs.file(pluginZip)
    // A local copy: the configuration cache can't serialize a reference to the script's own `starterVersion`
    val ideBuild = starterVersion
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dpath.to.build.plugin=${pluginZip.get().asFile.absolutePath}",
            "-De2e.ide.build=$ideBuild",
        )
    })
    // Test output (the IDE's stdout, Starter's steps) on the console
    testLogging {
        showStandardStreams = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251.1"
        }

        version = versionValue
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
