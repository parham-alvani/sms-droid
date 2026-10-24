plugins {
    id("sms.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "me.parham1995.sms.data"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Robolectric sandboxes SDK 36 -- this app's minSdk, below which it refuses
// to load the manifest at all -- only on Java 21 or newer. The code is still
// compiled for 17, which AGP requires; only the test JVM is newer.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    // SDK 36's shared-memory setup writes a FileDescriptor's private field.
    jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}

dependencies {
    api(projects.core.sms)

    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    // The backup runs after a message arrives and once a day, in a Hilt-built worker.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // git-over-SSH.
    implementation(libs.jgit)
    implementation(libs.jgit.ssh.apache)
    // sshd carries EdDSA support classes but no implementation; without this
    // Ed25519 is reported unsupported and key generation silently produces a
    // different algorithm.
    implementation(libs.eddsa)
    // Not optional: JGit and sshd call LoggerFactory on their own code paths.
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.slf4j.simple)
}
