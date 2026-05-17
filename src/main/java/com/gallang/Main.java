package com.gallang;

import com.gallang.ast.Program;
import com.gallang.compiler.AstBuilder;
import com.gallang.compiler.LogicCompiler;
import com.gallang.compiler.JedecGenerator;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.*;
/**
 * gallang compiler entry point.
 *
 * Usage:  java -jar gallang.jar <input.gal> [output.jed]
 *
 * Pipeline:
 *   1. Parse .gal source with ANTLR
 *   2. Build AST
 *   3. Compile (inline intermediates, validate pins)
 *   4. Generate JEDEC directly (no galasm required)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: gallang <input.gal> [output.jed]");
            System.exit(1);
        }

        Path   inputPath  = Paths.get(args[0]);
        String baseName   = inputPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path   jedPath    = inputPath.resolveSibling(baseName + ".jed");
        Path   outputJed  = args.length > 1 ? Paths.get(args[1]) : jedPath;

        // ── 1. Parse ──────────────────────────────────────────────────────────
        String     source  = readFile(inputPath);
        CharStream chars   = CharStreams.fromString(source);
        GalLangLexer  lexer  = new GalLangLexer(chars);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GalLangParser parser = new GalLangParser(tokens);

        // Abort on first syntax error
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> r, Object sym,
                                    int line, int col, String msg, RecognitionException e) {
                System.err.println(inputPath + ":" + line + ":" + col + ": error: " + msg);
                System.exit(1);
            }
        });

        ParseTree tree = parser.program();

        // ── 2. Build AST ──────────────────────────────────────────────────────
        Program program = (Program) new AstBuilder().visit(tree);

        // ── 3. Compile ────────────────────────────────────────────────────────
        LogicCompiler.CompiledProgram compiled =
                LogicCompiler.compile(program, baseName);

        if (compiled.equations.isEmpty()) {
            System.err.println("Error: no valid output equations (output pins are 14-23).");
            System.exit(1);
        }

        // ── 4. Generate JEDEC directly ────────────────────────────────────────
        String jedec = JedecGenerator.generate(compiled);
        Files.writeString(outputJed, jedec);
        System.out.println("Wrote JEDEC: " + outputJed);
    }

    private static String readFile(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            System.err.println("Cannot read " + p + ": " + e.getMessage());
            System.exit(1);
            return null;
        }
    }
}
