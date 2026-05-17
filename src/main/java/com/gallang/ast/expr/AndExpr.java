package com.gallang.ast.expr;

import java.util.Map;

public class AndExpr extends Expr {
    public final Expr left, right;

    public AndExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public String toGalasm() {
        // Parenthesise OR sub-expressions to preserve precedence
        String l = (left  instanceof OrExpr) ? "(" + left.toGalasm()  + ")" : left.toGalasm();
        String r = (right instanceof OrExpr) ? "(" + right.toGalasm() + ")" : right.toGalasm();
        return l + " * " + r;
    }

    @Override
    public Expr inline(Map<String, Expr> intermediates) {
        return new AndExpr(left.inline(intermediates), right.inline(intermediates));
    }
}
