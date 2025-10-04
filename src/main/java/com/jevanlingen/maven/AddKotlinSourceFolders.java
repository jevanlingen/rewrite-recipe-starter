package com.jevanlingen.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.xml.AddToTagVisitor;
import org.openrewrite.xml.tree.Xml;

import java.util.Optional;

public class AddKotlinSourceFolders extends Recipe {

    @Override
    public String getDisplayName() {
        return "Add Kotlin source folders to Maven";
    }

    @Override
    public String getDescription() {
        return "Adds `src/main/kotlin` and `src/test/kotlin` as source folders.";
    }

    @Override
    public MavenIsoVisitor<ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Document visitDocument(Xml.Document document, ExecutionContext executionContext) {
                Xml.Tag root = document.getRoot();
                root.getChild("build").ifPresent(build -> {
                    Optional<Xml.Tag> sourceDirectory = build.getChild("sourceDirectory");
                    if (!sourceDirectory.isPresent()) {
                        doAfterVisit(new AddToTagVisitor<>(build, Xml.Tag.build("<sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>")));
                    }
                    Optional<Xml.Tag> testSourceDirectory = build.getChild("testSourceDirectory");
                    if (!testSourceDirectory.isPresent()) {
                        doAfterVisit(new AddToTagVisitor<>(build, Xml.Tag.build("<testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>")));
                    }
                });
                return super.visitDocument(document, executionContext);
            }
        };
    }
}
