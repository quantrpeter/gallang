package com.gallang.ast.expr;

import java.util.Map;

public class VarExpr extends Expr {
    public final String name;

    public VarExpr(String name) { this.name = name; }

    @Override
    public String toGalasm() { return name; }

    @Override
    public Expr inline(Map<String, Expr> intermediates) {
        return intermediates.getOrDefault(name, this);
    }
}
