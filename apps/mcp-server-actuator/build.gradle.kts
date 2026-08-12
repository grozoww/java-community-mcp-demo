plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
