import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val versionValue = "0.0.3"

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
        // Tracks the 263 (2026.3) release branch. `useInstaller = false` is required: the installer
        // feed only lists published releases, so a snapshot has to come from the intellij-repository
        // Maven snapshots that `defaultRepositories()` already registers.
        // Note: the plain `263-EAP-SNAPSHOT` alias still resolves to 263.3889, which predates the
        // commit-details API this plugin needs.
        intellijIdea("263.4732-EAP-CANDIDATE-SNAPSHOT") {
            useInstaller = false
        }
        testFramework(TestFrameworkType.Platform)

        // In 263 the VCS core moved out of the platform into a bundled plugin, so the change browser,
        // the VCS Log and the DVCS classes have to be requested explicitly.
        bundledPlugin("intellij.vcs.plugin")

        // Git support from the IDE:
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 263.4732 rather than plain 263: the commit-details API used by
            // GitShowAllAffectedFromLogAction landed after the 263.3889 EAPs.
            sinceBuild = "263.4732"
        }

        version = versionValue
    }

    pluginVerification {
        ides {
            // 2026.3 is not released yet, so no published IDE falls into the `sinceBuild` range.
            // Verify against the very platform this plugin compiles against.
            current()
        }

        // The commit-details wiring deliberately calls @ApiStatus.Internal platform API — the same
        // API the platform's own ShowAllAffectedFromHistoryAction uses. All of it is confined to
        // showAffected/ShowCommitChanges.kt, whose two functions are candidates for public platform API.
        // Keep those usages reported, but do not let them fail the build.
        // This is the plugin's default set with INTERNAL_API_USAGES dropped.
        failureLevel = listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
        )
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
