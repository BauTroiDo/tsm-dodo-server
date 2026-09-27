// gamelogic — Hệ thống game cốt lõi (phụ thuộc vào domain + protocol)
dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))  // BattleManager cần PacketWriter/BattleRoleSerializer

    implementation(libs.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.bcrypt)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.mockk)
}