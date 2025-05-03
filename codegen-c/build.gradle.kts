allprojects {
    repositories {
        maven("https://maven.endoqa.io")
    }
}

dependencies {
    implementation(project(":codegen-api"))
    implementation(project(":caelum-core"))
    implementation(project(":codegen-c:adapter"))
}