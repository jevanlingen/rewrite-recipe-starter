package com.jevanlingen

import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.openrewrite.java.Assertions.java
import org.openrewrite.test.RecipeSpec
import org.openrewrite.test.RewriteTest

internal class TransformToKotlinTest : RewriteTest {
    override fun defaults(spec: RecipeSpec) {
        spec.recipe(TransformToKotlin())
    }

    @Test
    fun `class`() {
        rewriteRunJavaToKotlin(
            """
              class A {}
              """.trimIndent(),
            """
              class A {}
              """.trimIndent()
        )
    }

    @Test
    fun variableDeclarations() {
        rewriteRunJavaToKotlin(
            """
              import java.util.ArrayList;
              import java.util.List;
              
              class A {
                  String a;
                  List<String> c = new ArrayList<String>();
                  // List<String> d = new ArrayList<>();  <- no support for diamond operator yet
                  String e = "x";
                  var f = "x";
                  final var g = "x";
                  final String h = "x";
              }
              """.trimIndent(),
            """
              import java.util.ArrayList;
              import java.util.List;
              
              class A {
                  var a: String?
                  var c = ArrayList<String>()
                  // List<String> d = new ArrayList<>();  <- no support for diamond operator yet
                  var e = "x"
                  var f = "x"
                  val g = "x"
                  val h = "x"
              }
              """.trimIndent()
        )
    }

    @Test
    fun functions() {
        rewriteRunJavaToKotlin(
            """
              import java.util.Locale;
              
              public class A {
                  public String f(String a, String b) {
                      if (a != null && b != null) {
                          return a + b;
                      }
                      return null;
                  }
              
                  public String singleExpressionFunction() {
                      return "A";
                  }
              
                  public String singleExpressionFunctionReturnsNull() {
                      return null;
                  }
              
                  private void methodInvocation() {
                      singleExpressionFunction().toLowerCase(Locale.ROOT);
                      singleExpressionFunctionReturnsNull().toLowerCase(Locale.ROOT);
                  }
                  
                  private void singleExpressionFunctionMethodInvocation() {
                      singleExpressionFunction();
                  }
                  
                  private void noSingleExpressionFunction() {
                      String a;
                  }
              }
              """.trimIndent(),
            """
              import java.util.Locale;
              
              class A {
                  fun f(a: String?, b: String?): String? {
                      if (a != null && b != null) {
                          return a + b
                      }
                      return null
                  }
              
                  fun singleExpressionFunction() =
                      "A"
              
                  fun singleExpressionFunctionReturnsNull() =
                      null
              
                  private fun methodInvocation() {
                      singleExpressionFunction()?.toLowerCase(Locale.ROOT)
                      singleExpressionFunctionReturnsNull()?.toLowerCase(Locale.ROOT)
                  }
              
                  private fun singleExpressionFunctionMethodInvocation() =
                      singleExpressionFunction()

                  private fun noSingleExpressionFunction() {
                      var a: String?
                  }
              }
              """.trimIndent()
        )
    }

    @Test
    fun lambda() {
        rewriteRunJavaToKotlin(
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Stream;
              
              class A {
                  List<String> x = Stream.of(1, 2, null).filter(Objects::nonNull).map(x -> x.toString() + "..").toList();
              }
              """.trimIndent(),
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.stream.Stream;
              
              class A {
                  var x = Stream.of(1, 2, null)?.filter(Objects::nonNull)?.map({ x -> x?.toString() + ".." })?.toList()
              }
              """.trimIndent()
        )
    }

    private fun rewriteRunJavaToKotlin(@Language("java") before: String, @Language("kotlin") after: String) {
        rewriteRun(
            { spec ->
                spec.afterRecipe {
                    Assertions.assertThat(it.changeset.allResults.first().after!!.printAll()).isEqualTo(after)
                }
            },
            java(before, null as String?)
        )
    }
}
