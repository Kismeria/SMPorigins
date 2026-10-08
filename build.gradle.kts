plugins {
    java
}

group = "me.kismeria"
version = "2.4.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.152-beta")
    // кнопка в /smp, сброс SMP, меню в стиле SmpCore
    // любая версия, лежащая в build/libs SmpCore (раньше была прибита 1.21.2 — после clean сборка падала)
    compileOnly(fileTree("../SmpCore/build/libs") { include("SmpCore-*.jar") })
    // packet-level armour hiding for the rogue (Netty ships with the server)
    compileOnly("io.netty:netty-transport:4.2.16.Final")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
