rootProject.name = "mcp-java-community"

include(":apps:mcp-server-github")
include(":apps:mcp-server-actuator")
include(":apps:agent-chat")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Spring milestones/snapshots - only needed if you bump to a pre-GA Spring AI build.
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}
