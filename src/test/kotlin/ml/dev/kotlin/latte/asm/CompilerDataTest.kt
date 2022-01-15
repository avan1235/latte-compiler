package ml.dev.kotlin.latte.asm

import ml.dev.kotlin.latte.runCompiler
import ml.dev.kotlin.latte.util.dir
import ml.dev.kotlin.latte.util.invoke
import ml.dev.kotlin.latte.util.withExtension
import ml.dev.kotlin.latte.util.zeroCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.nio.file.Files
import java.util.stream.Stream

internal class CompilerDataTest {

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should work with arithmetic on different locations`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        int x = f(43, 24);
        printInt(x);
        return 0;
      }
      int f(int a, int b) {
        int x = 7;
        int y;
        int m = 1;
        int n = 2;
        int o = 3;
        int p = 4;
        int q = 5;
        int r = 6;
        int s = 7;
        int t = 8;

        // DIV

        a = 42;
        a = a / b;
        printInt(a);
        a = 42;
        a = a / x;
        printInt(a);
        a = 42;
        a = a / 3;
        printInt(a);

        y = 312;
        y = y / b;
        printInt(y);
        y = 312;
        y = y / x;
        printInt(y);
        y = 312;
        y = y / 3;
        printInt(y);

        a = 42;
        printInt(85 / a);
        printInt(85 / x);
        printInt(85 / 3);

        // MOD

        a = 42;
        a = a % b;
        printInt(a);
        a = 42;
        a = a % x;
        printInt(a);
        a = 42;
        a = a % 3;
        printInt(a);

        y = 312;
        y = y % b;
        printInt(y);
        y = 312;
        y = y % x;
        printInt(y);
        y = 312;
        y = y % 3;
        printInt(y);

        a = 42;
        printInt(85 % a);
        printInt(85 % x);
        printInt(85 % 3);

        // PLUS

        a = 42;
        a = a + b;
        printInt(a);
        a = 42;
        a = a + x;
        printInt(a);
        a = 42;
        a = a + 3;
        printInt(a);

        y = 312;
        y = y + b;
        printInt(y);
        y = 312;
        y = y + x;
        printInt(y);
        y = 312;
        y = y + 3;
        printInt(y);

        a = 42;
        printInt(85 + a);
        printInt(85 + x);
        printInt(85 + 3);

        // MINUS

        a = 42;
        a = a - b;
        printInt(a);
        a = 42;
        a = a - x;
        printInt(a);
        a = 42;
        a = a - 3;
        printInt(a);

        y = 312;
        y = y - b;
        printInt(y);
        y = 312;
        y = y - x;
        printInt(y);
        y = 312;
        y = y - 3;
        printInt(y);

        a = 42;
        printInt(85 - a);
        printInt(85 - x);
        printInt(85 - 3);

        // TIMES

        a = 42;
        a = a * b;
        printInt(a);
        a = 42;
        a = a * x;
        printInt(a);
        a = 42;
        a = a * 3;
        printInt(a);

        y = 312;
        y = y * b;
        printInt(y);
        y = 312;
        y = y * x;
        printInt(y);
        y = 312;
        y = y * 3;
        printInt(y);

        a = 42;
        printInt(85 * a);
        printInt(85 * x);
        printInt(85 * 3);

        return m + n + o + p + q + r + s + t;
      }
      """,
      output = """
      1
      6
      14
      13
      44
      104
      2
      12
      28
      18
      0
      0
      0
      4
      0
      1
      1
      1
      66
      49
      45
      336
      319
      315
      127
      92
      88
      18
      35
      39
      288
      305
      309
      43
      78
      82
      1008
      294
      126
      7488
      2184
      936
      3570
      595
      255
      36

      """
    )

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should work with unary op on different locations`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        f(42);
        return 0;
      }
      void f(int a) {
        int x = 2;
        printInt(a);
        printInt(x);
        a++;
        x++;
        printInt(a);
        printInt(x);
        a--;
        x--;
        printInt(a);
        printInt(x);
      }
      """,
      output = """
      42
      2
      43
      3
      42
      2

      """
    )

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should work with different references to class fields and methods`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        BTree b = new BTree;
        b.value = 42;
        b.insert(24);
        b.insert(66);
        b.insert(12);
        b.insert(-24);
        b.insert(99);
        b.insert(67);
        b.print();

        BTree c = new RevBTree;
        c.value = 42;
        c.insert(24);
        c.insert(66);
        c.insert(12);
        c.insert(-24);
        c.insert(99);
        c.insert(67);
        c.print();

        return 0;
      }
      class BTree {
        BTree left;
        BTree right;
        int value;

        void insert(int value) {
          if (value <= self.value) {
            if (left == (BTree) null) {
              left = create(value);
            } else {
              left.insert(value);
            }
          } else {
            if (self.right == (BTree) null) {
              self.right = self.create(value);
            } else {
              self.right.insert(value);
            }
          }
        }

        BTree create(int value) {
          BTree b = new BTree;
          b.value = value;
          return b;
        }

        void print() {
          if (self.left != (BTree) null) self.left.print();
          printInt(value);
          if (right != (BTree) null) right.print();
        }
      }

      class RevBTree extends BTree {

        RevBTree create(int value) {
          RevBTree b = new RevBTree;
          b.value = value;
          return b;
        }

        void print() {
          if (right != (BTree) null) right.print();
          printInt(value);
          if (self.left != (BTree) null) self.left.print();
        }
      }
      """,
      output = """
      -24
      12
      24
      42
      66
      67
      99
      99
      67
      66
      42
      24
      12
      -24

      """
    )

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should resolve functions at first and methods later`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        A a = new A;
        a.test();

        return 0;
      }
      class A {
        void test() {
          printInt(f());
          printInt(g());
          printInt(self.f());
          printInt(self.g());
        }
        int f() {
          return 42;
        }
        int g() {
          return 24;
        }
      }
      int f() {
        return 11;
      }
      """,
      output = """
      11
      24
      42
      24

      """
    )

  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should resolve variables at first and fields later`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        A a = new A;
        a.x = 42;
        a.test();

        return 0;
      }
      class A {
        int x;
        void test() {
          int y = 11;
          printInt(x);
          printInt(y);
          {
            int x = 24;
            printInt(x);
            printInt(y);
          }
          printInt(x);
          printInt(y);
        }
      }
      """,
      output = """
      42
      11
      24
      11
      42
      11

      """
    )


  @ParameterizedTest
  @MethodSource("allocatorsProvider")
  fun `should allow to access class variables with self`(alloc: TestAllocator) =
    testCompilerWithAllocatorStrategy(
      alloc,
      program = """
      int main() {
        A a = new A;
        a.x = 33;
        a.a = new B;
        a.a.x = 11;
        a.test();
        a.a.test();

        return 0;
      }
      class A {
        int x;
        A a;
        void test() {
          printString("A test");
          printInt(x);
          int x = 42;
          printInt(x);
          printInt(self.x);
          printInt(a.x);
          printInt(self.a.x);
        }
      }
      class B extends A {
        void test() {
          printString("B test");
        }
      }
      """,
      output = """
      A test
      33
      42
      33
      11
      11
      B test

      """
    )

  companion object {
    @JvmStatic
    fun allocatorsProvider(): Stream<TestAllocator> = Stream.of(*TestAllocator.values())
  }
}

private fun testCompilerWithAllocatorStrategy(
  allocator: TestAllocator,
  program: String,
  output: String = "",
  input: String? = null,
) {
  val shortcut = allocator.name.lowercase()
  val dataDir = File("testData/").apply { mkdirs() }.dir.toPath()
  val programFile = Files.createTempFile(dataDir, shortcut, ".lat").toFile().apply { writeText(program) }
  val inputFile = input?.let { programFile.withExtension(".input", it.trimIndent()) }
  val compiled = programFile.runCompiler(strategy = allocator.strategy)
  val asmFile = programFile.withExtension(".asm", compiled)
  val (o, exe) = nasm(asmFile, libFile = File("lib/runtime.o"))
  val outFile = programFile.withExtension(".outputTest")
  val errFile = programFile.withExtension(".errorTest")
  exe.absolutePath(inputFile, outFile, errFile).zeroCode()
  assertEquals(output.trimIndent(), outFile.readText())
  assertEquals("", errFile.readText())
  listOfNotNull(programFile, inputFile, asmFile, o, exe, outFile, errFile).forEach { it.delete() }
}

