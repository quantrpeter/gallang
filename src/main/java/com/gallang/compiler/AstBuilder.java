package com.gallang.compiler;

import com.gallang.GalLangBaseVisitor;
import com.gallang.GalLangParser;
import com.gallang.ast.Equation;
import com.gallang.ast.Program;
import com.gallang.ast.expr.*;

import java.util.*;

/**
 * Walks the ANTLR parse tree and produces a Program AST.
 */
public class AstBuilder extends GalLangBaseVisitor<Object> {

    @Override
    public Program visitProgram(GalLangParser.ProgramContext ctx) {
        Map<Integer, String> pinMap = visitPinsSection(ctx.pinsSection());
        List<Equation> equations   = visitLogicSection(ctx.logicSection());
        return new Program(pinMap, equations);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Integer, String> visitPinsSection(GalLangParser.PinsSectionContext ctx) {
        Map<Integer, String> pinMap = new LinkedHashMap<>();
        for (GalLangParser.PinAssignmentContext pa : ctx.pinAssignment()) {
            int    pin  = Integer.parseInt(pa.INT().getText());
            String name = pa.IDENT().getText();
            pinMap.put(pin, name);
        }
        return pinMap;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Equation> visitLogicSection(GalLangParser.LogicSectionContext ctx) {
        List<Equation> equations = new ArrayList<>();
        for (GalLangParser.EquationContext eq : ctx.equation()) {
            equations.add(visitEquation(eq));
        }
        return equations;
    }

    @Override
    public Equation visitEquation(GalLangParser.EquationContext ctx) {
        GalLangParser.LhsContext lhs = ctx.lhs();
        String  signal     = lhs.IDENT(0).getText();
        boolean registered = lhs.IDENT().size() > 1
                             && lhs.IDENT(1).getText().equalsIgnoreCase("r");
        Expr expr = (Expr) visit(ctx.expr());
        return new Equation(signal, registered, expr);
    }

    // ── expr alternatives ────────────────────────────────────────────────────

    @Override
    public Expr visitOrExpr(GalLangParser.OrExprContext ctx) {
        return new OrExpr((Expr) visit(ctx.expr()), (Expr) visit(ctx.term()));
    }

    @Override
    public Expr visitTermExpr(GalLangParser.TermExprContext ctx) {
        return (Expr) visit(ctx.term());
    }

    // ── term alternatives ────────────────────────────────────────────────────

    @Override
    public Expr visitAndExpr(GalLangParser.AndExprContext ctx) {
        return new AndExpr((Expr) visit(ctx.term()), (Expr) visit(ctx.factor()));
    }

    @Override
    public Expr visitFactorExpr(GalLangParser.FactorExprContext ctx) {
        return (Expr) visit(ctx.factor());
    }

    // ── factor alternatives ──────────────────────────────────────────────────

    @Override
    public Expr visitNotExpr(GalLangParser.NotExprContext ctx) {
        return new NotExpr((Expr) visit(ctx.factor()));
    }

    @Override
    public Expr visitParenExpr(GalLangParser.ParenExprContext ctx) {
        return (Expr) visit(ctx.expr());
    }

    @Override
    public Expr visitVarExpr(GalLangParser.VarExprContext ctx) {
        return new VarExpr(ctx.IDENT().getText());
    }
}
