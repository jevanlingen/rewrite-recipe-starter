package com.jevanlingen.maven

import org.junit.jupiter.api.Test
import org.openrewrite.DocumentExample
import org.openrewrite.maven.Assertions
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

internal class AddKotlinSourceFoldersTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(AddKotlinSourceFolders())
    }

    @DocumentExample
    @Test
    fun addPluginWithConfiguration() {
        rewriteRun( //language=xml
            Assertions.pomXml(
                """
                                <project>
                                  <groupId>com.mycompany.app</groupId>
                                  <artifactId>my-app</artifactId>
                                  <version>1</version>
                                  <build>
                                  </build>
                                </project>
                                
                                """.trimIndent(),
                """
                                <project>
                                  <groupId>com.mycompany.app</groupId>
                                  <artifactId>my-app</artifactId>
                                  <version>1</version>
                                  <build>
                                    <sourceDirectory>${'$'}{project.basedir}/src/main/kotlin</sourceDirectory>
                                    <testSourceDirectory>${'$'}{project.basedir}/src/test/kotlin</testSourceDirectory>
                                  </build>
                                </project>
                                
                                """.trimIndent()
            )
        )
    }
}

