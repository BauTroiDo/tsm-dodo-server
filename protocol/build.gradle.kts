// protocol — 封包編解碼（小端序、Big5 字串）
dependencies {
    implementation(project(":domain"))
    implementation(libs.netty.all)
    implementation(libs.kotlin.logging)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}
