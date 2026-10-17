import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    id("fabric-loom") version "1.18.4"
    id("ploceus") version "1.18.1"
    `maven-publish`
}

val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val minecraftVersion: String by project
val loaderVersion: String by project
val featherBuild: String by project
val fabricKotlinVersion: String by project
val pylonVersion: String by project
val oslVersion: String by project

group = mavenGroup
version = "$modVersion+mc$minecraftVersion"

base {
    archivesName = archivesBaseName
}

ploceus {
    setIntermediaryGeneration(2)
}

loom {
    runs {
        named("client") {
            programArgs("--username", "Player", "--accessToken", "0")
            vmArgs(
                "--enable-native-access=ALL-UNNAMED", "-XX:+UseZGC", "-XX:+UseCompactObjectHeaders",
                "-Xms4G", "-Xmx4G", "-XX:+AlwaysPreTouch", "-XX:ConcGCThreads=2", // fixes microstutters for me -_-
            )
        }
    }
}

configurations.configureEach {
    exclude(group = "org.lwjgl.lwjgl")
}

repositories {
    maven("https://maven.cloverclient.com/releases") { name = "Clover Client" }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(ploceus.featherMappings(featherBuild))
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    modImplementation("pl.tomgirl:pylon:$pylonVersion")

    ploceus.dependOsl(oslVersion)

    implementation("org.antlr:antlr4-runtime:4.13.1")
    include("org.antlr:antlr4-runtime:4.13.1")
    implementation("io.github.douira:glsl-transformer:3.0.0-pre3") {
        exclude(group = "org.antlr", module = "antlr4")
    }
    include("io.github.douira:glsl-transformer:3.0.0-pre3")
    implementation("org.anarres:jcpp:1.4.14")
    include("org.anarres:jcpp:1.4.14")
    implementation("org.joml:joml:1.10.8")
    include("org.joml:joml:1.10.8")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
        freeCompilerArgs.addAll("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "100000"))
}

val nativeBuildDir = layout.buildDirectory.dir("native")
val nativeOutputDir = layout.buildDirectory.dir("natives")
val nativeBuildType = providers.gradleProperty("nativeBuildType").orElse("Release")
val nativeJavaHome = javaToolchains.launcherFor(java.toolchain).map { it.metadata.installationPath.asFile.absolutePath }

val configureNative by tasks.registering(Exec::class) {
    group = "native"
    inputs.file("native/CMakeLists.txt")
    inputs.property("buildType", nativeBuildType)
    outputs.file(nativeBuildDir.map { it.file("CMakeCache.txt") })
    executable = "cmake"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-S", file("native").absolutePath,
            "-B", nativeBuildDir.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=${nativeBuildType.get()}",
            "-DASTATINE_JAVA_HOME=${nativeJavaHome.get()}",
            "-DASTATINE_OUTPUT_DIR=${nativeOutputDir.get().asFile.absolutePath}",
        )
    })
}

val buildNative by tasks.registering(Exec::class) {
    group = "native"
    dependsOn(configureNative)
    inputs.dir("native/src")
    inputs.dir("native/shaders")
    inputs.dir("native/cmake")
    outputs.dir(nativeOutputDir)
    executable = "cmake"
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--build", nativeBuildDir.get().asFile.absolutePath, "--config", nativeBuildType.get(), "--parallel")
    })
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
    dependsOn(buildNative)
    from(nativeOutputDir) {
        into("natives")
    }
}

publishing {
    publications {
        create<MavenPublication>("mod") {
            artifactId = archivesBaseName
            from(components["java"])
        }
    }
}
