package com.gallang;

import com.gallang.ast.Program;
import com.gallang.compiler.AstBuilder;
import com.gallang.compiler.LogicCompiler;
import com.gallang.compiler.JedecGenerator;
import com.gallang.compiler.SvgGenerator;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
/**
 * gallang compiler entry point.
 *
 * Usage:  java -jar gallang.jar <input.gal> [output.jed] [--svg]
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
            System.err.println("Usage: gallang <input.gal> [output.jed] [--svg]");
            System.exit(1);
        }

        // ── Argument parsing ──────────────────────────────────────────────────
        boolean    genSvg     = false;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--svg")) genSvg = true;
            else positional.add(arg);
        }
        if (positional.isEmpty()) {
            System.err.println("Usage: gallang <input.gal> [output.jed] [--svg]");
            System.exit(1);
        }

        Path   inputPath  = Paths.get(positional.get(0));
        String baseName   = inputPath.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        Path   jedPath    = inputPath.resolveSibling(baseName + ".jed");
        Path   outputJed  = positional.size() > 1 ? Paths.get(positional.get(1)) : jedPath;

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
        LogicCompiler.CompiledProgram compiled = LogicCompiler.compile(program, baseName);

        if (compiled.equations.isEmpty()) {
            System.err.println("Error: no valid output equations (output pins are 14-23).");
            System.exit(1);
        }

        // ── 4. Generate JEDEC ─────────────────────────────────────────────
        String jedec = JedecGenerator.generate(compiled);
        Files.writeString(outputJed, jedec);
        System.out.println("Wrote JEDEC: " + outputJed);

        // ── 5. Optionally generate SVG schematic ──────────────────────────────
        if (genSvg) {
            Path   svgPath = inputPath.resolveSibling(baseName + ".svg");
            String svg     = SvgGenerator.generate(compiled);
            Files.writeString(svgPath, svg);
            System.out.println("Wrote SVG:   " + svgPath);
        }
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
