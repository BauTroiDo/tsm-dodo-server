// persistence — Exposed + MySQL 持久化層
dependencies {
    implementation(project(":domain"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.mysql.connector)
    implementation(libs.bcrypt)
    implementation(libs.coroutines.core)
    implementation(libs.kotlin.logging)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}
