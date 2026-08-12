plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.ai.bom))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // The agent: a local Ollama model plus MCP clients.
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")

    // Code Mode: a JavaScript sandbox the model can write into.
    implementation(libs.graal.polyglot)
    runtimeOnly(libs.graal.js)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
