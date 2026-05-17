package com.gallang.compiler;

/** A literal in a product term: signal name plus polarity. */
public class Literal {
    public final String  signal;
    public final boolean complement;   // true = /signal, false = signal

    public Literal(String signal, boolean complement) {
        this.signal     = signal;
        this.complement = complement;
    }
}
