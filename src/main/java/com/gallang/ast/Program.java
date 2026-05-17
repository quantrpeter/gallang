package com.gallang.ast;

import java.util.List;
import java.util.Map;

public class Program {
    public final Map<Integer, String> pinMap;   // pin number → signal name
    public final List<Equation> equations;

    public Program(Map<Integer, String> pinMap, List<Equation> equations) {
        this.pinMap = pinMap;
        this.equations = equations;
    }
}
