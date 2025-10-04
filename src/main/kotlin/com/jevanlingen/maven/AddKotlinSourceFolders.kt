package com.jevanlingen.maven

import org.openrewrite.ExecutionContext
import org.openrewrite.Recipe
import org.openrewrite.maven.MavenIsoVisitor
import org.openrewrite.xml.AddToTagVisitor
import org.openrewrite.xml.tree.Xml

class AddKotlinSourceFolders : Recipe() {
    override fun getDisplayName() = "Add Kotlin source folders to Maven"

    override fun getDescription() = "Adds `src/main/kotlin` and `src/test/kotlin` as source folders."

    override fun getVisitor(): MavenIsoVisitor<ExecutionContext> {
        return object : MavenIsoVisitor<ExecutionContext>() {
            override fun visitDocument(document: Xml.Document, executionContext: ExecutionContext): Xml.Document {
                document.root.getChild("build").ifPresent {
                    val sourceDirectory = it.getChild("sourceDirectory")
                    if (!sourceDirectory.isPresent) {
                        doAfterVisit(
                            AddToTagVisitor(
                                it,
                                Xml.Tag.build("<sourceDirectory>\${project.basedir}/src/main/kotlin</sourceDirectory>")
                            )
                        )
                    }
                    val testSourceDirectory = it.getChild("testSourceDirectory")
                    if (!testSourceDirectory.isPresent) {
                        doAfterVisit(
                            AddToTagVisitor(
                                it,
                                Xml.Tag.build("<testSourceDirectory>\${project.basedir}/src/test/kotlin</testSourceDirectory>")
                            )
                        )
                    }
                }
                return super.visitDocument(document, executionContext)
            }
        }
    }
}
