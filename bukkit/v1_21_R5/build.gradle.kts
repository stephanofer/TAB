dependencies {
    implementation(projects.bukkit)
    compileOnly("org.spigotmc:spigot:1.21.8-R0.1-SNAPSHOT")
}

tasks.compileJava {
    options.release.set(25)
}
