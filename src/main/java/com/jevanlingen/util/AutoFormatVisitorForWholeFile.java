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
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.format.*;
import org.openrewrite.kotlin.style.*;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class AutoFormatVisitorForWholeFile<P> extends AutoFormatVisitor<P> {
    @Nullable
    private final Tree stopAfter;

    @SuppressWarnings("unused")
    public AutoFormatVisitorForWholeFile() {
        this(null);
    }

    public AutoFormatVisitorForWholeFile(@Nullable Tree stopAfter) {
        this.stopAfter = stopAfter;
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);

            JavaSourceFile t = (JavaSourceFile) new RemoveTrailingWhitespaceVisitor<>(stopAfter).visit(cu, p);

            t = (JavaSourceFile) new BlankLinesVisitor<>(Optional.ofNullable(cu.getStyle(BlankLinesStyle.class))
                    .orElse(IntelliJ.blankLines()), stopAfter)
                    .visit(t, p);

            t = (JavaSourceFile) new SpacesVisitor<P>(Optional.ofNullable(
                    cu.getStyle(SpacesStyle.class)).orElse(IntelliJ.spaces()),
                    stopAfter)
                    .visit(t, p);

            t = (JavaSourceFile) new WrappingAndBracesVisitor<>(Optional.ofNullable(cu.getStyle(WrappingAndBracesStyle.class))
                    .orElse(IntelliJ.wrappingAndBraces()), stopAfter)
                    .visit(t, p);

            t = (JavaSourceFile) new NormalizeTabsOrSpacesVisitor<>(Optional.ofNullable(cu.getStyle(TabsAndIndentsStyle.class))
                    .orElse(IntelliJ.tabsAndIndents()), stopAfter)
                    .visit(t, p);

            t = (JavaSourceFile) new TabsAndIndentsVisitor<>(
                    Optional.ofNullable(cu.getStyle(TabsAndIndentsStyle.class)).orElse(IntelliJ.tabsAndIndents()),
                    Optional.ofNullable(cu.getStyle(WrappingAndBracesStyle.class)).orElse(IntelliJ.wrappingAndBraces()),
                    stopAfter
            ).visit(t, p);

            t = (JavaSourceFile) new TrailingCommaVisitor<>(IntelliJ.other().getUseTrailingComma()).visit(t, p);

            assert t != null;
            return t;
        }
        return (J) tree;
    }
}
