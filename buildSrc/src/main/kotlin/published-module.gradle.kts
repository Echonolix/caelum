package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}