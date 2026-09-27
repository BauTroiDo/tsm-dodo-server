// data — .dat 靜態資料載入
dependencies {
    implementation(project(":domain"))

    implementation(libs.serialization.json)
    implementation(libs.kotlin.logging)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
}
