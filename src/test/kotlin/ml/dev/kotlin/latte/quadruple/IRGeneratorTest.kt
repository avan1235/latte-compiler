package ml.dev.kotlin.latte.quadruple

import ml.dev.kotlin.latte.asm.EMPTY_STRING_LABEL
import ml.dev.kotlin.latte.syntax.parse
import ml.dev.kotlin.latte.typecheck.CLASS_DIVIDER
import ml.dev.kotlin.latte.typecheck.typeCheck
import ml.dev.kotlin.latte.util.nlString
import ml.dev.kotlin.latte.util.splitAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class IRGeneratorTest {

  @Nested
  inner class BaseConstructTest {
    @Test
    fun `test decreases with special op`() = testIR(
      program = """
      int main() {
        int a = 1;
        a--;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 1
        a@0#1 = dec a@0#0
        ret a@0#1
      """
    )

    @Test
    fun `test increases with special op`() = testIR(
      program = """
      int main() {
        int a = -1;
        a++;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = -1
        a@0#1 = inc a@0#0
        ret a@0#1
      """
    )

    @Test
    fun `test check rel compare op with jumps`() = testIR(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        boolean c = a < b;
        if (c) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = -1
        b@1#0 = 1
        @T2#0 = false
        if a@0#0 ge b@1#0 goto F3
      G8:
        @T2#1 = true
      F3:
        @T2#2 = phi (G8:@T2#1, main:@T2#0)
        c@4#0 = @T2#2
        if c@4#0 goto L5
        ret 0
      L5:
        ret 1
      """
    )

    @Test
    fun `test check rel equality op with jumps`() = testIR(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        boolean c = a == b;
        if (c) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = -1
        b@1#0 = 1
        @T2#0 = false
        if a@0#0 ne b@1#0 goto F3
      G8:
        @T2#1 = true
      F3:
        @T2#2 = phi (G8:@T2#1, main:@T2#0)
        c@4#0 = @T2#2
        if c@4#0 goto L5
        ret 0
      L5:
        ret 1
      """
    )

    @Test
    fun `test check rel op in cond with jumps`() = testIR(
      program = """
      int main() {
        int a = -1;
        int b = 1;
        if (a < b && a == b) {
          return 1;
        }
        else {
          return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = -1
        b@1#0 = 1
        if a@0#0 lt b@1#0 goto M5
        goto L3
      M5:
        if a@0#0 eq b@1#0 goto L2
      L3:
        ret 0
      L2:
        ret 1
      """
    )

    @Test
    fun `test generates functions other than main`() = testIR(
      program = """
      int main() {
        int a = f();
        int b = g();
        return a + b;
      }
      int f() {
        return 42;
      }
      int g() {
        return 24;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call f ()
        a@1#0 = @T0#0
        @T2#0 = call g ()
        b@3#0 = @T2#0
        @T4#0 = a@1#0 plus b@3#0
        ret @T4#0
      f():
        ret 42
      g():
        ret 24
      """
    )
  }

  @Nested
  inner class FunctionTest {
    @Test
    fun `test not change std lib functions names`() = testIR(
      program = """
      int main() {
        printString("str");
        printInt(42);
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __printString (S1)
        @T2#0 = call __printInt (42)
        ret 0
      """,
      mapOf("str" to "S1")
    )

    @Test
    fun `test gives functions arguments proper names`() = testIR(
      program = """
      int main() {
        return 0;
      }
      int f(int a, boolean b) {
        while (a > 0) {
          b = !b;
          a--;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      f@int@boolean(a#0, b#0):
        goto L1
      L0:
        @T3#0 = not b#1
        b#2 = @T3#0
        a#2 = dec a#1
      L1:
        a#1 = phi (L0:a#2, f@int@boolean:a#0)
        b#1 = phi (L0:b#2, f@int@boolean:b#0)
        if a#1 gt 0 goto L0
        ret a#1
      """,
    )
  }

  @Nested
  inner class ClassesTest {
    @Test
    fun `accesses class fields with proper offsets`() = testIR(
      program = """
      int main() {
        A a = new A;
        a.x = 42;
        a.y = true;
        return 0;
      }
      class A {
        int x;
        boolean y;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __alloc (8)
        *(@T0#0 + 0) = A
        a@1#0 = @T0#0
        *(a@1#0 + 4) = 42
        *(a@1#0 + 8) = true
        ret 0
      """
    )

    @Test
    fun `accesses class methods with proper names on override`() = testIR(
      program = """
      int main() {
        A a = new A;
        B b = new B;
        a.f(42);
        b.f(42);
        a = b;
        a.f(42);
        return 0;
      }
      class A {
        int f(int x) {
          return x;
        }
      }
      class B extends A {
        int f(int x) {
          return 2 * x;
        }
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __alloc (0)
        *(@T0#0 + 0) = A
        a@1#0 = @T0#0
        @T2#0 = call __alloc (0)
        *(@T2#0 + 0) = B
        b@3#0 = @T2#0
        @T4#0 = call a@1#0.f (42)
        @T5#0 = call b@3#0.f (42)
        a@1#1 = b@3#0
        @T6#0 = call a@1#1.f (42)
        ret 0
      A${CLASS_DIVIDER}f@int(self#0, x#0):
        ret x#0
      B${CLASS_DIVIDER}f@int(self#0, x#0):
        @T7#0 = 2 times x#0
        ret @T7#0
      """,
      virtualTable = mapOf(
        "A" to listOf("A${CLASS_DIVIDER}f@int"),
        "B" to listOf("B${CLASS_DIVIDER}f@int"),
      )
    )

    @Test
    fun `orders class methods in the same order in all virtual tables`() = testIR(
      program = """
      int main() {
        A a = new A;
        A b = new B;
        A c = new C;
        a.f(42);
        b.f(42);
        c.f(42);
        return 0;
      }
      class A {
        void f(int x) { }
        void g(int x) { }
        void h(int x) { }
      }
      class B extends A {
        void g(int x) { }
        void f(int x) { }
      }
      class C extends B {
        void h(int x) { }
        void f(int x) { }
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __alloc (0)
        *(@T0#0 + 0) = A
        a@1#0 = @T0#0
        @T2#0 = call __alloc (0)
        *(@T2#0 + 0) = B
        b@3#0 = @T2#0
        @T4#0 = call __alloc (0)
        *(@T4#0 + 0) = C
        c@5#0 = @T4#0
        @T6#0 = call a@1#0.f (42)
        @T7#0 = call b@3#0.f (42)
        @T8#0 = call c@5#0.f (42)
        ret 0
      A${CLASS_DIVIDER}f@int(self#0, x#0):
        ret
      A${CLASS_DIVIDER}g@int(self#0, x#0):
        ret
      A${CLASS_DIVIDER}h@int(self#0, x#0):
        ret
      B${CLASS_DIVIDER}g@int(self#0, x#0):
        ret
      B${CLASS_DIVIDER}f@int(self#0, x#0):
        ret
      C${CLASS_DIVIDER}h@int(self#0, x#0):
        ret
      C${CLASS_DIVIDER}f@int(self#0, x#0):
        ret
      """,
      virtualTable = mapOf(
        "A" to listOf("A${CLASS_DIVIDER}f@int", "A${CLASS_DIVIDER}g@int", "A${CLASS_DIVIDER}h@int"),
        "B" to listOf("B${CLASS_DIVIDER}f@int", "B${CLASS_DIVIDER}g@int", "A${CLASS_DIVIDER}h@int"),
        "C" to listOf("C${CLASS_DIVIDER}f@int", "B${CLASS_DIVIDER}g@int", "C${CLASS_DIVIDER}h@int"),
      )
    )

    @Test
    fun `works with unary operations properly on reference types`() = testIR(
      program = """
      int main() {
        A a = new A;
        a.x = 0;
        a.x++;
        return 0;
      }
      class A {
        int x;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __alloc (4)
        *(@T0#0 + 0) = A
        a@1#0 = @T0#0
        *(a@1#0 + 4) = 0
        @T2#0 = *(a@1#0 + 4)
        @T2#1 = inc @T2#0
        *(a@1#0 + 4) = @T2#1
        ret 0
      """,
    )

    @Test
    fun `works correctly on nested classes fields`() = testIR(
      program = """
      int main() {
        C c = new C;
        c.b = new B;
        c.b.a = new A;
        c.b.a.x = 42;
        return 0;
      }
      class A {
        int x;
      }
      class B {
        A a;
      }
      class C {
        B b;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = call __alloc (4)
        *(@T0#0 + 0) = C
        c@1#0 = @T0#0
        @T2#0 = call __alloc (4)
        *(@T2#0 + 0) = B
        *(c@1#0 + 4) = @T2#0
        @T3#0 = call __alloc (4)
        *(@T3#0 + 0) = A
        @T4#0 = *(c@1#0 + 4)
        *(@T4#0 + 4) = @T3#0
        @T5#0 = *(c@1#0 + 4)
        @T6#0 = *(@T5#0 + 4)
        *(@T6#0 + 4) = 42
        ret 0
      """
    )

    @Test
    fun `accesses class fields with implicit this`() = testIR(
      program = """
      int main() {
        return 0;
      }
      class A {
        int x;
        boolean y;
        void z() {
          x = 42;
          boolean y = false;
          y = true;
          self.y = true;
          self.x = 24;
        }
      }
      """,
      irRepresentation = """
      main():
        ret 0
      A${CLASS_DIVIDER}z(self#0):
        *(self#0 + 4) = 42
        y@0#0 = false
        y@0#1 = true
        *(self#0 + 8) = true
        *(self#0 + 4) = 24
        ret
      """,
      virtualTable = mapOf(
        "A" to listOf("A${CLASS_DIVIDER}z")
      )
    )

    @Test
    fun `accesses class methods with implicit this`() = testIR(
      program = """
      int main() {
        return 0;
      }
      void f() {}
      class A extends B {
        void test() {
          f();
          self.f();
          g();
          h();
        }
        void f() {}
        void g() {}
      }
      class B {
        void h() {}
      }
      """,
      irRepresentation = """
      main():
        ret 0
      f():
        ret
      A${CLASS_DIVIDER}test(self#0):
        @T0#0 = call f ()
        @T1#0 = call self#0.f ()
        @T2#0 = call self#0.g ()
        @T3#0 = call self#0.h ()
        ret
      A${CLASS_DIVIDER}f(self#0):
        ret
      A${CLASS_DIVIDER}g(self#0):
        ret
      B${CLASS_DIVIDER}h(self#0):
        ret
      """,
      virtualTable = mapOf(
        "B" to listOf("B${CLASS_DIVIDER}h"),
        "A" to listOf("B${CLASS_DIVIDER}h", "A${CLASS_DIVIDER}test", "A${CLASS_DIVIDER}f", "A${CLASS_DIVIDER}g")
      )
    )

  }

  @Nested
  inner class NestVariablesTest {
    @Test
    fun `variables in main scope have same index`() = testIR(
      program = """
      int main() {
        int a = 0;
        int b = 1;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        b@1#0 = 1
        ret a@0#0
      """
    )

    @Test
    fun `variables index increases with scope`() = testIR(
      program = """
      int main() {
        int a = 0;
        {
          int a = 1;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        a@1#0 = 1
        ret a@0#0
      """
    )

    @Test
    fun `variables out of scope can be modified`() = testIR(
      program = """
      int main() {
        int a = 0;
        {
          a = 1;
          int a = 2;
          a = 3;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        a@0#1 = 1
        a@1#0 = 2
        a@1#1 = 3
        ret a@0#1
      """
    )

    @Test
    fun `scope increases with if statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        if (a == 1) {
          a = 1;
          int a = 2;
          a = 3;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        if a@0#0 eq 1 goto L1
      G4:
        goto L2
      L1:
        a@0#1 = 1
        a@3#0 = 2
        a@3#1 = 3
      L2:
        a@0#2 = phi (G4:a@0#0, L1:a@0#1)
        ret a@0#2
      """
    )

    @Test
    fun `scope increases with if else statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        if (a == 1) {
          a = 1;
          int a = 2;
          a = 3;
        }
        else {
          a = 4;
          int a = 5;
          a = 6;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        if a@0#0 eq 1 goto L1
      L2:
        a@0#3 = 4
        a@4#0 = 5
        a@4#1 = 6
        goto L3
      L1:
        a@0#1 = 1
        a@5#0 = 2
        a@5#1 = 3
      L3:
        a@0#2 = phi (L1:a@0#1, L2:a@0#3)
        ret a@0#2
      """
    )

    @Test
    fun `scope increases with while statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        while (a < 1) {
          a++;
          int a = 1;
          a = 2;
        }
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        goto L2
      L1:
        a@0#2 = inc a@0#1
        a@4#0 = 1
        a@4#1 = 2
      L2:
        a@0#1 = phi (L1:a@0#2, main:a@0#0)
        if a@0#1 lt 1 goto L1
        ret a@0#1
      """
    )

    @Test
    fun `scope increases with single line if statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        if (a == 1) int a = 2;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        if a@0#0 eq 1 goto L1
        goto L2
      L1:
        a@3#0 = 2
      L2:
        ret a@0#0
      """
    )

    @Test
    fun `scope increases with single line if else statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        if (a == 1) int a = 2;
        else int a = 5;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        if a@0#0 eq 1 goto L1
        a@4#0 = 5
        goto L3
      L1:
        a@5#0 = 2
      L3:
        ret a@0#0
      """
    )

    @Test
    fun `scope increases with single line while statement`() = testIR(
      program = """
      int main() {
        int a = 0;
        while (a < 1) int a = 1;
        return a;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 0
        goto L2
      L1:
        a@4#0 = 1
      L2:
        if a@0#0 lt 1 goto L1
        ret a@0#0
      """
    )
  }

  @Nested
  inner class OpOnNotConstTest {
    @Test
    fun `test op on int`() = testIR(
      program = """
      int main() {
        int a = 42;
        int b = 24;
        int x = (a + b * (3 - b) / a + 1) % 49;
        return x;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 42
        b@1#0 = 24
        @T2#0 = 3 minus b@1#0
        @T3#0 = b@1#0 times @T2#0
        @T4#0 = @T3#0 div a@0#0
        @T5#0 = a@0#0 plus @T4#0
        @T6#0 = @T5#0 plus 1
        @T7#0 = @T6#0 mod 49
        x@8#0 = @T7#0
        ret x@8#0
      """
    )

    @Test
    fun `test op on boolean OR`() = testIR(
      program = """
      int main() {
        boolean a = true;
        boolean b = false;
        boolean x = a || b;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = true
        b@1#0 = false
        if a@0#0 goto L3
        if b@1#0 goto L3
      L4:
        @T2#0 = false
        goto L5
      L3:
        @T2#1 = true
      L5:
        @T2#2 = phi (L3:@T2#1, L4:@T2#0)
        x@7#0 = @T2#2
        ret 0
      """
    )

    @Test
    fun `test op on boolean AND`() = testIR(
      program = """
      int main() {
        boolean x = id(positive(1) && positive(-1));
        return 0;
      }
      boolean id(boolean a) {
        return a;
      }
      boolean positive(int a) {
        return a > 0;
      }
      """,
      irRepresentation = """
      main():
        @T6#0 = call positive@int (1)
        if @T6#0 goto M5
        goto L3
      M5:
        @T7#0 = call positive@int (-1)
        if @T7#0 goto L2
      L3:
        @T1#0 = false
        goto L4
      L2:
        @T1#1 = true
      L4:
        @T1#2 = phi (L2:@T1#1, L3:@T1#0)
        @T0#0 = call id@boolean (@T1#2)
        x@8#0 = @T0#0
        ret 0
      id@boolean(a#0):
        ret a#0
      positive@int(a#0):
        @T9#0 = false
        if a#0 le 0 goto F10
      G13:
        @T9#2 = true
      F10:
        @T9#1 = phi (G13:@T9#2, positive@int:@T9#0)
        ret @T9#1
      """
    )

    @Test
    fun `test op on string`() = testIR(
      program = """
      int main() {
        string a = "42";
        string b = "24";
        string x = a + b + a;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@1#0 = S0
        b@3#0 = S2
        @T4#0 = call __concatString (a@1#0, b@3#0)
        @T5#0 = call __concatString (@T4#0, a@1#0)
        x@6#0 = @T5#0
        ret 0
      """,
      mapOf(
        "42" to "S0",
        "24" to "S2",
      )
    )
  }


  @Nested
  inner class CondStructureTest {
    @Test
    fun `test structure of if`() = testIR(
      program = """
      int main() {
        boolean b = true;
        if (b) return 1;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@0#0 = true
        if b@0#0 goto L1
        goto L2
      L1:
        ret 1
      L2:
        ret 0
      """
    )

    @Test
    fun `test structure of if else`() = testIR(
      program = """
      int main() {
        boolean b = true;
        if (b) return 1;
        else return 0;
      }
      """,
      irRepresentation = """
      main():
        b@0#0 = true
        if b@0#0 goto L1
        ret 0
      L1:
        ret 1
      """
    )

    @Test
    fun `test structure of while`() = testIR(
      program = """
      int main() {
        boolean b = true;
        int i = 0;
        while (b) i++;
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@0#0 = true
        i@1#0 = 0
        goto L3
      L2:
        i@1#2 = inc i@1#1
      L3:
        i@1#1 = phi (L2:i@1#2, main:i@1#0)
        if b@0#0 goto L2
        ret 0
      """
    )

    @Test
    fun `test structure of nested if else`() = testIR(
      program = """
      int main() {
        boolean a = true;
        boolean b = true;
        if (b) {
          if (a) return 3;
          else return 2;
        }
        else {
          if (a) return 1;
          else return 0;
        }
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = true
        b@1#0 = true
        if b@1#0 goto L2
        if a@0#0 goto L5
        ret 0
      L5:
        ret 1
      L2:
        if a@0#0 goto L8
        ret 2
      L8:
        ret 3
      """
    )
  }

  @Nested
  inner class CondSimplifyTest {
    @Test
    fun `test simplify const if boolean AND - remove`() = testIR(
      program = """
      int main() {
        if (false && true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if boolean AND - leave`() = testIR(
      program = """
      int main() {
        if (true && true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify const if boolean OR - remove`() = testIR(
      program = """
      int main() {
        if (false || false) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if boolean OR - leave`() = testIR(
      program = """
      int main() {
        if (false || true) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify const if nested - remove all`() = testIR(
      program = """
      int main() {
        if ((true && false) || 1 > 2) {
          if ((2 >= 3) || 1 == 2) {
            if (false || 1 != 1) {
              return 1;
            }
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 0
      """
    )

    @Test
    fun `test simplify const if nested - remove only nested`() = testIR(
      program = """
      int main() {
        if ((true && false) || 3 > 2) {
          if (!(2 < 3) || 1 == 2) {
            return 2;
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify mixed if nested - remove only nested`() = testIR(
      program = """
      int main() {
        boolean a = (true && false) || 3 > 2;
        if (a) {
          if (!(2 < 3) || 1 == 2) {
            return 2;
          }
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = true
        a@6#0 = @T0#0
        if a@6#0 goto L7
        goto L8
      L7:
        ret 1
      L8:
        ret 0
      """
    )

    @Test
    fun `test simplify const while - on true`() = testIR(
      program = """
      int main() {
        int i = 0;
        int j = 0;
        while (false || (true == true)) {
          i++;
          j--;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        i@0#0 = 0
        j@1#0 = 0
        goto L3
      L2:
        i@0#2 = inc i@0#1
        j@1#2 = dec j@1#1
      L3:
        i@0#1 = phi (L2:i@0#2, main:i@0#0)
        j@1#1 = phi (L2:j@1#2, main:j@1#0)
        goto L2
      """
    )

    @Test
    fun `test simplify const if else - on true`() = testIR(
      program = """
      int main() {
        if (1 == 2 || (true && (4 > 3))) {
          return 2;
        }
        else {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 2
      """
    )

    @Test
    fun `test simplify const if else - on false`() = testIR(
      program = """
      int main() {
        if (1 == 2 || (true && false)) {
          return 2;
        }
        else {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        ret 1
      """
    )

    @Test
    fun `test simplify identity equal memory locations`() = testIR(
      program = """
      int main() {
        int a = 42;
        if (a == a) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 42
        ret 1
      """
    )

    @Test
    fun `test simplify identity not equal memory locations`() = testIR(
      program = """
      int main() {
        int a = 42;
        if (a != a) {
          return 1;
        }
        return 0;
      }
      """,
      irRepresentation = """
      main():
        a@0#0 = 42
        ret 0
      """
    )
  }

  @Nested
  inner class ConstSimplifyTest {
    @Test
    fun `test simplify const int`() = testIR(
      program = """
      int main() {
        int i = (100 + 2 * (3 - 1) / 2 + 1) % 49;
        return i;
      }
      """,
      irRepresentation = """
      main():
        i@0#0 = 5
        ret i@0#0
      """
    )

    @Test
    fun `test simplify const neg int`() = testIR(
      program = """
      int main() {
        int i = -(-(-(-10)));
        return i;
      }
      """,
      irRepresentation = """
      main():
        i@0#0 = 10
        ret i@0#0
      """
    )

    @Test
    fun `test simplify const boolean`() = testIR(
      program = """
      int main() {
        boolean b = true && (false || (true && true));
        return 0;
      }
      """,
      irRepresentation = """
      main():
        @T0#0 = true
        b@7#0 = @T0#0
        ret 0
      """
    )

    @Test
    fun `test simplify const not boolean`() = testIR(
      program = """
      int main() {
        boolean b = !(!(!(!true)));
        return 0;
      }
      """,
      irRepresentation = """
      main():
        b@0#0 = true
        ret 0
      """
    )

    @Test
    fun `test simplify const string`() = testIR(
      program = """
      int main() {
        string s = "left" + "<>" + "right";
        return 0;
      }
      """,
      irRepresentation = """
      main():
        s@5#0 = S4
        ret 0
      """,
      mapOf(
        "left" to "S0",
        "<>" to "S1",
        "left<>" to "S2",
        "right" to "S3",
        "left<>right" to "S4"
      )
    )
  }
}

private fun testIR(
  program: String,
  irRepresentation: String,
  strings: Map<String, String>? = null,
  virtualTable: Map<String, List<String>>? = null,
) {
  val (graph, str, vTable) = program.byteInputStream().parse().typeCheck().toIR()
  with(graph) {
    removeNotReachableBlocks()
    transformToSSA()
  }
  val instructions = graph.instructions().peepHoleOptimize(extract = { it }).asIterable()
  val repr = instructions.nlString { it.repr() }
  val notEmptyVTables = vTable.filter { it.value.declarations.isNotEmpty() }
  val orderedVTablesRepr = notEmptyVTables.entries.associate { classVTable ->
    classVTable.key.typeName to classVTable.value.declarations.map { it.name }
  }
  assertEquals("\n${irRepresentation.trimIndent()}\n", repr)
  assertEquals((strings ?: emptyMap()) + ("" to EMPTY_STRING_LABEL.name), str.mapValues { it.value.name })
  virtualTable?.let { assertEquals(it, orderedVTablesRepr) }
  assert(instructions.splitAt { it is FunCodeLabelQ }.all { it.isSSA() })
}
