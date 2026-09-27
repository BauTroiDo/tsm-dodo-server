// network — Netty TCP 網路層
dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))

    implementation(libs.netty.all)
    implementation(libs.coroutines.core)
    implementation(libs.kotlin.logging)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}
