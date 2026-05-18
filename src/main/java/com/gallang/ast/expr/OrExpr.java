package com.gallang.ast.expr;

import java.util.Map;

public class OrExpr extends Expr {
    public final Expr left, right;

    public OrExpr(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Expr inline(Map<String, Expr> intermediates) {
        return new OrExpr(left.inline(intermediates), right.inline(intermediates));
    }
}
