// app — 進入點 + Koin DI + Handlers
plugins {
    application
}

application {
    mainClass.set("tw.tsm.app.MainKt")
}

// run task 的工作目錄設為專案根目錄，讓相對路徑（gamedata/ 等）正確解析
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
