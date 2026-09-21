import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "com.galitianu"
version = "0.2.0-SNAPSHOT"

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
    api("com.fasterxml.jackson.core:jackson-databind:2.22.2")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaToolchain))
    }
}

// Version.java is generated so the version has exactly one source of truth: this file.
val generateVersionSource = tasks.register<Copy>("generateVersionSource") {
    description = "Expands src/main/templates into build/generated with the project version."
    inputs.property("version", provider { project.version.toString() })
    from(layout.projectDirectory.dir("src/main/templates/java"))
    into(layout.buildDirectory.dir("generated/sources/version/java"))
    filteringCharset = "UTF-8"
    filter<ReplaceTokens>("tokens" to mapOf("version" to project.version.toString()))
}

sourceSets.main {
    java.srcDir(generateVersionSource)
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
    // The API reference documents what the module exports, and nothing else. Gradle
    // passes source files explicitly, which makes javadoc ignore --show-packages, so
    // the unexported package is dropped from the sources instead and read from the
    // compiled classes. That only works outside module mode, which is why module-info
    // is left out too; it carries no API of its own.
    exclude("**/internal/**", "**/module-info.java")
    modularity.inferModulePath.set(false)
    dependsOn(tasks.classes)
    classpath += files(sourceSets.main.map { it.output })
}

tasks.jar {
    manifest {
        attributes(
            // The module name comes from src/main/java/module-info.java, not a manifest
            // attribute; an Automatic-Module-Name here would be ignored.
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

// Prints the version for release tooling: `./gradlew -q printVersion`.
tasks.register("printVersion") {
    val v = provider { project.version.toString() }
    doLast { println(v.get()) }
}

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = SourcesJar.Sources()))
    // Uploads to the Central Portal. `publishToMavenCentral` only stages the deployment
    // (and is what -SNAPSHOT builds use); `publishAndReleaseToMavenCentral` also releases
    // it. The release workflow calls the latter, so the choice is visible at the call site.
    publishToMavenCentral()
    // Only CI has a PGP key, so skip signing without one and keep `publishToMavenLocal`
    // usable on a laptop. The release workflow checks the key is present before it runs,
    // and the Portal rejects an unsigned deployment regardless. See RELEASING.md.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "jev4j", version.toString())

    pom {
        name.set("jev4j")
        description.set("Java SDK for the TypeSafe AI API: typed yes/no, choice and score questions in one call.")
        inceptionYear.set("2026")
        url.set("https://github.com/galitianu/jev4j")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/galitianu/jev4j/blob/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("galitianu")
                name.set("Andrei Galitianu")
                url.set("https://github.com/galitianu")
            }
        }
        scm {
            url.set("https://github.com/galitianu/jev4j")
            connection.set("scm:git:git://github.com/galitianu/jev4j.git")
            developerConnection.set("scm:git:ssh://git@github.com/galitianu/jev4j.git")
        }
    }
}
