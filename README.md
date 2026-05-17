# GALLANG

![GALLANG banner](image/2026-05-17T12_25_40.png)

> **Logic in design, Power in code.**  
> A domain-specific language for programming GAL22V10 / ATF22V10C programmable logic devices — created by the **Hong Kong Programming Society (HKPS)**.

GALLANG compiles `.gal` source files directly to JEDEC `.jed` files ready to burn with `minipro`. No intermediate tools (galasm, WinCUPL, etc.) are required.

---

## Table of Contents

- [GALLANG](#gallang)
	- [Table of Contents](#table-of-contents)
	- [Target Device](#target-device)
	- [Requirements](#requirements)
	- [Build](#build)
	- [Usage](#usage)
	- [Language Reference](#language-reference)
		- [File Structure](#file-structure)
		- [Pins Section](#pins-section)
		- [Logic Section](#logic-section)
		- [Operators](#operators)
		- [Intermediate Signals](#intermediate-signals)
		- [Registered Outputs](#registered-outputs)
	- [Examples](#examples)
		- [AND / OR Combinatorial Logic](#and--or-combinatorial-logic)
		- [D Flip-Flop (Registered Output)](#d-flip-flop-registered-output)
		- [Mixed: Intermediate + Combinatorial](#mixed-intermediate--combinatorial)
	- [Product Term Limits](#product-term-limits)
	- [GAL22V10 Pin Map](#gal22v10-pin-map)
	- [Programming the Device](#programming-the-device)
	- [Project Architecture](#project-architecture)
		- [Compilation Pipeline](#compilation-pipeline)
		- [JEDEC Fuse Map (GAL22V10)](#jedec-fuse-map-gal22v10)

---

## Target Device

| Device | Package | I/O | Fuses | Notes |
|--------|---------|-----|-------|-------|
| GAL22V10 | DIP-24 | 10 configurable OLMCs | 5892 | Lattice original |
| ATF22V10C | DIP-24 | 10 configurable OLMCs | 5892 | Atmel/Microchip clone, 100% pin-compatible |

Both devices are 5 V EEPROM-based programmable logic, erased and reprogrammed in-circuit with a device programmer such as `minipro` + TL866.

---

## Requirements

| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 11 or later | Compile & run gallang |
| Apache Maven | 3.6+ | Build the fat jar |
| minipro | any | Burn `.jed` to the device |

---

## Build

```bash
git clone <repo>
cd gallang
mvn package
```

This produces `target/gallang.jar` — a self-contained fat jar with all dependencies bundled.

---

## Usage

```bash
java -jar target/gallang.jar <input.gal> [output.jed]
```

| Argument | Description |
|----------|-------------|
| `input.gal` | Source file written in the gallang language |
| `output.jed` | Optional. Defaults to `<input>.jed` next to the source file |

**Example:**

```bash
java -jar target/gallang.jar example/example.gal
# → writes example/example.jed
```

**Burn to device:**

```bash
minipro -p ATF22V10C -w example/example.jed
```

---

## Language Reference

### File Structure

A `.gal` file has exactly two sections, in order:

```
pins
  <pin assignments>

logic
  <boolean equations>
```

Comments use `//` and run to end-of-line.

---

### Pins Section

Maps physical pin numbers to signal names.

```
pins
1=clk
2=A  3=B  4=C     // multiple assignments per line are fine
23=out  22=carry
```

**Fixed pins** (cannot be reassigned):

| Pin | Function |
|-----|----------|
| 1   | CLK — dedicated clock input for registered outputs |
| 12  | GND |
| 24  | VCC |

**Input-only pins:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13  
**Output (OLMC) pins:** 14, 15, 16, 17, 18, 19, 20, 21, 22, 23

---

### Logic Section

Defines the Boolean function for each output pin.

```
logic
out = A * B + C    // combinatorial: out = (A AND B) OR C
```

Each output signal must appear in the `pins` section and be mapped to an output pin (14–23). All other signals in equations are either inputs from the `pins` section, or **intermediate** signals that are automatically inlined.

---

### Operators

| Operator | Symbol | Precedence | Description |
|----------|--------|------------|-------------|
| OR | `+` | lowest | Logical OR |
| AND | `*` | middle | Logical AND |
| NOT | `/` | highest | Logical NOT (prefix) |
| Grouping | `( )` | — | Override precedence |

**Operator examples:**

```
// AND has higher precedence than OR:
out = A + B * C         // A OR (B AND C)

// Use parentheses to override:
out = (A + B) * C       // (A OR B) AND C

// NOT is prefix, applied to the next atom or group:
out = /A * B            // (NOT A) AND B
out = /(A + B)          // NOT (A OR B)  → De Morgan: /A * /B
```

---

### Intermediate Signals

Any signal used in an equation but **not** listed in the `pins` section is treated as an intermediate (local) signal and is **automatically inlined** into the equations that use it.

```
pins
2=A  3=B  4=C
23=out

logic
sum = A * /B + /A * B   // intermediate: XOR
out = sum * C           // inlined → (A*/B + /A*B) * C
```

Chains of intermediates are resolved iteratively. Circular definitions are not permitted.

---

### Registered Outputs

Add `.r` (or `.R`) to the left-hand side to make an output **registered** (clocked D flip-flop, triggered on the rising edge of CLK / pin 1).

```
logic
Q.r = D          // Q is the flip-flop output; D is its next-state input
```

The equation defines the **D input** of the flip-flop. The device's CLK pin (pin 1) must be connected to a clock signal.

Feedback: registered outputs can be read back in other equations (using the signal's name directly):

```
Q.r = /Q          // toggle flip-flop: Q inverts on every clock edge
```

---

## Examples

### AND / OR Combinatorial Logic

```gal
// example/andtest.gal
pins
2=A  3=B
23=Y  22=Z

logic
Y = A * B    // AND gate
Z = A + B    // OR gate
```

```bash
java -jar target/gallang.jar example/andtest.gal
```

---

### D Flip-Flop (Registered Output)

```gal
// example/dfftest.gal
pins
1=clock
2=D
23=Q

logic
Q.r = D      // rising-edge D flip-flop
```

```bash
java -jar target/gallang.jar example/dfftest.gal
```

---

### Mixed: Intermediate + Combinatorial

```gal
// example/example.gal
pins
1=clock
4=a  5=b  10=c
23=out

logic
z   = a + b*c    // intermediate: a OR (b AND c) — inlined automatically
out = z          // combinatorial output
```

---

## Product Term Limits

Each output OLMC has a fixed number of AND-plane rows (product terms). The total per-output limit is:

| Output Pin | Max Product Terms |
|------------|-------------------|
| 23         | 8                 |
| 22         | 10                |
| 21         | 12                |
| 20         | 14                |
| 19         | 16                |
| 18         | 16                |
| 17         | 14                |
| 16         | 12                |
| 15         | 10                |
| 14         | 8                 |

The compiler will report an error if a Boolean expression expands to more product terms than the OLMC supports. Place functions with many OR terms on pins 18 or 19.

---

## GAL22V10 Pin Map

```
        ┌────── DIP-24 ──────┐
  CLK   │  1              24 │  VCC
   I1   │  2              23 │  O1 / I11 (OLMC)
   I2   │  3              22 │  O2 / I10 (OLMC)
   I3   │  4              21 │  O3 / I9  (OLMC)
   I4   │  5              20 │  O4 / I8  (OLMC)
   I5   │  6              19 │  O5 / I7  (OLMC)
   I6   │  7              18 │  O6 / I6  (OLMC)
   I7   │  8              17 │  O7 / I5  (OLMC)
   I8   │  9              16 │  O8 / I4  (OLMC)
   I9   │ 10              15 │  O9 / I3  (OLMC)
  I10   │ 11              14 │  O10/ I2  (OLMC)
  GND   │ 12              13 │  I11
        └────────────────────┘
```

- **OLMC pins (14–23):** configurable as combinatorial output, registered output, or input (feedback)
- **CLK (pin 1):** used automatically when `.r` equations are present
- **Pin 13:** additional dedicated input

---

## Programming the Device

With a TL866II+ or compatible programmer and `minipro` installed:

```bash
# Erase (optional, EEPROM auto-erases on write)
minipro -p ATF22V10C -E

# Program
minipro -p ATF22V10C -w output.jed

# Verify
minipro -p ATF22V10C -m output.jed
```

---

## Project Architecture

```
gallang/
├── pom.xml                                  Maven build (Java 11, ANTLR 4.13.1)
├── example/                                 Sample .gal files and generated .jed
└── src/main/
    ├── antlr4/com/gallang/
    │   └── GalLang.g4                       ANTLR4 grammar
    └── java/com/gallang/
        ├── Main.java                        Entry point
        ├── ast/
        │   ├── Program.java                 Top-level AST node
        │   ├── Equation.java                Single Boolean equation
        │   └── expr/
        │       ├── Expr.java                Abstract expression
        │       ├── VarExpr.java             Signal reference
        │       ├── AndExpr.java             A * B
        │       ├── OrExpr.java              A + B
        │       └── NotExpr.java             /A
        └── compiler/
            ├── AstBuilder.java              ANTLR visitor → AST
            ├── LogicCompiler.java           Inline intermediates, validate pins
            ├── SopConverter.java            Expr tree → Sum-of-Products
            ├── Literal.java                 Single literal in a product term
            ├── JedecGenerator.java          SOP → JEDEC fuse map (direct, no galasm)
            └── PldEmitter.java              (legacy) GALasm .pld text emitter
```

### Compilation Pipeline

```
.gal source
    │
    ▼ ANTLR4 lexer + parser (GalLang.g4)
Parse Tree
    │
    ▼ AstBuilder (visitor)
AST  (Program → Equation → Expr tree)
    │
    ▼ LogicCompiler
      • Inline intermediate signals
      • Validate pin assignments
CompiledProgram  (pin → SOP-ready Expr)
    │
    ▼ SopConverter
      • De Morgan normalization
      • Expand to List<List<Literal>>
Sum-of-Products
    │
    ▼ JedecGenerator
      • AND-array row encoding (132 rows × 44 cols)
      • OLMC config bits (SYN, AC0, AC1, XOR)
      • JEDEC checksums (fuse + file)
.jed  ──► minipro ──► GAL22V10 / ATF22V10C
```

### JEDEC Fuse Map (GAL22V10)

The 5892-fuse array is structured as follows:

| Region | Fuses | Description |
|--------|-------|-------------|
| AND array | 0 – 5807 | 132 rows × 44 columns product terms |
| Config | 5808 – 5827 | SYN, AC0, AC1[9:0], XOR[8:1] |
| UES | 5828 – 5891 | User Electronic Signature (64-bit label) |

Row 0 is the global async-reset PT; row 1 is the global output-enable PT. The 10 OLMCs (pins 23 down to 14) follow, each with their own product-term rows and a synchronous-preset row.
