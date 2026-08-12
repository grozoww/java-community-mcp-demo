plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    // Streamable HTTP MCP server on top of Spring MVC (virtual threads friendly).
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
