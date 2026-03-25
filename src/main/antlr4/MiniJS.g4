grammar MiniJS;

// =============================================
// Формальная грамматика подмножества JavaScript
// (Язык 3 из задания)
// =============================================

// Корень программы
program: statement* EOF;

// Блок кода
block: '{' statement* '}';

// Инструкции (Statements)
statement
    : varDecl ';'                                           # varDeclStmt
    | functionDecl                                          # funcDeclStmt
    | 'return' expr? ';'                                    # returnStmt
    | expr ';'                                              # expressionStmt
    | 'if' '(' expr ')' block ('else' block)?               # ifStmt
    | 'while' '(' expr ')' block                            # whileStmt
    | 'for' '(' (varDecl | expr)? ';' expr? ';' expr? ')' block  # forStmt
    | 'break' ';'                                           # breakStmt
    | 'continue' ';'                                        # continueStmt
    | 'console.log' '(' exprList? ')' ';'                   # printStmt
    | block                                                 # blockStmt
    ;

// Объявление переменных
varDecl: 'let' ID ('=' expr)?;

// Объявление функций
functionDecl: 'function' ID '(' paramList? ')' block;

paramList: ID (',' ID)*;
exprList: expr (',' expr)*;

// Выражения с приоритетом операций
expr
    : '(' expr ')'                                          # parenExpr
    | expr '.' ID                                           # memberExpr
    | expr '(' exprList? ')'                                # callExpr
    | expr '[' expr ']'                                     # indexExpr
    | ('!' | '-') expr                                      # unaryExpr
    | expr ('*' | '/' | 'div' | 'mod') expr                 # mulDivExpr
    | expr ('+' | '-') expr                                 # addSubExpr
    | expr ('>' | '<' | '>=' | '<=' | '==' | '!=') expr     # compareExpr
    | expr '&&' expr                                         # logicalAndExpr
    | expr '||' expr                                         # logicalOrExpr
    | ID '=' expr                                           # assignExpr
    | '[' exprList? ']'                                     # arrayExpr
    | '{' (ID ':' expr (',' ID ':' expr)*)? '}'             # objectExpr
    | ID                                                    # idExpr
    | NUMBER                                                # numberExpr
    | STRING                                                # stringExpr
    | 'true'                                                # trueExpr
    | 'false'                                               # falseExpr
    | 'null'                                                # nullExpr
    | 'undefined'                                           # undefinedExpr
    ;

// Лексер
NUMBER: [0-9]+ ('.' [0-9]+)?;
STRING: '"' (~["\\] | '\\' .)* '"'
      | '\'' (~['\\] | '\\' .)* '\'';
ID: [a-zA-Z_$][a-zA-Z0-9_$]*;

WS: [ \t\r\n]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;
