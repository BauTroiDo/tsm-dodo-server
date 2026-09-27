// tests — 整合測試
dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))
    implementation(project(":data"))
    implementation(project(":persistence"))
    implementation(project(":gamelogic"))
    implementation(project(":network"))
    implementation(project(":app"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.koin.test)
}
