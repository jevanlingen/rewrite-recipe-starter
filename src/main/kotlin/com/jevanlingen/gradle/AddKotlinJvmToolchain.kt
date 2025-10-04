package com.jevanlingen.gradle

import org.openrewrite.*
import org.openrewrite.Tree.randomId
import org.openrewrite.gradle.GradleParser
import org.openrewrite.gradle.marker.GradleProject
import org.openrewrite.groovy.tree.G
import org.openrewrite.java.JavaIsoVisitor
import org.openrewrite.java.tree.J
import org.openrewrite.java.tree.JavaSourceFile
import org.openrewrite.java.tree.Statement
import org.openrewrite.kotlin.marker.OmitBraces
import org.openrewrite.kotlin.tree.K
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Paths
import java.util.function.Supplier
import java.util.stream.Collectors

class AddKotlinJvmToolchain : Recipe() {
    override fun getDisplayName() = "Add Kotlin JVM toolchain to Gradle"

    override fun getDescription() = "Adds `kotlin { jvmToolchain(21) }` to `build.gradle`."

    override fun getVisitor(): TreeVisitor<*, ExecutionContext> {
        return object : JavaIsoVisitor<ExecutionContext>() {
            override fun visit(tree: Tree?, ctx: ExecutionContext): J? {
                val j = super.visit(tree, ctx)

                if (j is JavaSourceFile) {
                    val gradleProject = j.markers.findFirst(GradleProject::class.java).orElse(null)
                    if (gradleProject == null) {
                        return j
                    }
                    if (j is G.CompilationUnit && j.statements.hasNoKotlin()) {
                        val kotlinInvocation = GradleParser.builder().build()
                            .parse(ctx, template())
                            .collect(Collectors.toList())
                            .map { it as G.CompilationUnit }
                            .first().statements.first() as J.MethodInvocation
                        return j.withStatements(j.statements + kotlinInvocation)
                    } else if (j is K.CompilationUnit && j.statements.hasNoKotlin()) {
                        val input = Parser.Input(
                            Paths.get("build.gradle.kts"),
                            // Converting it to Lambda causes a `java.lang.VerifyError: Bad local variable type` error
                            object : Supplier<InputStream> {
                                override fun get() = ByteArrayInputStream(template().toByteArray())
                            }
                        )
                        val invocation = (GradleParser.builder().build()
                            .parseInputs(listOf(input), null, ctx)
                            .collect(Collectors.toList())
                            .map { it as K.CompilationUnit }
                            .first().statements.first() as J.Block).statements.first() as J.MethodInvocation
                        val kotlinInvocation = invocation.withMarkers(invocation.markers.add(OmitBraces(randomId())))
                        return j.withStatements(j.statements + kotlinInvocation)
                    }
                }
                return j
            }

            private fun List<Statement>.hasNoKotlin() =
                filterIsInstance<J.MethodInvocation>().none { it.name.simpleName == "kotlin" }

            private fun template() = """
                
                
                    kotlin {
                        jvmToolchain(21)
                    }
                """.trimIndent()
        }
    }
}
