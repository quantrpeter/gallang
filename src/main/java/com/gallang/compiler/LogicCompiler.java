package com.gallang.compiler;

import com.gallang.ast.Equation;
import com.gallang.ast.Program;
import com.gallang.ast.expr.Expr;

import java.util.*;

/**
 * Validates pin assignments and inlines intermediate signals.
 *
 * GAL22V10 / ATF22V10C pin model:
 *   - Pin 1        : CLK (dedicated input)
 *   - Pins 2-11    : dedicated inputs
 *   - Pin 12       : GND  (fixed)
 *   - Pin 13       : dedicated input (I11)
 *   - Pins 14-23   : I/O cells – these are the output-capable pins
 *   - Pin 24       : VCC  (fixed)
 */
public class LogicCompiler {

    private static final Set<Integer> INPUT_PINS = new HashSet<>(
            Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13));

    private static final Set<Integer> OUTPUT_PINS = new HashSet<>(
            Arrays.asList(14, 15, 16, 17, 18, 19, 20, 21, 22, 23));

    // ── Result types ─────────────────────────────────────────────────────────

    public static class CompiledEquation {
        public final int     pin;
        public final String  signal;
        public final boolean registered;
        public final Expr    expr;

        public CompiledEquation(int pin, String signal, boolean registered, Expr expr) {
            this.pin        = pin;
            this.signal     = signal;
            this.registered = registered;
            this.expr       = expr;
        }
    }

    public static class CompiledProgram {
        public final Map<Integer, String>    pinMap;
        public final List<CompiledEquation>  equations;
        public final String                  deviceName;

        public CompiledProgram(Map<Integer, String> pinMap,
                               List<CompiledEquation> equations,
                               String deviceName) {
            this.pinMap     = pinMap;
            this.equations  = equations;
            this.deviceName = deviceName;
        }
    }

    // ── Compiler entry point ─────────────────────────────────────────────────

    public static CompiledProgram compile(Program program, String deviceName) {
        // Reverse map: signal name → pin number
        Map<String, Integer> signalToPin = new HashMap<>();
        for (Map.Entry<Integer, String> e : program.pinMap.entrySet()) {
            signalToPin.put(e.getValue(), e.getKey());
        }

        // Split equations into intermediates (no pin) vs. output equations
        Map<String, Expr> intermediates = new LinkedHashMap<>();
        List<Equation>    outputs       = new ArrayList<>();

        for (Equation eq : program.equations) {
            if (!signalToPin.containsKey(eq.signal)) {
                intermediates.put(eq.signal, eq.expr);
            } else {
                outputs.add(eq);
            }
        }

        // Iteratively inline intermediates until stable (handles chains)
        int maxIter = intermediates.size() + 1;
        for (int i = 0; i < maxIter; i++) {
            Map<String, Expr> next = new LinkedHashMap<>();
            for (Map.Entry<String, Expr> e : intermediates.entrySet()) {
                next.put(e.getKey(), e.getValue().inline(intermediates));
            }
            if (next.equals(intermediates)) break;
            intermediates = next;
        }

        // Compile each output equation
        List<CompiledEquation> compiled = new ArrayList<>();
        for (Equation eq : outputs) {
            int pin = signalToPin.get(eq.signal);

            if (INPUT_PINS.contains(pin)) {
                System.err.println("Warning: '" + eq.signal + "' is on input-only pin " + pin
                        + " – skipping. Use pins 14-23 for outputs.");
                continue;
            }
            if (!OUTPUT_PINS.contains(pin)) {
                System.err.println("Warning: Pin " + pin + " is not a valid I/O pin – skipping.");
                continue;
            }

            Expr inlined = eq.expr.inline(intermediates);
            compiled.add(new CompiledEquation(pin, eq.signal, eq.registered, inlined));
        }

        return new CompiledProgram(program.pinMap, compiled, deviceName);
    }
}
