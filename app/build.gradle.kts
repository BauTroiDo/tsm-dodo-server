// app — Điểm vào + Koin DI + Handlers
plugins {
    application
}

application {
    mainClass.set("tw.tsm.app.MainKt")
}

// Đặt thư mục làm việc của run task thành thư mục gốc dự án，để đường dẫn tương đối（gamedata/ v.v.）phân giải chính xác
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":protocol"))
    implementation(project(":data"))
    implementation(project(":persistence"))
    implementation(project(":gamelogic"))
    implementation(project(":network"))

    implementation(libs.koin.core)
    implementation(libs.typesafe.config)
    implementation(libs.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
}
