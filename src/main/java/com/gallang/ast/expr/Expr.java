package com.gallang.ast.expr;

import java.util.Map;

public abstract class Expr {
    public abstract String toGalasm();
    public abstract Expr inline(Map<String, Expr> intermediates);
}
