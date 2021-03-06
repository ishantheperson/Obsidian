package edu.cmu.cs.obsidian.tests

import org.scalatest.junit.JUnitSuite
import org.junit.Test
import org.junit.Assert.assertTrue
import edu.cmu.cs.obsidian.lexer._
import edu.cmu.cs.obsidian.parser.Parser.ParseException
import edu.cmu.cs.obsidian.parser._

class ParserTests extends JUnitSuite {

    private def parse(src: String): Either[String, AST] = {
        val result = Lexer.tokenize(src)
        val tokens: Seq[Token] = result match {
            case Left(msg) => {
                println(s"Lexing Failed: $msg")
                return Left("Doesn't lex")
            }
            case Right(res) => res
        }
        assertTrue(result.isRight)
        Parser.parseProgram(tokens, "")
    }

    private def readFile(fileName: String): String = {
        val bufferedSource = scala.io.Source.fromFile(fileName)
        val src = try bufferedSource.getLines() mkString "\n" finally bufferedSource.close()

        val tokens: Seq[Token] = Lexer.tokenize(src) match {
            case Left(msg) => throw new ParseException(msg)
            case Right(ts) => ts
        }
        src
    }

    private def shouldSucceed(src: String): Unit = {
        val result = parse(src)
        val message: String = if (result.isRight) "" else result.left.get
        assertTrue(message, result.isRight)
    }

    private def shouldFail(src: String): Unit = {
        val result = parse(src)
        assertTrue(result.isLeft)
    }

    private def shouldSucceedFile(fileName: String): Unit = {
        shouldSucceed(readFile(fileName))
    }

    private def shouldFailFile(fileName: String): Unit = {
        shouldFail(readFile(fileName))
    }

    private def testAndPrint(src: String): Unit = {
        println(parse(src))
    }

    private def shouldEqual(src: String, ast: AST): Unit = {
        val result = parse(src)
        assertTrue(result.isRight && result.right.get == ast)
    }

    private def shouldEqual(src1: String, src2: String): Unit = {
        val result1 = Lexer.tokenize(src1)
        val result2 = Lexer.tokenize(src2)
        assertTrue((result1.isRight && result2.isRight) || (result1.isLeft && result2.isLeft))
        (result1, result2) match {
            case (Right(res1), Right(res2)) => assertTrue(res1 == res2)
            case _ => ()
        }
    }

    @Test def simpleContracts(): Unit = {
        shouldSucceed(
            """
              | main contract C { transaction f() { hello = 1; world = 2; } }
            """.stripMargin)
        shouldSucceed(
            """
              | main contract C {
              |     state S1 { }
              | }
            """.stripMargin)
    }

    @Test def goodExpressions(): Unit = {
        shouldSucceed(
            """
              | main contract C {
              |     state S1 {
              |     }
              |
              |     transaction t1 (C@S1 this) {
              |       x = (x.f.y.z((((5)))));
              |       (x).f = (new A()).f;
              |     }
              |
              | }
            """.stripMargin
        )
    }

    @Test def goodStatements(): Unit = {
        shouldSucceed(
            """
              | main contract C {
              |     state S1 {
              |
              |     }
              |     transaction t1(C@S1 this) {
              |             x.x = x.f1.f2.f3();
              |             x = x();
              |             x();
              |             x().f = x();
              |             x.f();
              |             x.f.f();
              |             new A();
              |             a = new A();
              |             A a = new A();
              |             T x;
              |             T x = x;
              |             T x = x.f();
              |             return;
              |             return x;
              |             return x.f;
              |         }
              | }
            """.stripMargin
        )
    }

    @Test def badStatements(): Unit = {
        shouldFail("""main contract C { state S { transaction t() {
              | new;
              | }}}""".stripMargin)
        shouldFail("""main contract C { state S { transaction t() {
              | return (;
              | }}}""".stripMargin)
    }

    @Test def goodFuncArgs(): Unit = {
        shouldSucceed(
            """
              | main contract C {
              |     state S1 {
              |     }
              |     transaction t(C@S1 this) { return x; }
              |         transaction t(C@S1 this, T x) { return x; }
              |         transaction t(C@S1 this, T1 x, T2 y, T3 z) {
              |             f(x, y, z);
              |             f();
              |             x.f(x, y, z);
              |             f(x);
              |             f(x.f);
              |             f(5, x, f());
              |             f(g(g(5)));
              |         }
              | }
            """.stripMargin
        )
    }

    @Test def badFuncArgs(): Unit = {
        shouldFail("""main contract C { state S {
              | transaction t(x) { return x; }
              | }}""".stripMargin)
        shouldFail("""main contract C { state S {
              | function t(x) { return x; }
              | }}""".stripMargin)
        shouldFail("""main contract C { state S {
              | transaction t(x, T x) { return x; }
              | }}""".stripMargin)
        shouldFail("""main contract C { state S { transaction t() {
              | f(T x);
              | }}}""".stripMargin)
    }

    @Test def transitions(): Unit = {
        shouldSucceed(
            """ main contract C { state S {
              | }
              | transaction t(C@S this, T x) { ->S(x = y, y = x); }
              | }
            """.stripMargin)
        shouldSucceed(
            """ main contract C { state S {
              | }
              | transaction t(C@S this, T x) { ->S(); }
              | }
            """.stripMargin)
        shouldSucceed(
            """ main contract C { state S {
              | }
              |  transaction t(C@S this, T x) { ->S; }
              | }
            """.stripMargin)
        shouldSucceed(
            """ main contract C { state S {
              | }
              |  transaction t(C@S this, T x) { ->S(x = y); }
              | }
            """.stripMargin)
    }

    // MJC: paths are disabled for now.
//    @Test def pathTests() = {
//
//        shouldSucceed(
//            """main contract UsesC {
//              |    transaction t1() {
//              |        z.T x = 3;
//              |    }
//              |}
//            """.stripMargin)
//    }

    //test token ordering in transaction parsing
    @Test def transactionTests(): Unit = {
        shouldSucceedFile("resources/tests/parser_tests/ValidTransactions.obs")
        shouldFail("resources/tests/parser_tests/BadTransactionOrdering.obs")
        shouldSucceedFile("resources/tests/parser_tests/AvailableInRepeats.obs")
        shouldSucceedFile("resources/tests/parser_tests/TransactionOptions.obs")
        shouldFail("resources/tests/parser_tests/BadTransactionOptions.obs")
    }

    @Test def invocationSpec(): Unit = {
        shouldSucceedFile("resources/tests/parser_tests/InvokableSpec.obs")
    }

    @Test def emptyBody(): Unit = {
        shouldSucceedFile("resources/tests/parser_tests/EmptyBody.obs")
    }

    @Test def stateInitialization(): Unit = {
        shouldSucceedFile("resources/tests/parser_tests/StateInitialization.obs")
    }

    @Test def unclosedContract(): Unit = {
        shouldFailFile("resources/tests/parser_tests/UnclosedContract.obs")
    }

    @Test def FSMs(): Unit = {
        shouldSucceedFile("resources/tests/parser_tests/FSMs.obs")
    }

    @Test def thisArgument(): Unit = {
        shouldFail(
          """main contract C {
            | transaction t(int this) { }
            | }
          """.stripMargin
          )
        shouldFail(
            """main contract C {
              | transaction t(Foo this) { }
              | }
            """.stripMargin
        )
        shouldSucceed(
            """main contract C {
              | transaction t(C this) { }
              | }
            """.stripMargin
        )
    }

    @Test def parseModOperator(): Unit = {
        shouldSucceed(
            """
              | main contract C { transaction f(int y) { int x = 1 % y; } }
            """.stripMargin)
    }

    @Test def parseContractImplements(): Unit = {
        shouldSucceed(
            """
              | contract C implements I {}
            """.stripMargin)
    }

    @Test def parseContractImplementsParams(): Unit = {
        // This won't compile, but should parse
        shouldSucceed(
            """
              | contract C implements I[X] {}
            """.stripMargin)
    }

    @Test def parseContractWithParams(): Unit = {
        shouldSucceed(
            """
              | contract C[T] {}
            """.stripMargin)
    }

    @Test def parseContractWithParamsState(): Unit = {
        shouldSucceed(
            """
              | contract C[T@s] {}
            """.stripMargin)
    }

    @Test def parseContractWithParamsWithBound(): Unit = {
        shouldSucceed(
            """
              | contract C[T where T implements I] {}
            """.stripMargin)
    }

    @Test def parseContractWithParamsStateWithBound(): Unit = {
        shouldSucceed(
            """
              | contract C[T@s where T implements I where s is Owned] {}
            """.stripMargin)
    }

    @Test def parseContractWithParamsWithBoundParams(): Unit = {
        shouldSucceed(
            """
              | contract C[T where T implements I[T]] {}
            """.stripMargin)
    }

    @Test def parseContractWithParamsStateWithBoundParams(): Unit = {
        shouldSucceed(
            """
              | contract C[T where T implements I[T] where s is Owned] {}
            """.stripMargin)
    }

    @Test def parseContractParamsAndImplements(): Unit = {
        shouldSucceed(
            """
              | contract C[T] implements I {}
            """.stripMargin)
    }

    @Test def parseContractParamsWithBoundAndImplements(): Unit = {
        shouldSucceed(
            """
              | contract C[T where T implements I1[T] where s is Owned] implements I2[T] {}
            """.stripMargin)
    }

    @Test def parseConstructionWithParams(): Unit = {
        shouldSucceed(
            """
              | contract C { transaction f () { new C[int](); } }
            """.stripMargin)
    }

    @Test def parseInterface(): Unit = {
        shouldSucceed(
            """
              | interface I { state A; transaction f() returns int; }
            """.stripMargin)
    }

    @Test def parseTransactionParameters(): Unit = {
        shouldSucceed(
            """
              | contract C { transaction f() { x.g[int](2); testing[string, K](); } }
            """.stripMargin)
    }

    @Test def parseGenericStateVariables(): Unit = {
        shouldSucceed(
            """
              | contract C[X@s] { transaction f(X@s >> Unowned x) {} }
            """.stripMargin)
    }
}
