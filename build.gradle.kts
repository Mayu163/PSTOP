plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "dev.pstop"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.oshi:oshi-core:6.6.6")
    implementation("org.jline:jline-terminal:3.27.1")
    implementation("org.jline:jline-terminal-jna:3.27.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

application {
    mainClass.set("dev.pstop.MainKt")
    applicationName = "pstop"
}

val runtimeImageDirectory = layout.buildDirectory.dir("runtime")
val javaLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Pstop"
        attributes["Implementation-Version"] = project.version
    }
}

val fatJar = tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Builds the dependency-complete JAR used by the Windows executable."
    archiveFileName.set("pstop.jar")
    destinationDirectory.set(layout.buildDirectory.dir("jpackage-input"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.classes)

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.exists() }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "Pstop"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.register<Exec>("createRuntimeImage") {
    group = "distribution"
    description = "Creates the minimized Java runtime shipped with Pstop."
    val outputDirectory = runtimeImageDirectory.get().asFile
    inputs.property("modules", "java.base,java.desktop,java.logging,java.management,java.naming,jdk.crypto.ec,jdk.management,jdk.unsupported")
    outputs.dir(outputDirectory)

    doFirst {
        delete(outputDirectory)
        val jlink = javaLauncher.get().metadata.installationPath.file("bin/jlink.exe").asFile
        commandLine(
            jlink,
            "--add-modules",
            inputs.properties["modules"],
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-6",
            "--output",
            outputDirectory,
        )
    }
}

tasks.register<Sync>("packageRelease") {
    group = "distribution"
    description = "Creates a self-contained Pstop application folder under dist/pstop."
    dependsOn(tasks.installDist, tasks.named("createRuntimeImage"))
    from(layout.buildDirectory.dir("install/pstop"))
    from(runtimeImageDirectory) {
        into("runtime")
    }
    from(layout.projectDirectory.dir("packaging"))
    into(layout.projectDirectory.dir("dist/pstop"))
}

val executableImageDirectory = layout.buildDirectory.dir("jpackage-output")

val createExeImage = tasks.register<Exec>("createExeImage") {
    group = "distribution"
    description = "Creates the portable Windows Pstop.exe application image under build."
    dependsOn(fatJar, tasks.named("createRuntimeImage"))

    val executable = executableImageDirectory.map { it.file("Pstop/Pstop.exe") }
    inputs.file(fatJar.flatMap { it.archiveFile })
    inputs.dir(runtimeImageDirectory)
    outputs.file(executable)

    doFirst {
        delete(executableImageDirectory)
        val jpackage = javaLauncher.get().metadata.installationPath.file("bin/jpackage.exe").asFile
        commandLine(
            jpackage,
            "--type",
            "app-image",
            "--name",
            "Pstop",
            "--app-version",
            project.version.toString(),
            "--vendor",
            "Pstop contributors",
            "--description",
            "Windows Terminal resource monitor",
            "--input",
            layout.buildDirectory.dir("jpackage-input").get().asFile,
            "--main-jar",
            "pstop.jar",
            "--main-class",
            application.mainClass.get(),
            "--runtime-image",
            runtimeImageDirectory.get().asFile,
            "--dest",
            executableImageDirectory.get().asFile,
            "--win-console",
            "--java-options",
            "-Dfile.encoding=UTF-8",
            "--java-options",
            "-Dstdout.encoding=UTF-8",
            "--java-options",
            "-Dstderr.encoding=UTF-8",
        )
    }
}

tasks.register<Sync>("packageExe") {
    group = "distribution"
    description = "Installs the portable Windows Pstop.exe application image under dist/exe/Pstop."
    dependsOn(createExeImage)
    from(executableImageDirectory.map { it.dir("Pstop") })
    into(layout.projectDirectory.dir("dist/exe/Pstop"))
}

val standalonePayload = tasks.register<Zip>("standalonePayload") {
    group = "distribution"
    description = "Compresses the complete Pstop app image for embedding."
    dependsOn(createExeImage)
    from(executableImageDirectory.map { it.dir("Pstop") })
    archiveFileName.set("pstop-payload.zip")
    destinationDirectory.set(layout.buildDirectory.dir("standalone-payload"))
    isZip64 = true
}

val standaloneOutputDirectory = layout.buildDirectory.dir("standalone-output")

val createStandaloneExe = tasks.register<Exec>("createStandaloneExe") {
    group = "distribution"
    description = "Builds the true single-file self-extracting Pstop.exe."
    dependsOn(standalonePayload)

    val executable = standaloneOutputDirectory.map { it.file("Pstop.exe") }
    inputs.file(standalonePayload.flatMap { it.archiveFile })
    inputs.file(layout.projectDirectory.file("launcher/PstopLauncher.cs"))
    inputs.file(layout.projectDirectory.file("launcher/PstopLauncher.manifest"))
    outputs.file(executable)

    doFirst {
        delete(standaloneOutputDirectory)
        mkdir(standaloneOutputDirectory)

        val windowsDirectory = System.getenv("WINDIR") ?: "C:\\Windows"
        val frameworkDirectory = file("$windowsDirectory/Microsoft.NET/Framework64/v4.0.30319")
        val compiler = frameworkDirectory.resolve("csc.exe")
        val compressionAssembly = frameworkDirectory.resolve("System.IO.Compression.dll")
        if (!compiler.isFile || !compressionAssembly.isFile) {
            throw GradleException("The Windows .NET Framework 4.x C# compiler is required to build Pstop.exe.")
        }

        val payload = standalonePayload.get().archiveFile.get().asFile
        val source = layout.projectDirectory.file("launcher/PstopLauncher.cs").asFile
        val manifest = layout.projectDirectory.file("launcher/PstopLauncher.manifest").asFile
        commandLine(
            compiler,
            "/nologo",
            "/target:exe",
            "/platform:x64",
            "/optimize+",
            "/debug-",
            "/utf8output",
            "/out:${executable.get().asFile}",
            "/win32manifest:$manifest",
            "/reference:$compressionAssembly",
            "/resource:$payload,Pstop.Payload.zip",
            source,
        )
    }
}

tasks.register<Copy>("packageStandaloneExe") {
    group = "distribution"
    description = "Copies the true standalone Pstop.exe to dist/Pstop.exe."
    dependsOn(createStandaloneExe)
    from(standaloneOutputDirectory.map { it.file("Pstop.exe") })
    into(layout.projectDirectory.dir("dist"))
}
