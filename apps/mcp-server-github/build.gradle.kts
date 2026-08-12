plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    // Streamable HTTP MCP server on top of Spring MVC (virtual threads friendly).
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
