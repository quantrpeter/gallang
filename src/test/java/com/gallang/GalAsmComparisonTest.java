package com.gallang;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compares the JEDEC fuse output of our gallang compiler against galasm for
 * every .pld / .gal test pair found in src/test/resources/.
 *
 * <p>For each test named {@code <name>}:
 * <ol>
 *   <li>Run {@code galasm <name>.pld} → produces {@code <name>.jed} (galasm
 *       reference)</li>
 *   <li>Run our compiler on {@code <name>.gal} → produces
 *       {@code <name>_ours.jed}</li>
 *   <li>Parse both JEDEC files and compare fuses 0–5827 (the functional fuse
 *       range, excluding the UES region where galasm embeds a design-name
 *       string and we write zeros).</li>
 * </ol>
 *
 * <p>The test fails if any fuse bit differs and prints a summary of every
 * differing address.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GalAsmComparisonTest {

    /** Absolute path to the gallang fat-jar produced by {@code mvn package}. */
    private static Path GALLANG_JAR;

    /** Directory that holds the .gal / .pld test resource pairs. */
    private static Path RESOURCES_DIR;

    /** galasm binary on PATH. */
    private static final String GALASM = "/usr/local/bin/galasm";

    /** Temporary directory for output JEDEC files produced during tests. */
    private static Path TMP_DIR;

    /** galasm-generated sidecar files to delete after all tests complete. */
    private static final List<Path> GALASM_OUTPUTS = new java.util.ArrayList<>();

    // ── Setup / teardown ─────────────────────────────────────────────────────

    @BeforeAll
    static void setUp() throws IOException {
        // Resolve the project root relative to the working directory that
        // Maven uses during 'mvn test' (= project root).
        Path projectRoot = Paths.get(System.getProperty("user.dir"));

        GALLANG_JAR  = projectRoot.resolve("target/gallang.jar");
        RESOURCES_DIR = projectRoot.resolve("src/test/resources");
        TMP_DIR       = Files.createTempDirectory("gallang_test_");

        assertTrue(Files.exists(GALLANG_JAR),
                "gallang.jar not found – run 'mvn package' first: " + GALLANG_JAR);
        assertTrue(Files.isDirectory(RESOURCES_DIR),
                "Test resources directory not found: " + RESOURCES_DIR);

        // Verify galasm is on PATH
        assertDoesNotThrow(() -> {
            Process p = new ProcessBuilder(GALASM).start();
            p.waitFor();
        }, "galasm not found on PATH – install galasm before running these tests");
    }

    @AfterAll
    static void tearDown() throws IOException {
        // Clean up temp JED files produced by our compiler
        if (TMP_DIR != null && Files.exists(TMP_DIR)) {
            try (Stream<Path> walk = Files.walk(TMP_DIR)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
        // Clean up galasm sidecar files (.jed, .fus, .chp) left next to .pld files
        for (Path p : GALASM_OUTPUTS) {
            Files.deleteIfExists(p);
        }
    }

    // ── Parameterized comparison test ─────────────────────────────────────────

    /**
     * Each name matches a {@code <name>.pld} + {@code <name>.gal} file pair in
     * src/test/resources/.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"and_or", "dff", "multi_out"})
    @Order(1)
    void fuseMapMatchesGalasm(String name) throws Exception {
        Path pldFile = RESOURCES_DIR.resolve(name + ".pld");
        Path galFile = RESOURCES_DIR.resolve(name + ".gal");

        assertTrue(Files.exists(pldFile), "Missing PLD file: " + pldFile);
        assertTrue(Files.exists(galFile), "Missing GAL file: " + galFile);

        // ── Step 1: run galasm ────────────────────────────────────────────────
        Path galasmJed = RESOURCES_DIR.resolve(name + ".jed");
        runGalasm(pldFile, galasmJed);

        // ── Step 2: run our compiler ──────────────────────────────────────────
        Path oursJed = TMP_DIR.resolve(name + "_ours.jed");
        runGallang(galFile, oursJed);

        // ── Step 3: compare fuse arrays ───────────────────────────────────────
        int[] galasmFuses = JedecParser.parse(galasmJed);
        int[] oursFuses   = JedecParser.parse(oursJed);

        assertEquals(galasmFuses.length, oursFuses.length,
                "Fuse-array length mismatch for " + name);

        List<String> diffs = new ArrayList<>();
        for (int i = 0; i < galasmFuses.length; i++) {
            if (galasmFuses[i] != oursFuses[i]) {
                diffs.add(String.format("  fuse[%4d]  galasm=%d  ours=%d  (row=%d col=%d)",
                        i, galasmFuses[i], oursFuses[i], i / 44, i % 44));
            }
        }

        if (!diffs.isEmpty()) {
            fail(String.format(
                    "Fuse-map mismatch for '%s': %d fuse(s) differ (fuses 0-%d compared):%n%s",
                    name, diffs.size(), JedecParser.COMPARE_LIMIT - 1,
                    String.join("\n", diffs)));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Runs {@code galasm <pldFile>}. galasm writes its .jed output next to the
     * input file, so {@code expectedJed} must be {@code <pldFile>} with a
     * {@code .jed} extension.
     */
    private static void runGalasm(Path pldFile, Path expectedJed) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(GALASM, pldFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        assertEquals(0, exit,
                "galasm failed for " + pldFile.getFileName() + ":\n" + output);
        assertTrue(Files.exists(expectedJed),
                "galasm did not produce " + expectedJed);
        // Register galasm sidecar files for cleanup
        String base = pldFile.toString().replaceAll("\\.pld$", "");
        for (String ext : new String[]{".jed", ".fus", ".chp", ".pin"}) {
            GALASM_OUTPUTS.add(Paths.get(base + ext));
        }
    }

    /**
     * Runs our gallang compiler: {@code java -jar gallang.jar <galFile> <oursJed>}.
     */
    private static void runGallang(Path galFile, Path oursJed) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", GALLANG_JAR.toAbsolutePath().toString(),
                galFile.toAbsolutePath().toString(),
                oursJed.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        assertEquals(0, exit,
                "gallang failed for " + galFile.getFileName() + ":\n" + output);
        assertTrue(Files.exists(oursJed),
                "gallang did not produce " + oursJed);
    }
}
