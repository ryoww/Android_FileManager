import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// アプリのコア（ドメインとユースケース）。Android / Compose / SMBJ / DataStore に依存しない。
// 依存方向は app -> core のみ。ここに Android 型を持ち込まないこと。
// jvmToolchain は使わない（ツールチェーンの自動取得が無い環境で解決に失敗する）。
// :app と同じく実行中の JDK でコンパイルし、ターゲットだけ 17 に合わせる。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("app.cash.turbine:turbine:1.2.0")
}
