plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

application {
    // ingest / chat / eval 모드는 args 로 분기 (Main.kt)
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    // 표준 입력으로 질문을 받기 위해 콘솔 연결
    standardInput = System.`in`

    // 프로젝트 루트의 .env 를 읽어 환경변수로 주입 (키를 코드/깃에 넣지 않기 위함).
    // 형식: KEY=VALUE 한 줄씩, # 주석 허용. 셸 export 도 그대로 우선한다.
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return@forEach
            val (key, value) = line.split("=", limit = 2)
            environment(key.trim(), value.trim().trim('"'))
        }
    }
}
