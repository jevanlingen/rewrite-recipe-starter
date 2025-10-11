package com.jevanlingen

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
              public abstract class A extends Object implements AutoCloseable {
                  @Override
                  public void close() throws Exception {
                  }
              }
              """.trimIndent(),
            """
              abstract class A : Object, AutoCloseable {
                  override fun close() {
                  }
              }
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
                  List<String> d = new ArrayList<>();
                  String e = "x";
                  var f = "x";
                  final var g = "x";
                  final String h = "x";
                  String[][] data = {
                      {"a", "b"},
                      {"c", "d"}
                  };
                  
                  void test() {
                      String b;
                  }
              }
              """.trimIndent(),
            """
              import java.util.ArrayList;
              import java.util.List;
              
              class A {
                  var a: String? = null
                  var c = ArrayList<String>()
                  var d = ArrayList<String>()
                  var e = "x"
                  var f = "x"
                  val g = "x"
                  val h = "x"
                  var data = arrayOf(
                      arrayOf("a", "b"),
                      arrayOf("c", "d")
                  )
              
                  fun test() {
                      var b: String?
                  }
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
              import java.util.Optional;
              import java.util.stream.Stream;
              
              class A {
                  List<String> x = Stream.of(1, 2, null).filter(Objects::nonNull).map(x -> x.toString() + "..").toList();
                  Integer y = Optional.of(1).orElseThrow(() -> new IllegalArgumentException("x"));
              }
              """.trimIndent(),
            """
              import java.util.List;
              import java.util.Objects;
              import java.util.Optional;
              import java.util.stream.Stream;
              
              class A {
                  var x = Stream.of(1, 2, null)?.filter(Objects::nonNull)?.map({ x -> x?.toString() + ".." })?.toList()
                  var y = Optional.of(1)?.orElseThrow({ IllegalArgumentException("x")})
              }
              """.trimIndent()
        )
    }

    @Test
    fun `constructor`() {
        // TODO: convert to primary constructor can possible be done at a later stage...
        rewriteRunJavaToKotlin(
            """
              class A {
                  private final String prop;
                  private String prop2; // no constructor prop
              
                  A(String prop) {
                      this.prop = prop;
                  }
              
                  public String getProp() {
                      return prop;
                  }
              }
              """.trimIndent(),
            """
              class A {
                  private val prop: String?
                  private var prop2: String? = null // no constructor prop
              
                  constructor(prop: String?) {
                      this.prop = prop
                  }
              
                  fun getProp() =
                      prop
              }
              """.trimIndent(),
        )
    }

    @Test
    fun `generics`() {
        rewriteRunJavaToKotlin(
            """
            import java.util.List;
            import java.util.function.Function;

            class A<T> {
                private A<A<A<?>>> a = new A<>();
                private List<T> list;

                <U extends Object> void b(U u) {
                }
                
                <A, B> void map(Function<? super A, ? extends B> mapper) {
                }
                
                void supports(Class<?> clazz) {
                }
            }
            """.trimIndent(),
            """
            import java.util.List;
            import java.util.function.Function;

            class A<T> {
                private var a = A<A<A<*>>>()
                private var list: List<T>? = null

                fun <U : Object> b(u: U?) {
                }
            
                fun <A, B> map(mapper: Function<in A, out B>?) {
                }
           
                fun supports(clazz: Class<*>?) {
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
                var a: String? = null
            
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
                fun b(o: Object?) {
                    var s = o as String?
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun switch() {
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
                    /* Switch statements don't translate directly to Kotlin’s `when` expression. Handle these cases manually to ensure correct fallthrough behavior.
                        switch (i) {
                            case 0:
                                System.out?.println("zero")
                                break
                            case 1:
                                System.out?.println("one")
                                break
                            default:
                                System.out?.println("other")
                        }*/
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `switch expression`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                String b(int i) {
                    return switch (i) {
                        case 0 -> "zero";
                        case 1 -> "one";
                        default -> "other";
                    };
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b(i: Int) =
                    when (i) {
                        0 -> "zero";
                        1 -> "one";
                        else -> "other";
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
                fun b(i: Int) =
                    if (i == 0) {
                        System.out?.println("zero")
                    } else if (i == 1) {
                        System.out?.println("one")
                    } else {
                        System.out?.println("other")
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
    fun `for loop`() {
        rewriteRunJavaToKotlin(
            """
            class A {
                void b() {
                    for (int i = 1; i < 5; i++) {
                        System.out.println(i);
                    }
                }
            }
            """.trimIndent(),
            """
            class A {
                fun b() {
                    var i = 1
                    while (i < 5) {
                        System.out?.println(i)
                        i++
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `enhanced for loop`() {
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
                    for (s in list!!) {
                        System.out?.println(s)
                    }
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `static members`() {
        // TODO inner classes with static members are not yet supported
        rewriteRunJavaToKotlin(
            """
            class A {
                static String b;
                
                void nonStatic() {
                }
                
                static void c() {
                }
            }
            """.trimIndent(),
            """
            class A {
                fun nonStatic() {
                }
            
                companion object {
                    var b: String? = null
            
                    fun c() {
                    }
                }
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
    fun dotClass() {
        rewriteRunJavaToKotlin(
            """
            class A {
                Class<?> x = A.class;
            }
            """.trimIndent(),
            """
            class A {
                var x = A::class.java
            }
            """.trimIndent()
        )
    }

    @Test
    fun propertiesInsteadOfMethodCalls() {
        rewriteRunJavaToKotlin(
            """
            import java.util.ArrayList;
            import java.util.List;
            
            class A {
                int x = List.of().size();
                int y = new ArrayList<String>().size();
                int z = "string".length();
            }
            """.trimIndent(),
            """
            import java.util.ArrayList;
            import java.util.List;
            
            class A {
                var x = List.of()?.size
                var y = ArrayList<String>()?.size
                var z = "string".length
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
            org.openrewrite.java.Assertions.java(before, null as String?)
        )
    }
}
