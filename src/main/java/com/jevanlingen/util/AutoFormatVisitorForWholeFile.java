/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jevanlingen.util;


import org.jspecify.annotations.Nullable;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.format.*;
import org.openrewrite.kotlin.style.*;
import org.openrewrite.style.Style;
import org.openrewrite.tree.ParseError;

import static java.util.Objects.requireNonNull;

// THIS FORMATTER ONLY REMOVES THE `J.CompilationUnit` CHECK, AS WE WANT TO UPDATE WHOLE KOTLIN FILES
// DON'T ALTER ANYTHING ELSE, BUT ADD A <PR> TO THE OpenRewrite PROJECT IF YOU WANT DIFFERENT STYLING
public class AutoFormatVisitorForWholeFile<P> extends AutoFormatVisitor<P> {
    @Nullable
    private final Tree stopAfter;

    @SuppressWarnings("unused")
    public AutoFormatVisitorForWholeFile() {
        this(null);
    }

    public AutoFormatVisitorForWholeFile(@Nullable Tree stopAfter) {
        super(stopAfter);
        this.stopAfter = stopAfter;
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
            // Avoid reformatting entire Groovy source files, or other J-derived ASTs
            // Java AutoFormat does OK for a snippet of Groovy, But whole-file reformatting is inadvisable and there is
            // currently no easy way to customize or fine-tune for Groovy
            // if (!(cu instanceof J.CompilationUnit)) {
            //    return cu;
            // }

            JavaSourceFile t = (JavaSourceFile) new RemoveTrailingWhitespaceVisitor<>(stopAfter).visit(cu, p);
            t = (JavaSourceFile) new BlankLinesVisitor<>(Style.from(BlankLinesStyle.class, cu, IntelliJ::blankLines), stopAfter)
                    .visit(t, p);
            t = (JavaSourceFile) new SpacesVisitor<P>(Style.from(SpacesStyle.class, cu, IntelliJ::spaces),
                    stopAfter)
                    .visit(t, p);
            t = (JavaSourceFile) new WrappingAndBracesVisitor<>(Style.from(WrappingAndBracesStyle.class, cu, IntelliJ::wrappingAndBraces), stopAfter)
                    .visit(t, p);
            t = (JavaSourceFile) new NormalizeTabsOrSpacesVisitor<>(Style.from(TabsAndIndentsStyle.class, cu, IntelliJ::tabsAndIndents), stopAfter)
                    .visit(t, p);
            t = (JavaSourceFile) new TabsAndIndentsVisitor<>(
                    Style.from(TabsAndIndentsStyle.class, cu, IntelliJ::tabsAndIndents),
                    Style.from(WrappingAndBracesStyle.class, cu, IntelliJ::wrappingAndBraces),
                    stopAfter
            ).visit(t, p);

            return new TrailingCommaVisitor<>(IntelliJ.other().getUseTrailingComma()).visitNonNull(t, p);
        }
        return (J) tree;
    }
}
