package com.gallang.ast;

import com.gallang.ast.expr.Expr;

public class Equation {
    public final String signal;
    public final boolean registered;   // true if ".r" suffix was used
    public final Expr expr;

    public Equation(String signal, boolean registered, Expr expr) {
        this.signal = signal;
        this.registered = registered;
        this.expr = expr;
    }
}
