@file:Suppress("PropertyName", "HasPlatformType")

plugins {
    id("usvm.kotlin-conventions")
}

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

val `samples-jdk11` by sourceSets.creating {
    java {
        srcDir("src/samples-jdk11/java")
    }
}

val `sample-approximations` by sourceSets.creating {
    java {
        srcDir("src/sample-approximations/java")
    }
}

val `usvm-api` by sourceSets.creating {
    java {
        srcDir("src/usvm-api/java")
    }
}

val approximations by configurations.creating
val approximationsRepo = "com.github.UnitTestBot.java-stdlib-approximations"
val approximationsVersion = "5f137507d6"

dependencies {
    implementation(project(":usvm-core"))
    implementation(project(":usvm-jvm-dataflow"))
    implementation(project(":usvm-jvm-instrumentation"))

    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_approximations)

    implementation(`usvm-api`.output)

    implementation(Libs.ksmt_runner)
    implementation(Libs.ksmt_yices)
    implementation(Libs.ksmt_cvc5)
    implementation(Libs.ksmt_symfpu)

    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)

    testImplementation(samples.output)

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")

    approximations(approximationsRepo, "approximations", approximationsVersion)
    testImplementation(approximationsRepo, "tests", approximationsVersion)
}

val `usvm-apiCompileOnly`: Configuration by configurations.getting
dependencies {
    `usvm-apiCompileOnly`(Libs.jacodb_api_jvm)
}

val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.Samples.lombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.Samples.slf4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.Samples.javaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.Samples.findBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.Samples.jetbrainsAnnotations}")

    // Use usvm-api in samples for makeSymbolic, assume, etc.
    samplesImplementation(`usvm-api`.output)

    testImplementation(project(":usvm-jvm-instrumentation"))
}

val `sample-approximationsCompileOnly`: Configuration by configurations.getting

dependencies {
    `sample-approximationsCompileOnly`(samples.output)
    `sample-approximationsCompileOnly`(`usvm-api`.output)
    `sample-approximationsCompileOnly`(Libs.jacodb_api_jvm)
    `sample-approximationsCompileOnly`(Libs.jacodb_approximations)
}

val `usvm-api-jar` = tasks.register<Jar>("usvm-api-jar") {
    archiveBaseName.set(`usvm-api`.name)
    from(`usvm-api`.output)
}

val testSamples by configurations.creating
val testSamplesWithApproximations by configurations.creating

val compileSamplesJdk11 = tasks.register<JavaCompile>("compileSamplesJdk11") {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()

    source = `samples-jdk11`.java
    classpath = `samples-jdk11`.compileClasspath
    options.sourcepath = `samples-jdk11`.java
    destinationDirectory = `samples-jdk11`.java.destinationDirectory
}

dependencies {
    testSamples(samples.output)
    testSamples(`usvm-api`.output)
    testSamples(files(`samples-jdk11`.java.destinationDirectory))

    testSamplesWithApproximations(samples.output)
    testSamplesWithApproximations(`usvm-api`.output)
    testSamplesWithApproximations(`sample-approximations`.output)
    testSamplesWithApproximations(approximationsRepo, "tests", approximationsVersion)
}

tasks.withType<Test> {
    dependsOn(`usvm-api-jar`)
    dependsOn(compileSamplesJdk11, testSamples, testSamplesWithApproximations)

    val usvmApiJarPath = `usvm-api-jar`.get().outputs.files.singleFile
    val usvmApproximationJarPath = approximations.resolvedConfiguration.files.single()

    environment("usvm.jvm.api.jar.path", usvmApiJarPath.absolutePath)
    environment("usvm.jvm.approximations.jar.path", usvmApproximationJarPath.absolutePath)

    environment("usvm.jvm.test.samples", testSamples.asPath)
    environment("usvm.jvm.test.samples.approximations", testSamplesWithApproximations.asPath)
}


tasks {
    register<Jar>("testJar") {
        group = "jar"
        shouldRunAfter("compileTestKotlin")
        archiveClassifier.set("test")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val contents = sourceSets.getByName("samples").output

        from(contents)
        dependsOn(getByName("compileSamplesJava"), configurations.testCompileClasspath)
        dependsOn(configurations.compileClasspath)
    }
}

tasks.getByName("compileTestKotlin").finalizedBy("testJar")

tasks.withType<Test> {
    environment(
        "usvm-test-jar",
        layout
            .buildDirectory
            .file("libs/usvm-jvm-test.jar")
            .get().asFile.absolutePath
    )
    environment(
        "usvm-jvm-instrumentation-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-1.0.jar")
            .get().asFile.absolutePath
    )
    environment(
        "usvm-jvm-collectors-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-collectors.jar")
            .get().asFile.absolutePath
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
        create<MavenPublication>("maven-api") {
            artifactId = "usvm-jvm-api"
            artifact(`usvm-api-jar`)
        }
    }
}

// --- JVMTI native agent build integration ---
// This uses CMake located under usvm-jvm/agent to produce a shared library.
// It expects JAVA_HOME to be set in the environment (Gradle will forward it).

val agentDirFile = project.file("agent")
val agentBuildDirFile = project.file("agent/build")
val agentLibRelative = "libs/usvm_jvm_agent"

// Platform-specific file name provider
fun providerAgentLibName(): Provider<String> = providers.provider {
    val os = org.gradle.internal.os.OperatingSystem.current()
    when {
        os.isWindows -> "${agentLibRelative}.dll"
        os.isMacOsX -> "${agentLibRelative}.dylib"
        else -> "${agentLibRelative}.so"
    }
}

val cmakeConfigure by tasks.registering(Exec::class) {
    group = "native"
    description = "Run CMake configure for JVMTI agent"
    // workingDir must be a File
    workingDir = agentDirFile
    val javaHome = providers.environmentVariable("JAVA_HOME").orNull ?: System.getenv("JAVA_HOME")
    if (javaHome == null) {
        throw GradleException("JAVA_HOME must be set to build the native JVMTI agent")
    }
    // Create build dir
    doFirst {
        agentBuildDirFile.mkdirs()
    }
    commandLine(
        "cmake",
        "-S",
        agentDirFile.absolutePath,
        "-B",
        agentBuildDirFile.absolutePath,
        "-DJAVA_HOME=${javaHome}"
    )
}

val cmakeBuild by tasks.registering(Exec::class) {
    group = "native"
    description = "Build JVMTI agent via CMake"
    workingDir = agentBuildDirFile
    dependsOn(cmakeConfigure)
    commandLine("cmake", "--build", ".", "--config", "Release")
}

val assembleAgent by tasks.registering(Copy::class) {
    group = "build"
    description = "Assemble native JVMTI agent into usvm-jvm build libs"
    dependsOn(cmakeBuild)

    val libName = providerAgentLibName().get()
    val sourcePath = agentBuildDirFile.resolve(libName)

    from(sourcePath)
    into(layout.buildDirectory.dir("libs"))
}

// Tests will attach the JVMTI agent only if a path is provided via project property
// -PusvmAgentPath=/full/path/to/libusvm_jvm_agent.so
// or environment variable USVM_AGENT_PATH
// Building the native agent is NOT mandatory to run tests.
val usvmAgentPathProp = providers.gradleProperty("usvmAgentPath").orNull
val usvmAgentPathEnv = providers.environmentVariable("USVM_AGENT_PATH").orNull

tasks.withType(Test::class.java) {
    doFirst {
        val providedPath = usvmAgentPathProp ?: usvmAgentPathEnv
        if (!providedPath.isNullOrBlank()) {
            val agentFile = file(providedPath)
            if (!agentFile.exists()) {
                throw GradleException("Provided JVMTI agent path does not exist: ${providedPath}")
            }
            logger.lifecycle("Attaching JVMTI agent from: ${agentFile.absolutePath}")
            jvmArgs("-agentpath:${agentFile.absolutePath}")
        } else {
            logger.lifecycle("No JVMTI agent path provided (project property 'usvmAgentPath' or env 'USVM_AGENT_PATH'); running tests without agent")
        }
    }
}
