package com.jevanlingen

import com.jevanlingen.util.AutoFormatVisitorForWholeFile
import org.openrewrite.*
import org.openrewrite.Tree.randomId
import org.openrewrite.java.marker.ImplicitReturn
import org.openrewrite.java.tree.*
import org.openrewrite.java.tree.Flag.Static
import org.openrewrite.java.tree.J.Modifier.Type.LanguageExtension
import org.openrewrite.java.tree.JContainer.Location.TYPE_PARAMETERS
import org.openrewrite.java.tree.JRightPadded.Location.METHOD_INVOCATION_ARGUMENT
import org.openrewrite.java.tree.JRightPadded.Location.METHOD_SELECT
import org.openrewrite.java.tree.JavaType.Primitive.Void
import org.openrewrite.java.tree.Space.Location.*
import org.openrewrite.java.tree.Space.SINGLE_SPACE
import org.openrewrite.kotlin.KotlinParser
import org.openrewrite.kotlin.internal.KotlinPrinter
import org.openrewrite.kotlin.marker.Extension
import org.openrewrite.kotlin.marker.OmitBraces
import org.openrewrite.kotlin.marker.Semicolon
import org.openrewrite.kotlin.marker.SingleExpressionBlock
import org.openrewrite.kotlin.tree.K
import org.openrewrite.marker.Marker
import org.openrewrite.marker.Markers.EMPTY

class TransformToKotlin : ScanningRecipe<Accumulator>() {
    override fun getDisplayName() = "Java to Kotlin transformer"

    override fun getDescription() = "Replaces Java files with Kotlin equivalent."

    override fun getInitialValue(ctx: ExecutionContext) = Accumulator(mutableListOf())

    override fun getScanner(acc: Accumulator): TreeVisitor<*, ExecutionContext> {
        return object : TreeVisitor<Tree, ExecutionContext>() {
            override fun visit(tree: Tree?, ctx: ExecutionContext): Tree? {
                if (tree is J.CompilationUnit) {
                    acc.javaSources.add(tree)
                }
                return tree
            }
        }
    }

    override fun generate(acc: Accumulator, ctx: ExecutionContext): Collection<SourceFile> {
        val kotlinSources = mutableListOf<K.CompilationUnit>()
        acc.javaSources.forEach { cu ->
            val printOutputCapture = PrintOutputCapture(OutputCaptureContext())
            JavaAsKotlinPrinter().visit(cu, printOutputCapture)
            kotlinSources.add(
                KotlinParser.builder().build()
                .parse(printOutputCapture.getOut())
                .map { AutoFormatVisitorForWholeFile<ExecutionContext>().visitNonNull(it, ctx) }
                .map { it.cast<K.CompilationUnit>() }
                .findFirst()
                .get())
        }

        return kotlinSources
    }

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> {
        return object : TreeVisitor<Tree, ExecutionContext>() {
            override fun visit(tree: Tree?, ctx: ExecutionContext): Tree? {
                if (tree is J.CompilationUnit) {
                    return null;
                }
                return tree
            }
        }
    }

    private class JavaAsKotlinPrinter : KotlinPrinter<OutputCaptureContext>() {
        override fun delegate(): ExtendedKotlinJavaPrinter = ExtendedKotlinJavaPrinter(this)

        class ExtendedKotlinJavaPrinter(kp: KotlinPrinter<OutputCaptureContext>) :
            KotlinJavaPrinter<OutputCaptureContext>(kp) {
            override fun visitBlock(block: J.Block, p: PrintOutputCapture<OutputCaptureContext>): J {
                if (p.context.isInMethodBodyDeclarationsSingleExpressionFunction) {
                    var statement = block.statements.first()
                    if (statement is J.Return) {
                        statement = statement.withMarkers(statement.markers.add(ImplicitReturn(randomId())))
                    } else if (statement is J.VariableDeclarations) {
                        // exception when we can't turn it into a single expression function after all
                        return super.visitBlock(block, p)
                    }

                    val blockWithMarkers = block.withStatements(mutableListOf(statement))
                        .withMarkers(block.markers.add(SingleExpressionBlock(randomId())).add(OmitBraces(randomId())))
                        .withEnd(Space.EMPTY)
                    return super.visitBlock(blockWithMarkers, p)
                }
                return super.visitBlock(block, p)
            }

            override fun visitMethodDeclaration(
                m: J.MethodDeclaration,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                val modifiers = m.modifiers.toMutableList()
                if (!m.isConstructor) {
                    modifiers += J.Modifier(randomId(), SINGLE_SPACE, EMPTY, "fun", LanguageExtension, emptyList())
                }
                val method = m.withModifiers(modifiers)
                val isSingleExpressionFunction = method.body?.statements?.size == 1

                beforeSyntax(method, METHOD_DECLARATION_PREFIX, p)
                visit(method.leadingAnnotations, p)
                method.modifiers.forEach { visitModifier(it, p) }

                method.annotations.typeParameters?.let {
                    if (method.modifiers.isNotEmpty()) {
                        p.append(" ")
                    }

                    visit(it.annotations, p)
                    visitSpace(it.prefix, Space.Location.TYPE_PARAMETERS, p)
                    visitMarkers(it.markers, p)
                    p.append("<")
                    visitRightPadded(
                        it.padding.typeParameters,
                        JRightPadded.Location.TYPE_PARAMETER,
                        ",",
                        p
                    )
                    p.append(">")
                }

                visit(method.annotations.name.annotations, p)
                visit(if (m.isConstructor) method.name.withSimpleName("constructor") else method.name, p)

                val params = method.padding.parameters
                beforeSyntax(
                    params.before,
                    params.markers,
                    JContainer.Location.METHOD_DECLARATION_PARAMETERS.beforeLocation,
                    p
                )
                p.append("(")
                p.context.isInMethodDeclarationsArguments = true
                val elements = params.padding.getElements()
                for (i in elements.indices) {
                    val element = elements[i]
                    val suffix = if (i == elements.size - 1) "" else ","
                    visit((element as JRightPadded<out J>).getElement(), p)
                    visitSpace(
                        element.after,
                        JContainer.Location.METHOD_DECLARATION_PARAMETERS.elementLocation.afterLocation,
                        p
                    )
                    visitMarkers(element.markers, p)
                    p.append(suffix)
                }

                afterSyntax(params.markers, p)
                p.context.isInMethodDeclarationsArguments = false
                p.append(")")

                method.returnTypeExpression?.let {
                    if (!(it is J.Primitive && it.type == Void) && !isSingleExpressionFunction) {
                        p.append(":")
                        visit(it, p)
                        if (it !is J.Primitive) {
                            p.append("?") // make every return type nullable
                        }
                    }
                }

                p.context.isInMethodBodyDeclarationsSingleExpressionFunction = isSingleExpressionFunction
                visit(method.body, p)
                p.context.isInMethodBodyDeclarationsSingleExpressionFunction = false
                afterSyntax(method, p)
                return method
            }

            override fun visitModifier(mod: J.Modifier, p: PrintOutputCapture<OutputCaptureContext>): J? {
                if (mod.type == J.Modifier.Type.Public) {
                    return null
                }
                return super.visitModifier(mod, p)
            }

            override fun visitPrimitive(primitive: J.Primitive, p: PrintOutputCapture<OutputCaptureContext>): J {
                val keyword = if (primitive.type == Void) "Unit" else primitive.type.toString()
                beforeSyntax(primitive, PRIMITIVE_PREFIX, p)
                p.append(keyword)
                afterSyntax(primitive, p)
                return primitive
            }

            override fun visitVariableDeclarations(
                multiVariable: J.VariableDeclarations,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                beforeSyntax(multiVariable, VARIABLE_DECLARATIONS_PREFIX, p)

                visit(multiVariable.leadingAnnotations, p)
                var isFinal = false
                for (m in multiVariable.modifiers) {
                    visitModifier(m, p)
                    if (m.type == J.Modifier.Type.Final) {
                        isFinal = true
                        p.append("val")
                    }
                }

                val containsTypeReceiver = multiVariable.markers.findFirst(Extension::class.java).isPresent
                val variables = multiVariable.padding.variables

                for (i in variables.indices) {
                    val variable = variables.get(i)

                    if (!isFinal && !p.context.isInMethodDeclarationsArguments && !p.context.isInLambdaParameters) {
                        if (multiVariable.typeExpression != null) {
                            p.append(multiVariable.typeExpression!!.prefix.whitespace)
                        }
                        p.append("var")
                    }

                    beforeSyntax(variable.getElement(), VARIABLE_PREFIX, p)
                    if (variables.size > 1 && !containsTypeReceiver && i == 0) {
                        p.append("(")
                    }

                    visit(variable.getElement().getName(), p)
                    visitSpace(variable.after, VARIABLE_INITIALIZER, p)

                    if (multiVariable.typeExpression != null && variable.getElement().initializer == null) {
                        p.append(":")
                        visit(multiVariable.typeExpression!!.withPrefix<TypeTree>(Space.EMPTY), p)
                        p.append("?") // make every declaration type nullable
                    }

                    variable.getElement().padding.initializer?.let {
                        visitSpace(it.before, VARIABLE_INITIALIZER, p)
                    }

                    variable.getElement().initializer?.let {
                        p.append("=")
                    }

                    visit(variable.getElement().initializer, p)

                    if (i < variables.size - 1) {
                        p.append(",")
                    } else if (variables.size > 1 && !containsTypeReceiver) {
                        p.append(")")
                    }

                    variable.markers.findFirst(Semicolon::class.java).ifPresent { visitMarker<Marker>(it, p) }
                }

                afterSyntax(multiVariable, p)
                return multiVariable
            }

            override fun visitMethodInvocation(
                method: J.MethodInvocation,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                beforeSyntax(method, METHOD_INVOCATION_PREFIX, p)

                visitRightPadded(method.padding.select, METHOD_SELECT, p)
                if (method.select != null) {
                    // In Java, the return type will always be: `T | null` for non-static method, so to be sure, use the null safe operator if method call is not static
                    if (method.methodType?.hasFlags(Static) != true) {
                        p.append("?")
                    }
                    p.append(".")
                }

                visit(method.name, p)
                visitContainer(
                    "<",
                    method.padding.typeParameters,
                    TYPE_PARAMETERS,
                    ",",
                    ">",
                    p
                )

                visitArgumentsContainer(
                    method.padding.arguments,
                    METHOD_INVOCATION_ARGUMENTS,
                    p
                )

                afterSyntax(method, p)
                return method
            }

            override fun visitLambdaParameters(
                parameters: J.Lambda.Parameters,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                p.context.isInLambdaParameters = true
                val visitLambdaParameters = super.visitLambdaParameters(parameters, p)
                p.context.isInLambdaParameters = false
                return visitLambdaParameters
            }

            override fun visitTypeCast(typeCast: J.TypeCast, p: PrintOutputCapture<OutputCaptureContext>): J {
                var tc = typeCast
                if (tc.clazz.prefix == Space.EMPTY) {
                    tc = tc.withClazz(tc.clazz.withPrefix(SINGLE_SPACE))
                }
                if (tc.clazz.tree.prefix == Space.EMPTY) {
                    tc = tc.withClazz(tc.clazz.withTree(tc.clazz.tree.withPrefix(SINGLE_SPACE)))
                }
                val j = super.visitTypeCast(tc, p)
                p.append("?") // make every cast type nullable
                return j;
            }

            private fun visitArgumentsContainer(
                argContainer: JContainer<Expression>,
                argsLocation: Space.Location,
                p: PrintOutputCapture<OutputCaptureContext>
            ) {
                visitSpace(argContainer.getBefore(), argsLocation, p)
                val args = argContainer.padding.getElements()
                p.append('(')
                for (i in 0..<args.size) {
                    if (i > 0) {
                        p.append(',')
                    }
                    visitRightPadded(args[i], METHOD_INVOCATION_ARGUMENT, p)
                }
                p.append(')')
            }
        }
    }
}

data class OutputCaptureContext(
    var isInMethodDeclarationsArguments: Boolean = false,
    var isInMethodBodyDeclarationsSingleExpressionFunction: Boolean = false,
    var isInLambdaParameters: Boolean = false,
)

data class Accumulator(val javaSources: MutableList<J.CompilationUnit>)


