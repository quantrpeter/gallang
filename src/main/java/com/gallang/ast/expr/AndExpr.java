package com.gallang.ast.expr;

import java.util.Map;

public class AndExpr extends Expr {
    public final Expr left, right;

    public AndExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Expr inline(Map<String, Expr> intermediates) {
        return new AndExpr(left.inline(intermediates), right.inline(intermediates));
    }
}
