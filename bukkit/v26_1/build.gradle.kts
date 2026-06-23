dependencies {
    implementation(projects.bukkit)
    compileOnly("org.spigotmc:spigot:26.1-R0.1-SNAPSHOT")
}

tasks.compileJava {
    options.release.set(25)
}
