package edu.cmu.cs.obsidian

import java.nio.file.{Files, Path, Paths}
import java.io.File
import java.util.Scanner

import edu.cmu.cs.obsidian.lexer._
import edu.cmu.cs.obsidian.parser._
import edu.cmu.cs.obsidian.codegen._
import edu.cmu.cs.obsidian.protobuf._
import edu.cmu.cs.obsidian.util._
import com.helger.jcodemodel.{JCodeModel, JPackage, JDefinedClass}
import collection.JavaConverters._

import scala.sys.process._

case class CompilerOptions (outputPath: Option[String],
                            debugPath: Option[String],
                            inputFiles: List[String],
                            verbose: Boolean,
                            printTokens: Boolean,
                            printAST: Boolean,
                            buildClient: Boolean)

object Main {


    val usage: String =
        """Usage: obsidian [options] file.obs
          |Options:
          |    --output-path path/to/dir    outputs jar and proto files at the given directory
          |    --dump-debug path/to/dir     save intermediate files at the given directory
          |    --verbose                    print error codes and messages for jar and javac
          |    --print-tokens               print output of the lexer
          |    --print-ast                  print output of the parser
          |    --build-client               build a client application rather than a server
        """.stripMargin

    def parseOptions(args: List[String]): CompilerOptions = {
        var outputPath: Option[String] = None
        var debugPath: Option[String] = None
        var inputFiles: List[String] = List.empty
        var verbose = false
        var printTokens = false
        var printAST = false
        var buildClient = false

        def parseOptionsRec(remainingArgs: List[String]) : Unit = {
            remainingArgs match {
                case Nil => ()
                case "--verbose" :: tail =>
                    verbose = true
                    parseOptionsRec(tail)
                case "--print-tokens" :: tail =>
                    printTokens = true
                    parseOptionsRec(tail)
                case "--print-ast" :: tail =>
                    printAST = true
                    parseOptionsRec(tail)
                case "--output-path" :: path :: tail =>
                    outputPath = Some(path)
                    parseOptionsRec(tail)
                case "--dump-debug" :: path :: tail =>
                    debugPath = Some(path)
                    parseOptionsRec(tail)
                case "--build-client" :: tail =>
                    buildClient = true
                    parseOptionsRec(tail)
                case option :: tail =>
                    if (option.startsWith("--") || option.startsWith("-")) {
                        println("Unknown option " + option)
                        sys.exit(0)
                    }
                    else if (option.endsWith(".obs")) {
                        // This is an input file.
                        inputFiles = option :: inputFiles
                        parseOptionsRec(tail)
                    }
                    else {
                        println("Unknown argument " + option)
                        sys.exit(0)
                    }
            }
        }

        parseOptionsRec(args)

        if (inputFiles.isEmpty) {
            println("Must pass at least one file")
            sys.exit(0)
        }

        if (inputFiles.length > 1) {
            println("For now: contracts can only consist of a single file")
        }

        CompilerOptions(outputPath, debugPath, inputFiles, verbose, printTokens, printAST, buildClient)
    }

    def findMainContractName(prog: Program): String = {
        val mainContractOption = findMainContract(prog)
        if (mainContractOption.isEmpty) {
            throw new RuntimeException("No main contract found")
        }
        return mainContractOption.get.name
    }

    def translateServerASTToJava (ast: Program, protobufOuterClassName: String): JCodeModel = {
        val codeGen = new CodeGen(Server())
        codeGen.translateProgram(ast, protobufOuterClassName)
    }

    def findMainContract(ast: Program): Option[Contract] = {
        ast.contracts.find((c: Contract) => c.mod.contains(IsMain))
    }

    def translateClientASTToJava (ast: Program, protobufOuterClassName: String): JCodeModel = {
        // Client programs must have a main contract.
        val mainContractOption = findMainContract(ast)
        if (mainContractOption.isEmpty) {
            throw new RuntimeException("No main contract found")
        }
        val codeGen = new CodeGen(Client(mainContractOption.get))
        codeGen.translateProgram(ast, protobufOuterClassName)
    }

    /* returns the exit code of the javac process */
    def invokeJavac(
            printJavacOutput: Boolean,
            mainName: String,
            sourceDir: Path,
            compileTo: Path): Int  = {

        val sourcePath = sourceDir.toString
        val classPath =
            s"Obsidian Runtime/src/Runtime/:$sourcePath:lib/protobuf-java-3.2.0.jar:lib/json-20160810.jar"
        val srcFile = sourceDir.resolve(s"edu/cmu/cs/obsidian/generated_code/$mainName.java")
        val compileCmd: Array[String] = Array("javac", "-d", compileTo.toString,
                                                       "-classpath", classPath,
                                                        srcFile.toString)

        val proc: java.lang.Process = Runtime.getRuntime().exec(compileCmd)

        if (printJavacOutput) {
            val compilerOutput = proc.getErrorStream()
            val untilEOF = new Scanner(compilerOutput).useDelimiter("\\A")
            val result = if (untilEOF.hasNext()) {
                untilEOF.next()
            } else {
                ""
            }

            print(result)
        }

        proc.waitFor()
        proc.exitValue()
    }

    /* returns the exit code of the jar process */
    def makeJar(
            printJavacOutput: Boolean,
            mainName: String,
            outputJar: Path,
            bytecode: Path): Int  = {

        val manifest = s"Obsidian Runtime/manifest.mf"
        val entryClass = s"edu.cmu.cs.obsidian.generated_code.$mainName"
        val jarCmd: Array[String] =
            Array("jar", "-cmfe", manifest, outputJar.toString, entryClass, "-C",
                  bytecode.toString, "edu")
        val procJar = Runtime.getRuntime().exec(jarCmd)

        if (printJavacOutput) {
            val compilerOutput = procJar.getErrorStream()
            val untilEOF = new Scanner(compilerOutput).useDelimiter("\\A")
            val result = if (untilEOF.hasNext()) {
                untilEOF.next()
            } else {
                ""
            }

            print(result)
        }

        procJar.waitFor()
        procJar.exitValue()
    }

    def recDelete(f: File): Unit = {
        if (f.isDirectory) {
            for (sub_f <- f.listFiles()) {
                recDelete(sub_f)
            }
        }

        f.delete()
    }


    private def protobufOuterClassNameForClass(className: String): String = {
        className.substring(0, 1).toUpperCase(java.util.Locale.US) + className.substring(1) + "OuterClass"
    }

    def runVerifier(directory: File, model: JCodeModel, sourcePath: Path): Boolean = {
        val javaFiles = directory.listFiles()
        var allSucceeded = true
        val classPath =
            s"Obsidian Runtime/src/Runtime/:$sourcePath:lib/protobuf-java-3.2.0.jar:lib/json-20160810.jar"


        for (p: JPackage <- model.packages.asScala) {
            for (c: JDefinedClass <- p.classes().asScala) {
                val innerFilePath = c.fullName().replace(".", "/")
                val filename = directory + "/" + (innerFilePath + ".java")
                // invoke java -jar OpenJML/openjml.jar <f>
                println("Running OpenJML verifier on " + filename)
                val verifyCommand: Array[String] = Array("java", "-Xss128m", "-jar", "OpenJML/openjml.jar", "-specspath", "resources/specs", "-subexpressions", /*"-ce",*/ "-esc", "-prover", "z3_4_3", "-exec", "/Users/mcoblenz/Downloads/z3-4.3.0-x86/bin/z3", "-cp", classPath, filename)

                val proc: java.lang.Process = Runtime.getRuntime().exec(verifyCommand)

                // This API is confusingly named; the "input stream" is connected to the output of the process.
                val compilerOutput = proc.getInputStream()
                val untilEOF = new Scanner(compilerOutput).useDelimiter("\\A")
                val result = if (untilEOF.hasNext()) {
                    untilEOF.next()
                } else {
                    ""
                }

                print(result)

                proc.waitFor()
                println("OpenJML result: " + proc.exitValue())
                allSucceeded = allSucceeded && (proc.exitValue() == 0)
            }

        }
        allSucceeded
    }

    // For input foo.obs, we generate foo.proto, from which we generate FooOuterClass.java.
    //    We also generate a jar at the specified directory, containing the generated classes.
    def main(args: Array[String]): Unit = {
        if (args.length == 0) {
            println(usage)
            sys.exit(0)
        }

        val options = parseOptions(args.toList)

        val tmpPath: Path = options.debugPath match {
            case Some(p) =>
                val path = Paths.get(p)
                /* create the dir if it doesn't exist */
                Files.createDirectories(path)
                path
            case None => Files.createTempDirectory("obsidian").toAbsolutePath
        }

        val shouldDelete = options.debugPath.isEmpty

        val srcDir = tmpPath.resolve("generated_java")
        val bytecodeDir = tmpPath.resolve("generated_bytecode")

        /* if an output path is specified, use it; otherwise, use working directory */
        val outputPath = options.outputPath match {
            case Some(p) =>
                val path = Paths.get(p)
                /* create the dir if it doesn't exist */
                Files.createDirectories(path)
                path
            case None => Paths.get(".")
        }


        Files.createDirectories(srcDir)
        Files.createDirectories(bytecodeDir)

        /* we just look at the first file because we don't have a module system yet */
        val filename = options.inputFiles.head

        try {
            val ast = Parser.parseFileAtPath(filename, options.printTokens)

            if (options.printAST) {
                println("AST")
                println()
                println(ast)
                println()
            }


            val lastSlash = filename.lastIndexOf("/")
            val sourceFilename = if (lastSlash < 0) filename else filename.substring(lastSlash + 1)

            val protobufOuterClassName = Util.protobufOuterClassNameForFilename(sourceFilename)

            val javaModel = if (options.buildClient) translateClientASTToJava(ast, protobufOuterClassName)
                                else translateServerASTToJava(ast, protobufOuterClassName)
            javaModel.build(srcDir.toFile)

            val protobufs: Seq[(Protobuf, String)] = ProtobufGen.translateProgram(ast, sourceFilename)

            // Each import results in a .proto file, which needs to be compiled.
            for (p <- protobufs) {
                val protobuf = p._1
                val filename = p._2

                val protobufOuterClassName = Util.protobufOuterClassNameForFilename(filename)
                val protobufFilename = protobufOuterClassName + ".proto"

                val protobufPath = outputPath.resolve(protobufFilename)

                protobuf.build(protobufPath.toFile, protobufOuterClassName)


                // Invoke protoc to compile from protobuf to Java.
                val protocInvocation: String =
                    "protoc --java_out=" + srcDir + " " + protobufPath.toString

                try {
                    val exitCode = protocInvocation.!
                    if (exitCode != 0) {
                        println("`" + protocInvocation + "` exited abnormally: " + exitCode)
                    }
                } catch {
                    case e: Throwable => println("Error running protoc: " + e)
                }
            }


            // invoke javac and make a jar from the result
            val mainName = findMainContractName(ast)
            val javacExit = invokeJavac(options.verbose, mainName, srcDir, bytecodeDir)
            if (options.verbose) {
                println("javac exited with value " + javacExit)
            }
            if (javacExit == 0) {
                val jarPath = outputPath.resolve(s"$mainName.jar")
                val jarExit = makeJar(options.verbose, mainName, jarPath, bytecodeDir)
                if (options.verbose) {
                    println("jar exited with value " + jarExit)
                }
            }

            // Run the OpenJML verifier on all the generated files.
            runVerifier(srcDir.toFile, javaModel, srcDir)

        } catch {
            case e: Parser.ParseException => println(e.message)
        }

        if (shouldDelete) {
            recDelete(tmpPath.toFile)
        }
    }
}