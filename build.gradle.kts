plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

allprojects {
    group = "com.dataart.jc"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            // Java 25 LTS. Requires Gradle 9.1+ to run on / target JDK 25.
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-serial"))
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
