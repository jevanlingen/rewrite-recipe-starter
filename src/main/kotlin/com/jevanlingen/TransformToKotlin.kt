package com.jevanlingen

import com.jevanlingen.util.AutoFormatVisitorForWholeFile
import org.openrewrite.*
import org.openrewrite.Tree.randomId
import org.openrewrite.java.marker.ImplicitReturn
import org.openrewrite.java.tree.*
import org.openrewrite.java.tree.Flag.Static
import org.openrewrite.java.tree.J
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
import kotlin.io.path.Path

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
        acc.javaSources = acc.javaSources.filter { cu ->
            val sourcePath = cu.sourcePath.toString()
            val printOutputCapture = PrintOutputCapture(OutputCaptureContext())
            JavaAsKotlinPrinter().visit(cu, printOutputCapture)
            var kotlinString = printOutputCapture.getOut()

            val staticRegex = Regex("\\s*// STATIC_START\\n(.*?)\\n// STATIC_END\\n", RegexOption.DOT_MATCHES_ALL)
            val staticMembers = staticRegex.findAll(kotlinString).map { it.groupValues[1] }.toList()
            if (staticMembers.isNotEmpty()) {
                kotlinString = staticRegex.replace(kotlinString, "").replace(Regex("\n\\s*\n"), "\n")
                val lastBrace = kotlinString.lastIndexOf('}')
                if (lastBrace != -1) {
                    val staticContent = staticMembers.joinToString("\n\n") { it.trim() }
                    val companionObject = "\ncompanion object {$staticContent}\n"
                    kotlinString = kotlinString.substring(0, lastBrace) + companionObject + kotlinString.substring(lastBrace)
                }
            }

            try {
                kotlinSources.add(
                    KotlinParser.builder().build()
                        .parse(kotlinString)
                        .map { AutoFormatVisitorForWholeFile<ExecutionContext>().visitNonNull(it, ctx) }
                        .map<K.CompilationUnit> { it.cast() }
                        .findFirst()
                        .get()
                        .withSourcePath(
                            Path(
                                sourcePath
                                    .replace(".java", ".kt", true)
                                    .replace("java", "kotlin", true)
                            )
                        ))
                true
            } catch (e: Exception) {
                System.err.println("Could not transform '$sourcePath' because it contains patterns not (yet) implemented.")
                System.err.println("Problem: ${e.message}")
                false
            }
        }.toMutableList()

        return kotlinSources
    }

    override fun getVisitor(acc: Accumulator): TreeVisitor<*, ExecutionContext> {
        return object : TreeVisitor<Tree, ExecutionContext>() {
            override fun visit(tree: Tree?, ctx: ExecutionContext): Tree? {
                if (tree is J.CompilationUnit && tree in acc.javaSources) {
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
            override fun visitClassDeclaration(
                classDecl: J.ClassDeclaration,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                val modifiers = classDecl.modifiers.toMutableList()
                if (classDecl.kind == J.ClassDeclaration.Kind.Type.Enum) {
                    modifiers += J.Modifier(randomId(), Space.EMPTY, EMPTY, "enum ", LanguageExtension, emptyList())
                }
                return super.visitClassDeclaration(classDecl.withModifiers(modifiers), p)
            }

            override fun visitBlock(block: J.Block, p: PrintOutputCapture<OutputCaptureContext>): J {
                if (p.context.isInMethodBodyDeclarationsSingleExpressionFunction == block) {
                    var statement = block.statements.first()
                    if (statement is J.Return) {
                        statement = statement.withMarkers(statement.markers.add(ImplicitReturn(randomId())))
                    } else if (statement is J.VariableDeclarations ||
                        statement is J.Switch ||
                        statement is J.ForLoop ||
                        statement is J.ForEachLoop ||
                        statement is J.WhileLoop ||
                        statement is J.Assignment
                    ) {
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
                val isStatic = m.hasModifier(J.Modifier.Type.Static)
                if (isStatic) {
                    p.append("// STATIC_START\n")
                }

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

                p.context.isInMethodBodyDeclarationsSingleExpressionFunction =
                    if (isSingleExpressionFunction) method.body else null
                visit(method.body, p)
                p.context.isInMethodBodyDeclarationsSingleExpressionFunction = null
                afterSyntax(method, p)

                if (isStatic) {
                    p.append("\n// STATIC_END\n")
                }

                return method
            }

            override fun visitModifier(mod: J.Modifier, p: PrintOutputCapture<OutputCaptureContext>): J? {
                if (mod.type == J.Modifier.Type.Public || mod.type == J.Modifier.Type.Static) {
                    return null
                }
                return super.visitModifier(mod, p)
            }

            override fun visitPrimitive(primitive: J.Primitive, p: PrintOutputCapture<OutputCaptureContext>): J {
                val keyword = if (primitive.type == Void) "Unit" else primitive.type.name
                beforeSyntax(primitive, PRIMITIVE_PREFIX, p)
                p.append(keyword)
                afterSyntax(primitive, p)
                return primitive
            }

            override fun visitVariableDeclarations(
                multiVariable: J.VariableDeclarations,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                val isStatic = multiVariable.hasModifier(J.Modifier.Type.Static)
                if (isStatic) {
                    p.append("// STATIC_START\n")
                }

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

                    if (!isFinal && !p.context.isInMethodDeclarationsArguments && !p.context.isInForEach && !p.context.isInLambdaParameters) {
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

                    if (multiVariable.typeExpression != null && !p.context.isInForEach && variable.getElement().initializer == null) {
                        p.append(":")
                        visit(multiVariable.typeExpression!!.withPrefix<TypeTree>(Space.EMPTY), p)
                        if (variable.element.type !is JavaType.Primitive) {
                            p.append("?") // make every declaration type nullable
                        }
                        if (isStatic) {
                            p.append(" = null")
                        }
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

                if (isStatic) {
                    p.append("\n// STATIC_END\n")
                }
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

            override fun visitSwitch(switch_: J.Switch, p: PrintOutputCapture<OutputCaptureContext>): J {
                p.context.isInSwitch = true
                p.append("\n/* Switch statements don't translate directly to Kotlin’s `when` expression. Handle these cases manually to ensure correct fallthrough behavior.")
                val s = super.visitSwitch(switch_, p)
                p.append("*/")
                p.context.isInSwitch = false
                return s;
            }

            override fun visitSwitchExpression(
                switch_: J.SwitchExpression,
                p: PrintOutputCapture<OutputCaptureContext>
            ): J {
                beforeSyntax(switch_, SWITCH_EXPRESSION_PREFIX, p);
                p.append("when");
                visit(switch_.selector, p);
                visit(switch_.cases, p);
                afterSyntax(switch_, p);
                return switch_;
            }

            override fun visitCase(case_: J.Case, p: PrintOutputCapture<OutputCaptureContext>): J {
                if (p.context.isInSwitch) {
                    return super.visitCase(case_, p)
                }

                beforeSyntax(case_, CASE_PREFIX, p)
                var c = case_
                val elem = case_.caseLabels.first()
                if (elem is J.Identifier && elem.simpleName == "default") {
                    c = c.withCaseLabels(mutableListOf(elem.withSimpleName("else") as J))
                }
                visitContainer("", c.getPadding().caseLabels, JContainer.Location.CASE_LABEL, ",", "", p)
                if (case_.guard != null) {
                    p.append("if")
                    visit(case_.guard, p)
                }
                visitSpace(case_.getPadding().statements.before, CASE, p)
                p.append("->")
                visitStatements(
                    case_.getPadding().statements.getPadding().getElements(), JRightPadded.Location.CASE, p
                )
                if (case_.body is Statement) {
                    visitStatement(
                        case_.getPadding().body as JRightPadded<Statement>?,
                        JRightPadded.Location.CASE_BODY, p
                    )
                } else {
                    visitRightPadded(case_.getPadding().body, JRightPadded.Location.CASE_BODY, ";", p)
                }
                afterSyntax(case_, p)
                return case_
            }

            override fun visitForLoop(forLoop: J.ForLoop, p: PrintOutputCapture<OutputCaptureContext>): J {
                val ctrl = forLoop.control
                val body = forLoop.getPadding().body
                val bodyElement = body.element
                if (bodyElement !is J.Block) {
                    throw IllegalArgumentException("Cannot convert $ctrl yet!")
                }

                beforeSyntax(forLoop, FOR_PREFIX, p)
                visitRightPadded(ctrl.getPadding().init, JRightPadded.Location.FOR_INIT, "", p)
                p.append("\nwhile")
                p.append('(')
                visitRightPadded(ctrl.getPadding().condition, JRightPadded.Location.FOR_CONDITION, "", p)
                p.append(')')
                val ctrlUpdate = ctrl.getPadding().update.map<JRightPadded<Statement>, Statement> {
                    it.element.withPrefix(
                        Space.build(
                            "\n",
                            emptyList()
                        )
                    )
                }
                visitStatement(
                    body.withElement(bodyElement.withStatements(bodyElement.statements + ctrlUpdate)),
                    JRightPadded.Location.FOR_BODY, p
                )
                afterSyntax(forLoop, p)
                return forLoop
            }

            override fun visitForEachLoop(forEachLoop: J.ForEachLoop, p: PrintOutputCapture<OutputCaptureContext>): J {
                p.context.isInForEach = true
                beforeSyntax(forEachLoop, FOR_EACH_LOOP_PREFIX, p)
                p.append("for")
                val ctrl = forEachLoop.control
                visitSpace(ctrl.prefix, FOR_EACH_CONTROL_PREFIX, p)
                p.append('(')
                visitRightPadded(ctrl.getPadding().variable, JRightPadded.Location.FOREACH_VARIABLE, "in", p)
                visitRightPadded(ctrl.getPadding().iterable, JRightPadded.Location.FOREACH_ITERABLE, "!!", p)
                p.append(')')
                visitStatement(forEachLoop.getPadding().body, JRightPadded.Location.FOR_BODY, p)
                afterSyntax(forEachLoop, p)
                p.context.isInForEach = false
                return forEachLoop
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
    var isInForEach: Boolean = false,
    var isInSwitch: Boolean = false,
    var isInMethodBodyDeclarationsSingleExpressionFunction: Any? = null,
    var isInLambdaParameters: Boolean = false,
)

data class Accumulator(var javaSources: MutableList<J.CompilationUnit>)


