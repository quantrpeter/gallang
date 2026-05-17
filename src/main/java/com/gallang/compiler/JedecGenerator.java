package com.gallang.compiler;

import java.util.*;

/**
 * Generates a JEDEC file directly for the GAL22V10 / ATF22V10C.
 *
 * ── Verified fuse-map (cross-checked against galasm outputs) ─────────────────
 *
 * AND array:  132 rows × 44 columns = fuses 0-5807
 *   Fuse 0 = input connected to AND gate
 *   Fuse 1 = input NOT connected  (all-ones row → disabled PT)
 *   Default (*F0) → all 0 → all inputs+complements connected → X·/X = 0 (disabled)
 *
 * Row layout:
 *   Row  0 : AR  (global async-reset PT)
 *   Row  1 : OE  (global output-enable PT; set all-ones → always enabled)
 *   Rows 2-9   : OLMC9 / pin 23 (8 PTs)  ; row 10 = SP
 *   Rows 11-20 : OLMC8 / pin 22 (10 PTs) ; row 21 = SP
 *   Rows 22-33 : OLMC7 / pin 21 (12 PTs) ; row 34 = SP
 *   Rows 35-48 : OLMC6 / pin 20 (14 PTs) ; row 49 = SP
 *   Rows 50-65 : OLMC5 / pin 19 (16 PTs) ; row 66 = SP
 *   Rows 67-82 : OLMC4 / pin 18 (16 PTs) ; row 83 = SP
 *   Rows 84-97 : OLMC3 / pin 17 (14 PTs) ; row 98 = SP
 *   Rows 99-110: OLMC2 / pin 16 (12 PTs) ; row 111 = SP
 *   Rows 112-121: OLMC1 / pin 15 (10 PTs); row 122 = SP
 *   Rows 123-130: OLMC0 / pin 14 (8 PTs) ; row 131 = SP
 *
 * Column layout (44 cols, interleaved input / output-feedback pairs):
 *   0,1  : CLK   (pin  1)
 *   2,3  : O1 fb (pin 23)    4,5  : I1    (pin  2)
 *   6,7  : O2 fb (pin 22)    8,9  : I2    (pin  3)
 *   10,11: O3 fb (pin 21)   12,13 : I3    (pin  4)
 *   14,15: O4 fb (pin 20)   16,17 : I4    (pin  5)
 *   18,19: O5 fb (pin 19)   20,21 : I5    (pin  6)
 *   22,23: O6 fb (pin 18)   24,25 : I6    (pin  7)
 *   26,27: O7 fb (pin 17)   28,29 : I7    (pin  8)
 *   30,31: O8 fb (pin 16)   32,33 : I8    (pin  9)
 *   34,35: O9 fb (pin 15)   36,37 : I9    (pin 10)
 *   38,39: O10fb (pin 14)   40,41 : I10   (pin 11)
 *   42,43: I11   (pin 13)
 *
 * Configuration fuses (5808-5827, 20 bits):
 *   5808     : SYN  = 1 (always)
 *   5809     : AC0  = 1 if any combinatorial output, 0 if all registered
 *   5810-5819: AC1[9..0] for OLMCs 9(pin23)..0(pin14) — 1=comb, 0=reg
 *   5820-5827: XOR[8..1] — 0 = active-high (always 0 here)
 *
 * UES (5828-5891, 64 bits): left as all-0 (not written).
 */
public class JedecGenerator {

    private static final int TOTAL_FUSES  = 5892;
    private static final int AND_ROWS     = 132;
    private static final int AND_COLS     = 44;
    private static final int CONFIG_START = 5808;

    // PT counts per OLMC: index 0 = pin 23, index 9 = pin 14
    private static final int[] PT_COUNTS = {8, 10, 12, 14, 16, 16, 14, 12, 10, 8};

    // ── Public API ────────────────────────────────────────────────────────────

    public static String generate(LogicCompiler.CompiledProgram prog) {
        int[] fuses = new int[TOTAL_FUSES]; // default all-0 (*F0)

        // OE row always all-ones (outputs always enabled)
        setRowAllOnes(fuses, 1);

        // Build signal → pin map (inputs from pinMap + outputs from equations)
        Map<String, Integer> sigToPin = new HashMap<>();
        prog.pinMap.forEach((pin, name) -> sigToPin.put(name, pin));
        for (LogicCompiler.CompiledEquation eq : prog.equations) {
            sigToPin.put(eq.signal, eq.pin);
        }

        int[]   ac1    = new int[10];   // AC1 per OLMC, default 0 (registered)
        boolean hasComb = false;

        for (LogicCompiler.CompiledEquation eq : prog.equations) {
            int olmcIdx  = 23 - eq.pin;                  // 0 for pin 23 … 9 for pin 14
            int firstRow = firstPtRow(olmcIdx);
            int numPts   = PT_COUNTS[olmcIdx];
            int spRow    = firstRow + numPts;

            // Convert expression to SOP
            List<List<Literal>> sop = SopConverter.toSop(eq.expr);
            if (sop.size() > numPts) {
                throw new RuntimeException(
                    "Pin " + eq.pin + " needs " + sop.size() + " product terms, max is " + numPts);
            }

            // Encode each product term row
            for (int i = 0; i < sop.size(); i++) {
                encodePt(fuses, firstRow + i, sop.get(i), sigToPin);
            }

            // SP row explicitly disabled (all-ones)
            setRowAllOnes(fuses, spRow);

            // Track config bits
            if (!eq.registered) {
                hasComb       = true;
                ac1[olmcIdx] = 1;
            }
            // registered outputs leave ac1[olmcIdx] = 0
        }

        // Configuration bits
        fuses[CONFIG_START]     = 1;           // SYN = 1 always
        fuses[CONFIG_START + 1] = hasComb ? 1 : 0;  // AC0
        for (int i = 0; i < 10; i++) {
            fuses[CONFIG_START + 2 + i] = ac1[i];   // AC1[9..0]
        }
        // XOR bits (5820-5827) remain 0 → active-high

        return buildJedecString(fuses, prog.deviceName);
    }

    // ── Fuse-map helpers ─────────────────────────────────────────────────────

    /** Return the first product-term row index for an OLMC. */
    private static int firstPtRow(int olmcIdx) {
        int row = 2; // rows 0=AR, 1=OE are fixed; PTs start at 2
        for (int i = 0; i < olmcIdx; i++) {
            row += PT_COUNTS[i] + 1; // +1 for SP row
        }
        return row;
    }

    private static void setRowAllOnes(int[] fuses, int row) {
        int base = row * AND_COLS;
        Arrays.fill(fuses, base, base + AND_COLS, 1);
    }

    /**
     * Encode one product term into the AND array.
     * Start with all-1 (all disconnected) then connect each literal (set to 0).
     */
    private static void encodePt(int[] fuses, int row,
                                  List<Literal> pt, Map<String, Integer> sigToPin) {
        int base = row * AND_COLS;
        Arrays.fill(fuses, base, base + AND_COLS, 1);   // disconnect all

        for (Literal lit : pt) {
            Integer pin = sigToPin.get(lit.signal);
            if (pin == null) {
                throw new RuntimeException("Unknown signal in expression: '" + lit.signal + "'");
            }
            int col = lit.complement ? compCol(pin) : trueCol(pin);
            fuses[base + col] = 0;   // connect this literal
        }
    }

    /**
     * AND-array column for the TRUE polarity of a signal on the given pin.
     * Complement column = trueCol + 1.
     *
     * Verified against galasm JEDEC output:
     *   pin 2 (I1) → col 4, pin 3 (I2) → col 8, pin 23 feedback → col 2
     */
    static int trueCol(int pin) {
        if (pin == 1)                      return 0;          // CLK
        if (pin >= 2  && pin <= 11)        return 4 * (pin - 1);  // I1-I10
        if (pin == 13)                     return 42;         // I11
        if (pin >= 14 && pin <= 23)        return 94 - 4 * pin;   // Ox feedback
        throw new IllegalArgumentException("No AND-plane column for pin " + pin);
    }

    static int compCol(int pin) {
        return trueCol(pin) + 1;
    }

    // ── JEDEC string builder ─────────────────────────────────────────────────

    private static String buildJedecString(int[] fuses, String designName) {
        StringBuilder body = new StringBuilder();

        // Header comment
        body.append("Generated by gallang\n");
        body.append("Design: ").append(designName).append("\n");
        body.append("Device: GAL22V10 / ATF22V10C\n\n");

        // Mandatory control fields
        body.append("*F0\n");    // default fuse = 0
        body.append("*G0\n");    // security fuse off
        body.append("*QF5892\n"); // total fuse count

        // AND array: write only rows that have at least one '1'
        for (int row = 0; row < AND_ROWS; row++) {
            int base = row * AND_COLS;
            if (rowHasOnes(fuses, base)) {
                body.append(String.format("*L%04d ", base));
                for (int c = 0; c < AND_COLS; c++) body.append(fuses[base + c]);
                body.append('\n');
            }
        }

        // Config fuses (5808-5827)
        if (rowHasOnes(fuses, CONFIG_START)) {
            body.append("*L5808 ");
            for (int i = 0; i < 20; i++) body.append(fuses[CONFIG_START + i]);
            body.append('\n');
        }

        // UES (5828-5891) — leave as default 0, don't write

        // Fuse checksum + end-of-file marker (matches galasm format)
        int fuseCs = fuseChecksum(fuses);
        body.append(String.format("*C%04X\n", fuseCs));
        body.append("*\n");   // end-of-file marker

        // Build the full transmission: STX \n <body> ETX <file_checksum>
        String bodyStr = body.toString();
        int fileCs = fileChecksum(bodyStr);

        return "\u0002\n" + bodyStr + "\u0003" + String.format("%04x", fileCs);
    }

    private static boolean rowHasOnes(int[] fuses, int base) {
        int len = (base == CONFIG_START) ? 20 : AND_COLS;
        for (int i = 0; i < len; i++) {
            if (fuses[base + i] == 1) return true;
        }
        return false;
    }

    /**
     * JEDEC fuse checksum: group fuses into 8-bit bytes (LSB first),
     * sum the byte values, truncate to 16 bits.
     */
    private static int fuseChecksum(int[] fuses) {
        int sum = 0;
        for (int i = 0; i < TOTAL_FUSES; i += 8) {
            int byteVal = 0;
            for (int b = 0; b < 8; b++) {
                int idx = i + b;
                if (idx < TOTAL_FUSES) byteVal |= (fuses[idx] << b);
            }
            sum = (sum + byteVal) & 0xFFFF;
        }
        return sum;
    }

    /**
     * JEDEC transmission checksum: ASCII sum of STX(0x02) + '\n' + body + ETX(0x03),
     * truncated to 16 bits. The body string here does NOT yet include STX/ETX.
     */
    private static int fileChecksum(String body) {
        int sum = 0x02 + '\n';   // STX + the newline that follows it
        for (char c : body.toCharArray()) sum += c;
        sum += 0x03;             // ETX
        return sum & 0xFFFF;
    }
}
