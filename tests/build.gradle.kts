// tests — 整合測試
dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))
    implementation(project(":data"))
    implementation(project(":persistence"))
    implementation(project(":gamelogic"))
    implementation(project(":network"))
    implementation(project(":app"))

    testImplementation(libs.exposed.core)
    testImplementation(libs.exposed.jdbc)
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.koin.test)
}

// Surface useful stack traces in CI when a regression fails.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
