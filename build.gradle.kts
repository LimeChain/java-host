plugins {
    id("java")
    id("io.freefair.lombok") version "8.12"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("application")
}

application {
    mainClass.set("com.limechain.Main")
}

group = "com.limechain"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_22

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://artifacts.consensys.net/public/maven/maven/")
}

dependencies {
    implementation("net.openhft:zero-allocation-hashing:0.27ea0")
    implementation("org.rocksdb:rocksdbjni:9.8.4")
    compileOnly("org.projectlombok:lombok:1.18.36")
    implementation("org.projectlombok:lombok:1.18.36")
    implementation("org.web3j:crypto:4.12.3")
    implementation("com.dylibso.chicory:wasm:0.0.12")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")

    // CLI
    implementation("commons-cli:commons-cli:1.9.0")

    // TODO: Publish imported packages to mvnrepository and import them
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Websockets
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.javatuples:javatuples:1.2")

    implementation("com.github.luben:zstd-jni:1.5.6-9")

    // Prometheus
    implementation("io.prometheus:prometheus-metrics-core:1.3.5")
    implementation("io.prometheus:prometheus-metrics-instrumentation-jvm:1.3.5")
    implementation("io.prometheus:prometheus-metrics-exporter-httpserver:1.3.5")

    // NOTE:
    //  We implicitly rely on Nabu's transitive dependency on Netty's public interfaces.
    //  We could explicitly include Netty ourselves, but we prefer to make sure we use the same version as Nabu.

    // NOTE:
    //  For jitpack's syntax for GitHub version resolution, refer to: https://docs.jitpack.io/

    // Nabu
//    implementation("com.github.LimeChain:nabu:master-SNAPSHOT") // Uncomment for "most-recent on the master branch"
    implementation("com.github.LimeChain:nabu:0.8.0")

    //JSON-RPC
    implementation("com.github.LimeChain:jsonrpc4j:1.7.0")

    // Guava
    implementation("com.google.guava:guava:33.4.0-jre")

    // Apache commons
    implementation("commons-io:commons-io:2.18.0")

}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}

tasks.getByName<Jar>("jar") {
    enabled = false //To remove the build/libs/Fruzhin-ver-plain.jar
}