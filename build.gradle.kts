plugins {
    `java-library`
    `maven-publish`
}

group = "com.galitianu"
version = "0.1.0-SNAPSHOT"

// The version users compile against. Java 17 is the floor Spring Boot 3.x sets, and the
// library uses nothing newer; see close() in TypeSafeClient for the one runtime concession.
val javaRelease = 17

// The JDK that runs the build. CI overrides it (-PjavaToolchain=17|21|25) to prove the
// library builds and its tests pass on every JDK it claims to support.
val javaToolchain = (providers.gradleProperty("javaToolchain").getOrNull() ?: "21").toInt()

repositories {
    mavenCentral()
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaToolchain))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaRelease)
    options.compilerArgs.add("-Xlint:all,-serial,-processing")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    with(options as StandardJavadocDocletOptions) {
        source = javaRelease.toString()
        // Fail the build on broken {@link} targets, bad @param/@return names and
        // malformed HTML. -missing allows types to go undocumented; it does not
        // excuse a doc comment that is present but wrong.
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addStringOption("Xwerror", "-quiet")
        windowTitle = "jev4j $version"
        docTitle = "jev4j $version"
        bottom = "jev4j &mdash; Java SDK for the TypeSafe AI API"
    }
}

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "com.galitianu.jev4j",
            "Implementation-Title" to "jev4j",
            "Implementation-Version" to project.version,
        )
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Runnable example in src/demo/java. Needs TYPESAFE_API_KEY: `./gradlew demo`
val demo: SourceSet = sourceSets.create("demo") {
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

tasks.register<JavaExec>("demo") {
    group = "application"
    description = "Runs the demo against the live API (needs TYPESAFE_API_KEY)."
    classpath = demo.runtimeClasspath
    mainClass.set("Demo")
}

// The demo is the README's worked example; compiling it in `check` keeps it from rotting
// silently, since running it needs a live API key that CI does not have.
tasks.check {
    dependsOn(tasks.named("compileDemoJava"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("jev4j")
                description.set("jev4j: Java SDK for the TypeSafe AI API")
                url.set("https://typesafe.ai")
                licenses {
                    license {
                        name.set("MIT")
                    }
                }
            }
        }
    }
}
