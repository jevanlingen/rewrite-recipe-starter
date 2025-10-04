# Jacob's Rewrite Recipe Starter

Familiarize yourself with the [OpenRewrite documentation](https://docs.openrewrite.org/), in particular the [concepts & explanations](https://docs.openrewrite.org/concepts-explanations) op topics like the [lossless semantic trees](https://docs.openrewrite.org/concepts-and-explanations/lossless-semantic-trees), [recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes), [traits](https://docs.openrewrite.org/concepts-and-explanations/traits) and [visitors](https://docs.openrewrite.org/concepts-and-explanations/visitors).

You might be interested to watch some of the [videos available on OpenRewrite and Moderne](https://www.youtube.com/@moderne-and-openrewrite).

Once you want to dive into the code there is a [comprehensive getting started guide](https://docs.openrewrite.org/authoring-recipes/recipe-development-environment)
available in the OpenRewrite docs that provides more details than the below README.

## Local Publishing for Testing

Before you publish this recipe module to an artifact repository, you may want to try it out locally.
To do this on the command line, using `gradle`, run:

```bash
./gradlew publishToMavenLocal
# or ./gradlew pTML
```

This will publish to your local maven repository, typically under `~/.m2/repository`.

In the pom.xml of a different project you wish to test your recipe out in, run:

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=com.jevanlingen:rewrite-recipe-starter:LATEST \
  -Drewrite.activeRecipes=com.jevanlingen.MigrateJavaToKotlin
```

or make your recipe module a plugin dependency of rewrite-maven-plugin:

```xml
<project>
    <build>
        <plugins>
            <plugin>
                <groupId>org.openrewrite.maven</groupId>
                <artifactId>rewrite-maven-plugin</artifactId>
                <version>RELEASE</version>
                <configuration>
                    <activeRecipes>
                        <recipe>com.jevanlingen.MigrateJavaToKotlin</recipe>
                    </activeRecipes>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>com.jevanlingen</groupId>
                        <artifactId>rewrite-recipe-starter</artifactId>
                        <version>0.1.0-SNAPSHOT</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

Unlike Maven, Gradle must be explicitly configured to resolve dependencies from Maven local.
The root project of your Gradle build, make your recipe module a dependency of the `rewrite` configuration:

```groovy
plugins {
    id("java")
    id("org.openrewrite.rewrite") version("latest.release")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite("com.jevanlingen:rewrite-recipe-starter:latest.integration")
}

rewrite {
    activeRecipe("com.jevanlingen.NoGuavaListsNewArrayList")
}
```

Now you can run `gradlew rewriteRun` to run your recipe.

## Publishing to Artifact Repositories

This project is configured to publish to Moderne's open artifact repository (via the `publishing` task at the bottom of
the `build.gradle.kts` file). If you want to publish elsewhere, you'll want to update that task.
[app.moderne.io](https://app.moderne.io) can draw recipes from the provided repository, as well as from [Maven Central](https://search.maven.org).

Note:
Running the publish task _will not_ update [app.moderne.io](https://app.moderne.io), as only Moderne employees can
add new recipes. If you want to add your recipe to [app.moderne.io](https://app.moderne.io), please ask the
team in [Slack](https://join.slack.com/t/rewriteoss/shared_invite/zt-nj42n3ea-b~62rIHzb3Vo0E1APKCXEA) or in [Discord](https://discord.gg/xk3ZKrhWAb).

These other docs might also be useful for you depending on where you want to publish the recipe:

* Sonatype's instructions for [publishing to Maven Central](https://maven.apache.org/repository/guide-central-repository-upload.html)
* Gradle's instructions on the [Gradle Publishing Plugin](https://docs.gradle.org/current/userguide/publishing\_maven.html).

### From Github Actions

The `.github` directory contains a Github action that will push a snapshot on every successful build.

Run the release action to publish a release version of a recipe.

### From the command line

To build a snapshot, run `./gradlew snapshot publish` to build a snapshot and publish it to Moderne's open artifact repository for inclusion at [app.moderne.io](https://app.moderne.io).

To build a release, run `./gradlew final publish` to tag a release and publish it to Moderne's open artifact repository for inclusion at [app.moderne.io](https://app.moderne.io).

## Applying OpenRewrite recipe development best practices

We maintain a collection of [best practices for writing OpenRewrite recipes](https://docs.openrewrite.org/recipes/java/recipes).
You can apply these recommendations to your recipes by running the following command:

```bash
./gradlew --init-script init.gradle rewriteRun -Drewrite.activeRecipe=org.openrewrite.recipes.rewrite.OpenRewriteRecipeBestPractices
```
