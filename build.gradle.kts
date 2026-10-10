plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "me.skoohy"
version = rootProject.property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Compile against the OLDEST version we support (1.21.11). Anything that
    // exists there also exists on 26.1 / 26.2 / 26.3, so one jar runs on all.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    compileOnly("me.clip:placeholderapi:2.12.3")

    // SQLite JDBC (shaded into the final jar)
    implementation("org.xerial:sqlite-jdbc:3.44.0.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    // Test on any version with:  .\gradlew.bat runServer -PmcVersion=26.3
    runServer {
        minecraftVersion((findProperty("mcVersion") as String?) ?: "1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    // IMPORTANT: the default `jar` task and `shadowJar` were both writing
    // to the same output filename (archiveClassifier = ""), and with
    // org.gradle.parallel=true in gradle.properties, Gradle doesn't
    // guarantee which one finishes last - so the plain, un-shaded jar
    // (missing SQLite entirely) could silently overwrite the
    // correct shaded one on disk. Disabling the plain jar task entirely
    // guarantees shadowJar's output is always the one that exists.
    jar {
        enabled = false
    }

    // Java 21 bytecode: loads on Java 21 (1.21.11 servers) AND Java 25 (26.x servers).
    // Without this the jar is built for Java 25 and 1.21.11 servers on Java 21 can't load it.
    compileJava {
        options.release.set(21)
    }

    shadowJar {
        archiveClassifier.set("")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}