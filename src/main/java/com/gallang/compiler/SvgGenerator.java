package com.gallang.compiler;

import com.gallang.ast.expr.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an SVG schematic of a compiled GAL program.
 *
 * Layout: input pins (left) → gate tree → output pin label (right).
 * One row of gates per equation; equations are stacked vertically.
 */
public class SvgGenerator {

    // ── Layout constants ───────────────────────────────────────────────────────
    private static final int COL_W  = 120; // horizontal pixels per depth level
    private static final int ROW_H  = 54;  // vertical pixels per leaf slot
    private static final int GW     = 60;  // gate box width
    private static final int GH     = 30;  // gate box height
    private static final int PAD_X  = 20;  // left margin
    private static final int PAD_Y  = 36;  // top margin
    private static final int EQ_GAP = 44;  // vertical gap between equations

    // ── Internal node representation ──────────────────────────────────────────
    private enum Kind { AND, OR, NOT, IN, OUT, DFF }

    private static final class GNode {
        final Kind        kind;
        final String      label;
        final List<GNode> children = new ArrayList<>();
        double cx, cy;

        GNode(Kind k, String l) { kind = k; label = l; }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public static String generate(LogicCompiler.CompiledProgram prog) {

        // Build one root node per equation
        List<GNode> roots = new ArrayList<>();
        for (LogicCompiler.CompiledEquation eq : prog.equations) {
            GNode root = new GNode(eq.registered ? Kind.DFF : Kind.OUT, eq.signal);
            root.children.add(fromExpr(eq.expr));
            roots.add(root);
        }

        // Max tree depth across all equations (used to right-align outputs)
        int maxDepth = 0;
        for (GNode r : roots) maxDepth = Math.max(maxDepth, depth(r));

        // Assign pixel positions; stack equations vertically
        int curY = PAD_Y;
        for (GNode r : roots) {
            int shift = (maxDepth - depth(r)) * COL_W; // right-align outputs
            layout(r, PAD_X + GW / 2 + shift, curY);
            curY += leaves(r) * ROW_H + EQ_GAP;
        }

        int svgW = PAD_X + GW / 2 + maxDepth * COL_W + 120; // room for output label
        int svgH = curY - EQ_GAP + PAD_Y;

        // Render SVG
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\">\n",
            svgW, svgH));
        sb.append(String.format(
            "  <rect width=\"%d\" height=\"%d\" fill=\"#f8f9fa\" rx=\"4\"/>\n",
            svgW, svgH));
        // Title
        sb.append(String.format(
            "  <text x=\"%d\" y=\"18\" font-family=\"sans-serif\" font-size=\"13\" "
            + "font-weight=\"bold\" fill=\"#374151\">%s — GAL22V10</text>\n",
            PAD_X, xml(prog.deviceName)));

        for (GNode r : roots) renderTree(r, sb);

        sb.append("</svg>\n");
        return sb.toString();
    }

    // ── Expression → tree ──────────────────────────────────────────────────────

    private static GNode fromExpr(Expr e) {
        if (e instanceof VarExpr) {
            return new GNode(Kind.IN, ((VarExpr) e).name);
        }
        if (e instanceof NotExpr) {
            GNode g = new GNode(Kind.NOT, "NOT");
            g.children.add(fromExpr(((NotExpr) e).operand));
            return g;
        }
        if (e instanceof AndExpr) {
            GNode g = new GNode(Kind.AND, "AND");
            g.children.add(fromExpr(((AndExpr) e).left));
            g.children.add(fromExpr(((AndExpr) e).right));
            return g;
        }
        if (e instanceof OrExpr) {
            GNode g = new GNode(Kind.OR, "OR");
            g.children.add(fromExpr(((OrExpr) e).left));
            g.children.add(fromExpr(((OrExpr) e).right));
            return g;
        }
        return new GNode(Kind.IN, "?");
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    /** Number of leaf (IN) nodes in subtree. */
    private static int leaves(GNode n) {
        if (n.children.isEmpty()) return 1;
        int s = 0;
        for (GNode c : n.children) s += leaves(c);
        return s;
    }

    /** Depth of subtree: 0 for leaves, 1 + max(children) otherwise. */
    private static int depth(GNode n) {
        if (n.children.isEmpty()) return 0;
        int m = 0;
        for (GNode c : n.children) m = Math.max(m, depth(c));
        return m + 1;
    }

    /**
     * Assigns cx/cy to every node in the subtree.
     *
     * @param n     current node
     * @param leftX x-origin (where depth-0 leaf nodes sit)
     * @param topY  top of the vertical region allocated to this subtree
     */
    private static void layout(GNode n, int leftX, int topY) {
        n.cx = leftX + depth(n) * COL_W;
        if (n.children.isEmpty()) {
            n.cy = topY + ROW_H / 2.0;
            return;
        }
        int off = 0;
        for (GNode c : n.children) {
            layout(c, leftX, topY + off * ROW_H);
            off += leaves(c);
        }
        // Centre this node vertically between its first and last child
        n.cy = (n.children.get(0).cy
              + n.children.get(n.children.size() - 1).cy) / 2.0;
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    private static void renderTree(GNode n, StringBuilder sb) {
        for (GNode c : n.children) renderTree(c, sb); // children first (wires behind)
        drawWires(n, sb);
        drawGate(n, sb);
    }

    private static void drawWires(GNode n, StringBuilder sb) {
        int    numCh = n.children.size();
        double inX   = inputPortX(n);

        for (int i = 0; i < numCh; i++) {
            GNode  c      = n.children.get(i);
            double outX   = c.cx + GW / 2.0;
            double outY   = c.cy;
            // Spread input ports across the gate's face height
            double spread = numCh > 1
                ? (i - (numCh - 1) / 2.0) * (GH / (double) numCh)
                : 0.0;
            double inY  = n.cy + spread;
            double midX = (outX + inX) / 2.0;
            // Elbow route: horizontal → vertical → horizontal
            sb.append(String.format(
                "  <polyline fill=\"none\" stroke=\"#6b7280\" stroke-width=\"1.5\" "
                + "points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f\"/>\n",
                outX, outY, midX, outY, midX, inY, inX, inY));
        }
    }

    /** X coordinate of a node's left (input) port. */
    private static double inputPortX(GNode n) {
        switch (n.kind) {
            case OUT:  return n.cx;           // no box; wire terminates at cx
            case DFF:  return n.cx - 35;      // left edge of DFF box
            default:   return n.cx - GW / 2.0;
        }
    }

    private static void drawGate(GNode n, StringBuilder sb) {
        double x  = n.cx;
        double y  = n.cy;
        double hw = GW / 2.0;
        double hh = GH / 2.0;

        switch (n.kind) {

            case IN:
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"%d\" height=\"24\" rx=\"5\" "
                    + "fill=\"#dcfce7\" stroke=\"#16a34a\" stroke-width=\"1.2\"/>\n",
                    x - hw, y - 12, GW));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"monospace\" "
                    + "font-size=\"12\" fill=\"#14532d\">%s</text>\n",
                    x, y, xml(n.label)));
                break;

            case AND:
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"%d\" height=\"%d\" rx=\"6\" "
                    + "fill=\"#dbeafe\" stroke=\"#1d4ed8\" stroke-width=\"1.5\"/>\n",
                    x - hw, y - hh, GW, GH));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"sans-serif\" "
                    + "font-size=\"11\" font-weight=\"bold\" fill=\"#1e3a8a\">AND</text>\n",
                    x, y));
                break;

            case OR:
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"%d\" height=\"%d\" rx=\"15\" "
                    + "fill=\"#fef3c7\" stroke=\"#d97706\" stroke-width=\"1.5\"/>\n",
                    x - hw, y - hh, GW, GH));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"sans-serif\" "
                    + "font-size=\"11\" font-weight=\"bold\" fill=\"#92400e\">OR</text>\n",
                    x, y));
                break;

            case NOT:
                // Triangle (pointing right) + inversion bubble
                sb.append(String.format(
                    "  <polygon points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f\" "
                    + "fill=\"#fce7f3\" stroke=\"#be185d\" stroke-width=\"1.5\"/>\n",
                    x - hw, y - hh,   // top-left
                    x - hw, y + hh,   // bottom-left
                    x + hw - 8, y));  // right apex
                sb.append(String.format(
                    "  <circle cx=\"%.1f\" cy=\"%.1f\" r=\"5\" "
                    + "fill=\"white\" stroke=\"#be185d\" stroke-width=\"1.5\"/>\n",
                    x + hw - 3, y));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"sans-serif\" "
                    + "font-size=\"9\" fill=\"#9d174d\">NOT</text>\n",
                    x - 6, y));
                break;

            case DFF:
                // Flip-flop box
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"70\" height=\"50\" rx=\"4\" "
                    + "fill=\"#fff7ed\" stroke=\"#c2410c\" stroke-width=\"1.8\"/>\n",
                    x - 35, y - 25));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"sans-serif\" "
                    + "font-size=\"9\" font-weight=\"bold\" fill=\"#c2410c\">DFF</text>\n",
                    x, y - 11));
                // D / Q labels inside box
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"start\" "
                    + "dominant-baseline=\"middle\" font-family=\"monospace\" "
                    + "font-size=\"10\" fill=\"#555\">D</text>\n",
                    x - 30, y + 2));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"end\" "
                    + "dominant-baseline=\"middle\" font-family=\"monospace\" "
                    + "font-size=\"10\" fill=\"#555\">Q</text>\n",
                    x + 30, y + 2));
                // CLK indicator
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" "
                    + "font-size=\"7\" fill=\"#9ca3af\">&#x25B6;CLK</text>\n",
                    x - 33, y + 19));
                // Output wire + label
                sb.append(String.format(
                    "  <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" "
                    + "stroke=\"#6b7280\" stroke-width=\"1.5\"/>\n",
                    x + 35, y, x + 50, y));
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"60\" height=\"24\" rx=\"4\" "
                    + "fill=\"#f3e8ff\" stroke=\"#7e22ce\" stroke-width=\"1.2\"/>\n",
                    x + 53, y - 12));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"monospace\" "
                    + "font-size=\"12\" font-weight=\"bold\" fill=\"#6b21a8\">%s</text>\n",
                    x + 83, y, xml(n.label)));
                break;

            case OUT:
                // Short wire stub + output label box
                sb.append(String.format(
                    "  <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" "
                    + "stroke=\"#6b7280\" stroke-width=\"1.5\"/>\n",
                    x, y, x + 15, y));
                sb.append(String.format(
                    "  <rect x=\"%.1f\" y=\"%.1f\" width=\"60\" height=\"24\" rx=\"4\" "
                    + "fill=\"#f3e8ff\" stroke=\"#7e22ce\" stroke-width=\"1.2\"/>\n",
                    x + 18, y - 12));
                sb.append(String.format(
                    "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" "
                    + "dominant-baseline=\"middle\" font-family=\"monospace\" "
                    + "font-size=\"12\" font-weight=\"bold\" fill=\"#6b21a8\">%s</text>\n",
                    x + 48, y, xml(n.label)));
                break;
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
