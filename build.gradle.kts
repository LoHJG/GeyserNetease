plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

group = "nc.geyserext"
version = "1.2.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

// Versions mirror Geyser 2.12.0 (see gradle/libs.versions.toml in the Geyser repository)
val geyserVersion = "2.12.0-SNAPSHOT"
val protocolVersion = "3.0.0.Beta14-SNAPSHOT"
val raknetVersion = "2.0.0.CR5-SNAPSHOT"
val nettyVersion = "4.2.7.Final"
val mcprotocollibVersion = "26.3"

dependencies {
    // Geyser core — provided by the Geyser platform at runtime
    compileOnly("org.geysermc.geyser:core:$geyserVersion") {
        exclude(group = "com.google.code.gson", module = "gson")
        exclude(group = "org.cloudburstmc.netty", module = "netty-transport-raknet")
    }

    // Protocol library — provided by Geyser at runtime
    compileOnly("org.cloudburstmc.protocol:common:$protocolVersion")
    compileOnly("org.cloudburstmc.protocol:bedrock-codec:$protocolVersion")
    compileOnly("org.cloudburstmc.protocol:bedrock-connection:$protocolVersion")

    // RakNet / Netty — provided by Geyser and the server at runtime
    compileOnly("org.cloudburstmc.netty:netty-transport-raknet:$raknetVersion")
    compileOnly("io.netty:netty-transport:$nettyVersion")
    compileOnly("io.netty:netty-buffer:$nettyVersion")
    compileOnly("io.netty:netty-codec:$nettyVersion")
    compileOnly("io.netty:netty-common:$nettyVersion")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("org.checkerframework:checker-qual:3.19.0")
    compileOnly("org.geysermc.mcprotocollib:protocol:$mcprotocollibVersion")

    // Bundled into shadowJar (implementation)
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    implementation("org.bitbucket.b_c:jose4j:0.9.4")
}

tasks.shadowJar {
    archiveFileName = "GeyserNeteaseExtension.jar"
    dependencies {
        include(dependency("org.yaml:snakeyaml:2.2"))
        include(dependency("com.fasterxml.jackson.core:jackson-databind:2.17.0"))
        include(dependency("com.fasterxml.jackson.core:jackson-annotations:2.17.0"))
        include(dependency("com.fasterxml.jackson.core:jackson-core:2.17.0"))
        include(dependency("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0"))
        include(dependency("org.bitbucket.b_c:jose4j:0.9.4"))
    }
    // Strip dependency license files and service descriptors (not needed at runtime)
    exclude("META-INF/LICENSE", "META-INF/NOTICE", "META-INF/*LICENSE*", "META-INF/*NOTICE*",
        "META-INF/thirdparty-LICENSE", "META-INF/maven/**", "META-INF/services/**")
    relocate("com.fasterxml.jackson", "nc.geyserext.shaded.jackson")
    relocate("org.yaml.snakeyaml", "nc.geyserext.shaded.yaml")
    relocate("org.jose4j", "nc.geyserext.shaded.jose4j")
}
