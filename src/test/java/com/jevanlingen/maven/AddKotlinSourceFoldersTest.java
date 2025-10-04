
package com.jevanlingen.maven;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

class AddKotlinSourceFoldersTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddKotlinSourceFolders());
    }

    @DocumentExample
    @Test
    void addPluginWithConfiguration() {
        rewriteRun(
                //language=xml
                pomXml(
                        """
                                <project>
                                  <groupId>com.mycompany.app</groupId>
                                  <artifactId>my-app</artifactId>
                                  <version>1</version>
                                  <build>
                                  </build>
                                </project>
                                """,
                        """
                                <project>
                                  <groupId>com.mycompany.app</groupId>
                                  <artifactId>my-app</artifactId>
                                  <version>1</version>
                                  <build>
                                    <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
                                    <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
                                  </build>
                                </project>
                                """
                )
        );
    }
}
