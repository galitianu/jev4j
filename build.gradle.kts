plugins {
    `java-library`
    `maven-publish`
}

group = "com.galitianu"
version = "0.1.0-SNAPSHOT"

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
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all,-serial,-processing")
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    with(options as StandardJavadocDocletOptions) {
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
