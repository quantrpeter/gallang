package com.gallang.ast.expr;

import java.util.Map;

public class NotExpr extends Expr {
    public final Expr operand;

    public NotExpr(Expr operand) { this.operand = operand; }

    @Override
    public String toGalasm() {
        if (operand instanceof VarExpr) {
            return "/" + operand.toGalasm();
        }
        return "/(" + operand.toGalasm() + ")";
    }

    @Override
    public Expr inline(Map<String, Expr> intermediates) {
        return new NotExpr(operand.inline(intermediates));
    }
}
