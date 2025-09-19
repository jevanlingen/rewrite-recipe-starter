import com.jevanlingen.TransformToKotlin
import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
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

    @Test
    fun `constructor`() {
        rewriteRunJavaToKotlin(
            """
              class A {
                  A() {
                  }
              }
              """.trimIndent(),
            """
              class A {
                  constructor() {
                  }
              }
              """.trimIndent(),
        )
    }

    @Test
    fun `generics`() {
        rewriteRunJavaToKotlin(
            """
            import java.util.List;

            class A<T> {
                private List<T> list;

                <U> void b(U u) {
                }
            }
            """.trimIndent(),
            """
            import java.util.List;

            class A<T> {
                private var list: List<T>?

                fun <U> b(u: U?) {
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `annotations`() {
        rewriteRunJavaToKotlin(
            """
            @Deprecated
            class A {
                @Deprecated
                String a;

                @Deprecated
                void b() {
                }
            }
            """.trimIndent(),
            """
            @Deprecated
            class A {
                @Deprecated
                private var a: String?

                @Deprecated
                fun b() {
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `type cast`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                void b(Object o) {
                    String s = (String) o;
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b(o: Any?) {
                    val s = o as String?
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `switch`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                void b(int i) {
                    switch (i) {
                        case 0:
                            System.out.println("zero");
                            break;
                        case 1:
                            System.out.println("one");
                            break;
                        default:
                            System.out.println("other");
                    }
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b(i: Int) {
                    when (i) {
                        0 -> System.out.println("zero")
                        1 -> System.out.println("one")
                        else -> System.out.println("other")
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `while loop`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                void b() {
                    int i = 0;
                    while (i < 10) {
                        System.out.println(i);
                        i++;
                    }
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b() {
                    var i = 0
                    while (i < 10) {
                        System.out.println(i)
                        i++
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `if-else`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                void b(int i) {
                    if (i == 0) {
                        System.out.println("zero");
                    } else if (i == 1) {
                        System.out.println("one");
                    } else {
                        System.out.println("other");
                    }
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b(i: Int) {
                    if (i == 0) {
                        System.out?.println("zero")
                    } else if (i == 1) {
                        System.out?.println("one")
                    } else {
                        System.out?.println("other")
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `for loop`() {
        rewriteRunJavaToKotlin(
            """
            import java.util.List;

            class A {
                void b(List<String> list) {
                    for (String s : list) {
                        System.out.println(s);
                    }
                }
            }
            """.trimIndent(),
            """
            import java.util.List;

            class A {
                fun b(list: List<String>?) {
                    for (s in list) {
                        System.out.println(s)
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `static members`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                static String b;
                static void c() {
                }
            }
            """.trimIndent(),
            """
            class A {
                companion object {
                    var b: String?
                    fun c() {
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `interface`() {
        rewriteRunJavaToKotlin(
            """
            interface A {
                void b();
            }
            """.trimIndent(),
            """
            interface A {
                fun b()
            }
            """.trimIndent()
        )
    }

    @Test
    fun `enum`() {
        rewriteRunJavaToKotlin(
            """
            enum A {
                B, C
            }
            """.trimIndent(),
            """
            enum class A {
                B, C
            }
            """.trimIndent()
        )
    }

    @Test
    fun `constructor with properties`() {
        rewriteRunJavaToKotlin(
            """
              class A {
                  private final String prop;
                  A(String prop) {
                      this.prop = prop;
                  }
              }
              """.trimIndent(),
            """
              class A(private val prop: String?) {
              }
              """.trimIndent(),
        )
    }

    private fun rewriteRunJavaToKotlin(@Language("java") before: String, @Language("kotlin") after: String) {
        rewriteRun(
            { spec ->
                spec.afterRecipe {
                    Assertions.assertThat(it.changeset.allResults.first().after!!.printAll()).isEqualTo(after)
                }
            },
            org.openrewrite.java.Assertions.java(before, null as String?)
        )
    }
}
