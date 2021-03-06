/*
 * Copyright (c) 2020
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import net.researchgate.release.GitAdapter.GitConfig
import net.researchgate.release.ReleaseExtension

buildscript {
    repositories {
        google()
    }
    dependencies {
        val androidBuildPluginVersion: String by project
        classpath("com.android.tools.build:gradle:$androidBuildPluginVersion")
    }
}
plugins {
    id("net.researchgate.release")
}

release {
    tagTemplate = "acra-$version"
    git {
        pushToRemote = "ACRA"
        requireBranch = "master"
    }
}
tasks["afterReleaseBuild"].dependsOn("publish")

subprojects {
    repositories {
        jcenter()
        mavenCentral()
        google()
        maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots") }
    }
    plugins.withType<LibraryPlugin> {
        android {
            val androidVersion: String by project
            val buildToolsVersion: String by project
            val androidMinVersion: String by project
            compileSdkVersion(Integer.parseInt(androidVersion))
            buildToolsVersion(buildToolsVersion)
            defaultConfig {
                minSdkVersion(androidMinVersion)
                targetSdkVersion(androidVersion)
                versionNameSuffix = "$version"
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = false
                }
            }
            lintOptions {
                isAbortOnError = false
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        tasks.withType<Test> {
            systemProperty("robolectric.logging.enabled", true)
        }

        dependencies {
            val junitVersion: String by project
            val robolectricVersion: String by project
            val androidTestVersion: String by project
            val hamcrestVersion: String by project
            "testImplementation"("junit:junit:$junitVersion")
            "testImplementation"("org.hamcrest:hamcrest:$hamcrestVersion")
            "testImplementation"("org.robolectric:robolectric:$robolectricVersion")
            "testImplementation"("androidx.test:core:$androidTestVersion")
        }

        tasks.register<Jar>("sourcesJar") {
            from(android.sourceSets["main"].java.srcDirs)
            archiveClassifier.set("sources")
        }

        tasks.register<Javadoc>("javadoc") {
            source = (android.sourceSets["main"].java.srcDirs.map { fileTree(it) }.reduce(FileTree::plus) +
                    files("$buildDir/generated/source/buildConfig/release") +
                    files("$buildDir/generated/ap_generated_sources/release/out")).filter { it.extension != "kt" }.asFileTree
            classpath += files(*android.bootClasspath.toTypedArray())
            android.libraryVariants.find { it.name == "release" }?.apply {
                classpath += javaCompileProvider.get().classpath
            }
            linksOffline("http://d.android.com/reference", "${android.sdkDirectory.path}/docs/reference")
            dependsOn("assembleRelease")
        }

        tasks.register<Jar>("javadocJar") {
            from(tasks["javadoc"])
            archiveClassifier.set("javadoc")
        }
    }
    plugins.withType<JavaPlugin> {
        tasks.register<Jar>("sourcesJar") {
            from(sourceSets["main"].allSource)
            archiveClassifier.set("sources")
        }

        tasks.register<Jar>("javadocJar") {
            from(tasks["javadoc"])
            archiveClassifier.set("javadoc")
        }
    }
    plugins.withType<MavenPublishPlugin> {
        afterEvaluate {
            publishing {
                publications {
                    create<MavenPublication>("maven") {
                        from(components.findByName("release") ?: components.findByName("java"))

                        artifact(tasks["sourcesJar"])
                        artifact(tasks["javadocJar"])

                        pom {
                            name.set("ACRA")
                            description.set("Publishes reports of Android application crashes to an end point.")
                            url.set("http://acra.ch")
                            scm {
                                connection.set("scm:git:https://github.com/F43nd1r/acra.git")
                                developerConnection.set("scm:git:git@github.com:F43nd1r/acra.git")
                                url.set("https://github.com/F43nd1r/acra.git")
                            }
                            licenses {
                                license {
                                    name.set("Apache-2.0")
                                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    distribution.set("repo")
                                }
                            }
                            developers {
                                developer {
                                    id.set("kevin.gaudin")
                                    name.set("Kevin Gaudin")
                                }
                                developer {
                                    id.set("william.ferguson")
                                    name.set("William Ferguson")
                                }
                                developer {
                                    id.set("f43nd1r")
                                    name.set("Lukas Morawietz")
                                }
                            }
                        }
                    }
                }
                repositories {
                    mavenLocal()
                    maven {
                        setUrl("https://api.bintray.com/maven/acra/maven/ACRA/;publish=1")
                        name = "bintray"
                        credentials {
                            username = findProperty("bintrayUser") as String?
                            password = findProperty("bintrayPassword") as String?
                        }
                    }
                }
            }
        }
    }
}

// TASKS

tasks.register("build") {}

tasks.register("publish") {
    subprojects {
        tasks.findByName("publish")?.let { dependsOn(it) }
    }
}

tasks.register<Delete>("clean") {
    delete = setOf(buildDir)
}

tasks.register<Javadoc>("joinedJavadoc") {
    setDestinationDir(file("$buildDir/javadoc"))
    subprojects {
        afterEvaluate {
            val tasks = tasks.withType(Javadoc::class.java)
            source += files(*tasks.map { it.source }.toTypedArray()).asFileTree
            classpath += files(*tasks.map { it.classpath }.toTypedArray())
            dependsOn(*tasks.map { it.dependsOn }.toTypedArray())
            plugins.withType(LibraryPlugin::class.java) {
                linksOffline("http://d.android.com/reference", "${android.sdkDirectory.path}/docs/reference")
                android.libraryVariants.find { it.name == "release" }?.apply {
                    classpath += javaCompileProvider.get().classpath
                }
            }
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println(version)
    }
}

// UTILS

fun ReleaseExtension.git(configure: GitConfig.() -> Unit) = (getProperty("git") as GitConfig).configure()

@Suppress("ObjectLiteralToLambda")
fun subprojects(action: Project.() -> Unit) = subprojects(object : Action<Project> {
    override fun execute(t: Project) = t.action()
})

val Project.android: LibraryExtension get() = this.extensions.getByType(LibraryExtension::class.java)

fun Project.android(block: LibraryExtension.() -> Unit) = android.apply(block)

fun Project.publishing(block: PublishingExtension.() -> Unit) = this.extensions.getByType(PublishingExtension::class.java).apply(block)

val Project.sourceSets: SourceSetContainer get() = this.extensions.getByType(SourceSetContainer::class.java)

fun Javadoc.linksOffline(extDocUrl: String, packageListLoc: String) = (options as StandardJavadocDocletOptions).linksOffline(extDocUrl, packageListLoc)
