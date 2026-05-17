package com.gallang.compiler;

import com.gallang.ast.expr.*;

import java.util.*;

/**
 * Converts a Boolean expression tree into Sum-of-Products (SOP) form.
 *
 * Each product term is a list of Literals.  De Morgan's law is applied
 * recursively so that NOT is always pushed down to leaves.
 */
public class SopConverter {

    public static List<List<Literal>> toSop(Expr expr) {
        return toSopInner(expr, false);
    }

    private static List<List<Literal>> toSopInner(Expr expr, boolean negated) {

        if (expr instanceof VarExpr) {
            return List.of(List.of(new Literal(((VarExpr) expr).name, negated)));
        }

        if (expr instanceof NotExpr) {
            // NOT flips the polarity context
            return toSopInner(((NotExpr) expr).operand, !negated);
        }

        if (expr instanceof OrExpr) {
            OrExpr or = (OrExpr) expr;
            if (!negated) {
                // (A + B) → [[A], [B]] (just concatenate the two SOP lists)
                List<List<Literal>> result = new ArrayList<>();
                result.addAll(toSopInner(or.left,  false));
                result.addAll(toSopInner(or.right, false));
                return result;
            } else {
                // /(A + B) = /A * /B  (De Morgan)
                return multiply(toSopInner(or.left, true), toSopInner(or.right, true));
            }
        }

        if (expr instanceof AndExpr) {
            AndExpr and = (AndExpr) expr;
            if (!negated) {
                // (A * B) → Cartesian product of the two SOP lists
                return multiply(toSopInner(and.left, false), toSopInner(and.right, false));
            } else {
                // /(A * B) = /A + /B  (De Morgan)
                List<List<Literal>> result = new ArrayList<>();
                result.addAll(toSopInner(and.left,  true));
                result.addAll(toSopInner(and.right, true));
                return result;
            }
        }

        throw new UnsupportedOperationException("Unknown expression type: " + expr.getClass().getName());
    }

    /** Cartesian product of two SOP lists: used to AND two sub-expressions. */
    private static List<List<Literal>> multiply(List<List<Literal>> a, List<List<Literal>> b) {
        List<List<Literal>> result = new ArrayList<>();
        for (List<Literal> la : a) {
            for (List<Literal> lb : b) {
                List<Literal> combined = new ArrayList<>(la);
                combined.addAll(lb);
                result.add(combined);
            }
        }
        return result;
    }
}
