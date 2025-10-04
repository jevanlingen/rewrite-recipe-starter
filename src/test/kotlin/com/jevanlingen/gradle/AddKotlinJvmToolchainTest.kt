package com.jevanlingen.gradle

import org.junit.jupiter.api.Test
import org.openrewrite.gradle.Assertions
import org.openrewrite.gradle.Assertions.buildGradle
import org.openrewrite.gradle.Assertions.buildGradleKts
import org.openrewrite.java.Assertions.mavenProject
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

internal class AddKotlinJvmToolchainTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(AddKotlinJvmToolchain())
    }

    @Test
    fun addKotlinJvmToolchain() {
        rewriteRun(
            mavenProject(
                "project",  //language=groovy
                Assertions.buildGradle(
                    """
                                        plugins {
                                            id "java-library"
                                        }
                                        
                                        repositories {
                                            mavenCentral()
                                        }
                                        """.trimIndent(),
                    """
                                        plugins {
                                            id "java-library"
                                        }
                                        
                                        repositories {
                                            mavenCentral()
                                        }
                                        
                                        kotlin {
                                            jvmToolchain(21)
                                        }
                                        """.trimIndent()
                )
            )
        )
    }

    @Test
    fun addKotlinJvmToolchainForKotlin() {
        rewriteRun(
            //language=kotlin
            mavenProject(
                "project",
                buildGradleKts(
                    """
                                        plugins {
                                            `java-library`
                                        }
                                        
                                        repositories {
                                            mavenCentral()
                                        }
                                        """.trimIndent(),
                    """
                                        plugins {
                                            `java-library`
                                        }
                                        
                                        repositories {
                                            mavenCentral()
                                        }
                                        
                                        kotlin {
                                            jvmToolchain(21)
                                        }
                                        """.trimIndent()
                )
            )
        )
    }
}

