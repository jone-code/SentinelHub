plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("com.fasterxml.jackson.core:jackson-databind")
}
