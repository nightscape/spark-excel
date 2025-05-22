ifdef
=====

ifdef is an **experimental** project to implement conditional compilation in Scala.
Conditional compilation is a concept of compiling some region of source code
only under certain conditions. Programming languages like C and Rust implement it
via preprocessor and macro.

For example, in Rust, you can include unit tests in the same source code where
the traits and functions are defined using the [`#[cfg(test)]`][rust] annotation.
Currently ifdef is a combination of an annotation library, a compiler plugin, and an sbt plugin
that implements similar mechanism.

setup
-----

```scala
addSbtPlugin("com.eed3si9n.ifdef" % "sbt-ifdef" % "x.y.z")
```

usage
-----

First we create meta declarations using the `ifDefDeclarations` setting.
This can be checked at compile-time using the `@ifdef(...)` annotation.
sbt-ifdef defines `compile`, `test`, and `scalaBinaryVersion:3` declarations out-of-box.

### test in the same source code

During the `Test / compile`, sbt-ifdef will pass in the `test` declaration,
but not during `Compile / compile`.

```scala
package example

import com.eed3si9n.ifdef.ifdef

class A {
  def foo: Int = 42
}

@ifdef("test")
class ATest extends munit.FunSuite {
  test("hello") {
    val actual = new A().foo
    val expected = 42
    assertEquals(actual, expected)
  }
}
```

The above code combines the `Compile` configuration and the `Test` configuration
into one source code.

### Scala cross building

sbt-ifdef also defines `scalaBinaryVersion:<sbv>` lifting the `scalaBinaryVersion` setting
into the `ifDefDeclarations` setting.
This allows Scala cross building using a single source code.

```scala
package example

import com.eed3si9n.ifdef.ifdef

class A {
  def foo: Int = 42

  @ifdef("scalaBinaryVersion:2.13")
  def bar: Int = 1

  @ifdef("scalaBinaryVersion:3")
  def bar: Int = 2
}
```

In the above, `bar` method will return `2` on Scala 3.x:

```scala
Welcome to Scala 3.6.4 (1.8.0_402, Java OpenJDK 64-Bit Server VM).
Type in expressions for evaluation. Or try :help.

scala> example.A().bar
val res0: Int = 2
```

### custom declarations

You can add custom declarations in your build.sbt as follows:

```scala
// Add a custom definition for early access features
ThisBuild / ifDefDeclarations += "eap"
```

Then use them in your code:

```scala
// This method will only be available when "eap" is defined
@ifdef("eap")
def earlyAccessFeature(): Unit = {
  // Implementation of early access feature
}
```

license
-------
ifdef is released under Apache License Version 2.0.

references
----------

- [ifdef in Scala via pre-typer processing](https://eed3si9n.com/ifdef-in-scala-via-pre-typer-processing/), 2023
- [ifdef macro in Scala](https://eed3si9n.com/ifdef-macro-in-scala/), 2023
- [Scala Preprocessor / Conditional Compilation scala-dev#640](https://github.com/scala/scala-dev/issues/640), 2019, Stefan Zeiger
- [ThoughtWorksInc/enableIf.scala](https://github.com/ThoughtWorksInc/enableIf.scala), 2016

  [rust]: https://doc.rust-lang.org/book/ch11-03-test-organization.html
