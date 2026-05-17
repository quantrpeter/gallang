grammar GalLang;

// ── Top-level ────────────────────────────────────────────────────────────────

program
    : pinsSection logicSection EOF
    ;

// ── Pins section ─────────────────────────────────────────────────────────────

pinsSection
    : 'pins' pinAssignment+
    ;

pinAssignment
    : INT '=' IDENT
    ;

// ── Logic section ─────────────────────────────────────────────────────────────

logicSection
    : 'logic' equation+
    ;

equation
    : lhs '=' expr
    ;

// lhs supports "signal" or "signal.r" (registered output)
lhs
    : IDENT (DOT IDENT)?
    ;

// ── Expressions ───────────────────────────────────────────────────────────────
//   Precedence (low → high): OR < AND (*) < NOT < atom

expr
    : expr '+' term   # OrExpr
    | term            # TermExpr
    ;

term
    : term '*' factor  # AndExpr
    | factor           # FactorExpr
    ;

factor
    : '/' factor      # NotExpr
    | '(' expr ')'    # ParenExpr
    | IDENT           # VarExpr
    ;

// ── Lexer rules ───────────────────────────────────────────────────────────────

DOT     : '.' ;
INT     : [0-9]+ ;
IDENT   : [a-zA-Z_][a-zA-Z0-9_]* ;
WS      : [ \t\r\n]+ -> skip ;
COMMENT : '//' ~[\r\n]* -> skip ;
