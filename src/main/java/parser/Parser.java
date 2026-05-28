package parser;

import ast.ASTNode;
import ast.ASTNode.*;
import diagnostic.Diagnostic;
import lexer.Token;
import lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Синтаксический анализатор (парсер) методом рекурсивного спуска.
 *
 * НАПИСАН ВРУЧНУЮ (без ANTLR / генераторов).
 *
 * Принцип работы:
 * Каждому нетерминалу грамматики соответствует метод (parseProgram, parseStatement,
 * parseExpr и т.д.). Метод читает токены из потока и строит соответствующий узел AST.
 * Приоритет операций реализован через цепочку вызовов:
 *   parseExpr → parseAssignment → parseOr → parseAnd → parseComparison
 *   → parseAddSub → parseMulDiv → parseUnary → parsePostfix → parsePrimary
 *
 * Каждый уровень обрабатывает операции своего приоритета и делегирует
 * подвыражения более высоким уровням.
 */
public class Parser {
    private final List<Token> tokens;
    private int pos;
    private final List<String> errors = new ArrayList<>();
    private final List<String> sourceLines;

    public Parser(List<Token> tokens) {
        this(tokens, null);
    }

    public Parser(List<Token> tokens, List<String> sourceLines) {
        this.tokens = tokens;
        this.pos = 0;
        this.sourceLines = sourceLines;
    }

    public List<String> getErrors() {
        return errors;
    }

    // ==================== Точка входа ====================

    /**
     * program → statement* EOF
     */
    public ASTNode parseProgram() {
        List<ASTNode> statements = new ArrayList<>();
        while (!check(TokenType.EOF)) {
            int before = pos; // запоминаем позицию для защиты от зацикливания
            try {
                statements.add(parseStatement());
            } catch (ParseException e) {
                errors.add(e.getMessage());
                synchronize();
                // Защита: если позиция не сдвинулась — принудительно пропускаем токен
                if (pos == before) {
                    advance();
                }
            }
        }
        return new Program(statements);
    }

    // ==================== Инструкции (Statements) ====================

    /**
     * statement → varDeclStmt | functionDecl | returnStmt | ifStmt | whileStmt
     *           | forStmt | breakStmt | continueStmt | printStmt | block | exprStmt
     */
    private ASTNode parseStatement() {
        // let x = ...;
        if (check(TokenType.LET)) {
            return parseVarDeclStmt();
        }
        // function name(...) { ... }
        if (check(TokenType.FUNCTION)) {
            return parseFunctionDecl();
        }
        // return expr;
        if (check(TokenType.RETURN)) {
            return parseReturnStmt();
        }
        // if (expr) { ... }
        if (check(TokenType.IF)) {
            return parseIfStmt();
        }
        // while (expr) { ... }
        if (check(TokenType.WHILE)) {
            return parseWhileStmt();
        }
        // for (...) { ... }
        if (check(TokenType.FOR)) {
            return parseForStmt();
        }
        // break;
        if (check(TokenType.BREAK)) {
            SourcePos pos = currentPos();
            advance();
            expect(TokenType.SEMICOLON, "';' после 'break'");
            return new BreakStatement(pos);
        }
        // continue;
        if (check(TokenType.CONTINUE)) {
            SourcePos pos = currentPos();
            advance();
            expect(TokenType.SEMICOLON, "';' после 'continue'");
            return new ContinueStatement(pos);
        }
        // console.log(...)
        if (check(TokenType.CONSOLE)) {
            return parsePrintStmt();
        }
        // { ... }
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        // expr;
        return parseExpressionStmt();
    }

    /**
     * varDeclStmt → 'let' ID ('=' expr)? ';'
     */
    private ASTNode parseVarDeclStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.LET, "'let'");
        String name = expect(TokenType.IDENTIFIER, "имя переменной").getValue();
        ASTNode init = null;
        if (match(TokenType.ASSIGN)) {
            init = parseExpr();
        }
        expect(TokenType.SEMICOLON, "';' после объявления переменной");
        return new VarDecl(name, init, pos);
    }

    /**
     * varDecl → 'let' ID ('=' expr)?   (без точки с запятой — для for)
     */
    private ASTNode parseVarDecl() {
        SourcePos pos = currentPos();
        expect(TokenType.LET, "'let'");
        String name = expect(TokenType.IDENTIFIER, "имя переменной").getValue();
        ASTNode init = null;
        if (match(TokenType.ASSIGN)) {
            init = parseExpr();
        }
        return new VarDecl(name, init, pos);
    }

    /**
     * functionDecl → 'function' ID '(' paramList? ')' block
     */
    private ASTNode parseFunctionDecl() {
        SourcePos pos = currentPos();
        expect(TokenType.FUNCTION, "'function'");
        String name = expect(TokenType.IDENTIFIER, "имя функции").getValue();
        expect(TokenType.LPAREN, "'(' после имени функции");
        List<String> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            params.add(expect(TokenType.IDENTIFIER, "имя параметра").getValue());
            while (match(TokenType.COMMA)) {
                params.add(expect(TokenType.IDENTIFIER, "имя параметра").getValue());
            }
        }
        expect(TokenType.RPAREN, "')' после параметров");
        ASTNode body = parseBlock();
        return new FunctionDecl(name, params, body, pos);
    }

    /**
     * returnStmt → 'return' expr? ';'
     */
    private ASTNode parseReturnStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.RETURN, "'return'");
        ASTNode value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpr();
        }
        expect(TokenType.SEMICOLON, "';' после 'return'");
        return new ReturnStatement(value, pos);
    }

    /**
     * ifStmt → 'if' '(' expr ')' block ('else' block)?
     */
    private ASTNode parseIfStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.IF, "'if'");
        expect(TokenType.LPAREN, "'(' после 'if'");
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN, "')' после условия if");
        ASTNode thenBranch = parseBlock();
        ASTNode elseBranch = null;
        if (match(TokenType.ELSE)) {
            if (check(TokenType.IF)) {
                elseBranch = parseIfStmt();
            } else {
                elseBranch = parseBlock();
            }
        }
        return new IfStatement(condition, thenBranch, elseBranch, pos);
    }

    /**
     * whileStmt → 'while' '(' expr ')' block
     */
    private ASTNode parseWhileStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.WHILE, "'while'");
        expect(TokenType.LPAREN, "'(' после 'while'");
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN, "')' после условия while");
        ASTNode body = parseBlock();
        return new WhileStatement(condition, body, pos);
    }

    /**
     * forStmt → 'for' '(' (varDecl | expr)? ';' expr? ';' expr? ')' block
     */
    private ASTNode parseForStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.FOR, "'for'");
        expect(TokenType.LPAREN, "'(' после 'for'");

        // Инициализация
        ASTNode init = null;
        if (!check(TokenType.SEMICOLON)) {
            if (check(TokenType.LET)) {
                init = parseVarDecl();
            } else {
                init = parseExpr();
            }
        }
        expect(TokenType.SEMICOLON, "';' после инициализации for");

        // Условие
        ASTNode condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpr();
        }
        expect(TokenType.SEMICOLON, "';' после условия for");

        // Обновление
        ASTNode update = null;
        if (!check(TokenType.RPAREN)) {
            update = parseExpr();
        }
        expect(TokenType.RPAREN, "')' после for");

        ASTNode body = parseBlock();
        return new ForStatement(init, condition, update, body, pos);
    }

    /**
     * printStmt → 'console' '.' 'log' '(' exprList? ')' ';'
     */
    private ASTNode parsePrintStmt() {
        SourcePos pos = currentPos();
        expect(TokenType.CONSOLE, "'console'");
        expect(TokenType.DOT, "'.'");
        Token logToken = expect(TokenType.IDENTIFIER, "'log'");
        if (!"log".equals(logToken.getValue())) {
            throw error("Ожидалось 'log' после 'console.'");
        }
        expect(TokenType.LPAREN, "'('");
        List<ASTNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpr());
            while (match(TokenType.COMMA)) {
                args.add(parseExpr());
            }
        }
        expect(TokenType.RPAREN, "')'");
        expect(TokenType.SEMICOLON, "';' после console.log(...)");
        return new PrintStatement(args, pos);
    }

    /**
     * block → '{' statement* '}'
     */
    private ASTNode parseBlock() {
        expect(TokenType.LBRACE, "'{'");
        List<ASTNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            int before = pos;
            try {
                stmts.add(parseStatement());
            } catch (ParseException e) {
                errors.add(e.getMessage());
                synchronize();
                if (pos == before) advance();
            }
        }
        expect(TokenType.RBRACE, "'}'");
        return new Block(stmts);
    }

    /**
     * exprStmt → expr ';'
     */
    private ASTNode parseExpressionStmt() {
        ASTNode expr = parseExpr();
        expect(TokenType.SEMICOLON, "';' после выражения");
        return new ExpressionStatement(expr);
    }

    // ==================== Выражения (Expressions) ====================
    // Приоритет операций (от низкого к высокому):
    // 1. Присваивание: =
    // 2. Логическое ИЛИ: ||
    // 3. Логическое И: &&
    // 4. Сравнение: ==, !=, <, >, <=, >=
    // 5. Сложение/Вычитание: +, -
    // 6. Умножение/Деление: *, /, div, mod
    // 7. Унарные: !, -
    // 8. Постфиксные: ., (), []
    // 9. Первичные: литералы, идентификаторы, скобки, массивы, объекты

    /**
     * expr → assignment
     */
    public ASTNode parseExpr() {
        return parseAssignment();
    }

    /**
     * assignment → ID '=' assignment | or
     *
     * Присваивание правоассоциативно: a = b = 5 → a = (b = 5)
     */
    private ASTNode parseAssignment() {
        ASTNode expr = parseOr();

        if (check(TokenType.ASSIGN)) {
            SourcePos pos = currentPos();
            advance();
            ASTNode value = parseAssignment(); // правоассоциативность

            if (expr instanceof Identifier
                    || expr instanceof MemberAccess
                    || expr instanceof IndexAccess) {
                return new Assignment(expr, value, pos);
            }
            error("Левая часть присваивания должна быть идентификатором, свойством или элементом массива");
        }
        return expr;
    }

    /**
     * or → and ('||' and)*
     */
    private ASTNode parseOr() {
        ASTNode left = parseAnd();
        while (check(TokenType.OR)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode right = parseAnd();
            left = new BinaryExpr(left, op, right, pos);
        }
        return left;
    }

    /**
     * and → comparison ('&&' comparison)*
     */
    private ASTNode parseAnd() {
        ASTNode left = parseComparison();
        while (check(TokenType.AND)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode right = parseComparison();
            left = new BinaryExpr(left, op, right, pos);
        }
        return left;
    }

    /**
     * comparison → addSub (('==' | '!=' | '<' | '>' | '<=' | '>=') addSub)*
     */
    private ASTNode parseComparison() {
        ASTNode left = parseAddSub();
        while (check(TokenType.EQ) || check(TokenType.NEQ) ||
               check(TokenType.LT) || check(TokenType.GT) ||
               check(TokenType.LE) || check(TokenType.GE)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode right = parseAddSub();
            left = new BinaryExpr(left, op, right, pos);
        }
        return left;
    }

    /**
     * addSub → mulDiv (('+' | '-') mulDiv)*
     */
    private ASTNode parseAddSub() {
        ASTNode left = parseMulDiv();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode right = parseMulDiv();
            left = new BinaryExpr(left, op, right, pos);
        }
        return left;
    }

    /**
     * mulDiv → unary (('*' | '/' | 'div' | 'mod') unary)*
     */
    private ASTNode parseMulDiv() {
        ASTNode left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) ||
               check(TokenType.DIV_KW) || check(TokenType.MOD_KW)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode right = parseUnary();
            left = new BinaryExpr(left, op, right, pos);
        }
        return left;
    }

    /**
     * unary → ('!' | '-') unary | postfix
     */
    private ASTNode parseUnary() {
        if (check(TokenType.NOT) || check(TokenType.MINUS)) {
            SourcePos pos = currentPos();
            String op = advance().getValue();
            ASTNode operand = parseUnary();
            return new UnaryExpr(op, operand, pos);
        }
        return parsePostfix();
    }

    /**
     * postfix → primary ( '.' ID | '(' exprList? ')' | '[' expr ']' )*
     */
    private ASTNode parsePostfix() {
        ASTNode expr = parsePrimary();

        while (true) {
            if (check(TokenType.DOT)) {
                // obj.prop
                SourcePos pos = currentPos();
                advance();
                String prop = expect(TokenType.IDENTIFIER, "имя свойства после '.'").getValue();
                expr = new MemberAccess(expr, prop, pos);
            } else if (check(TokenType.LPAREN)) {
                // func(args)
                SourcePos pos = currentPos();
                advance();
                List<ASTNode> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (match(TokenType.COMMA)) {
                        args.add(parseExpr());
                    }
                }
                expect(TokenType.RPAREN, "')' после аргументов вызова");
                expr = new FunctionCall(expr, args, pos);
            } else if (check(TokenType.LBRACKET)) {
                // arr[index]
                SourcePos pos = currentPos();
                advance();
                ASTNode index = parseExpr();
                expect(TokenType.RBRACKET, "']' после индекса");
                expr = new IndexAccess(expr, index, pos);
            } else {
                break;
            }
        }
        return expr;
    }

    /**
     * primary → NUMBER | STRING | 'true' | 'false' | 'null' | 'undefined'
     *         | IDENTIFIER | '(' expr ')' | '[' exprList? ']' | '{' ... '}'
     */
    private ASTNode parsePrimary() {
        SourcePos pos = currentPos();

        // Числовой литерал
        if (check(TokenType.NUMBER)) {
            double value = Double.parseDouble(advance().getValue());
            return new NumberLiteral(value, pos);
        }
        // Строковый литерал
        if (check(TokenType.STRING)) {
            String raw = advance().getValue();
            // Убираем кавычки: "hello" → hello
            String value = raw.substring(1, raw.length() - 1);
            return new StringLiteral(value, pos);
        }
        // true
        if (check(TokenType.TRUE)) {
            advance();
            return new BooleanLiteral(true, pos);
        }
        // false
        if (check(TokenType.FALSE)) {
            advance();
            return new BooleanLiteral(false, pos);
        }
        // null
        if (check(TokenType.NULL)) {
            advance();
            return new NullLiteral(pos);
        }
        // undefined
        if (check(TokenType.UNDEFINED)) {
            advance();
            return new UndefinedLiteral(pos);
        }
        // Идентификатор
        if (check(TokenType.IDENTIFIER)) {
            String name = advance().getValue();
            return new Identifier(name, pos);
        }
        // Скобки: (expr)
        if (check(TokenType.LPAREN)) {
            advance();
            ASTNode expr = parseExpr();
            expect(TokenType.RPAREN, "')' после выражения в скобках");
            return expr;
        }
        // Массив: [expr, expr, ...]
        if (check(TokenType.LBRACKET)) {
            advance();
            List<ASTNode> elements = new ArrayList<>();
            if (!check(TokenType.RBRACKET)) {
                elements.add(parseExpr());
                while (match(TokenType.COMMA)) {
                    elements.add(parseExpr());
                }
            }
            expect(TokenType.RBRACKET, "']' после элементов массива");
            return new ArrayLiteral(elements, pos);
        }
        // Объект: { key: value, ... }
        if (check(TokenType.LBRACE)) {
            advance();
            List<String> keys = new ArrayList<>();
            List<ASTNode> values = new ArrayList<>();
            if (!check(TokenType.RBRACE)) {
                keys.add(expect(TokenType.IDENTIFIER, "ключ объекта").getValue());
                expect(TokenType.COLON, "':' после ключа объекта");
                values.add(parseExpr());
                while (match(TokenType.COMMA)) {
                    keys.add(expect(TokenType.IDENTIFIER, "ключ объекта").getValue());
                    expect(TokenType.COLON, "':' после ключа объекта");
                    values.add(parseExpr());
                }
            }
            expect(TokenType.RBRACE, "'}' после литерала объекта");
            return new ObjectLiteral(keys, values, pos);
        }

        // Ничего не подошло — синтаксическая ошибка
        throw error("Ожидалось выражение, но найдено: " + peek().getValue());
    }

    // ==================== Вспомогательные методы ====================

    /** Текущий токен */
    private Token peek() {
        return tokens.get(pos);
    }

    /** Проверка типа текущего токена */
    private boolean check(TokenType type) {
        return peek().getType() == type;
    }

    /** Если текущий токен нужного типа — съедаем его и возвращаем true */
    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    /** Съесть текущий токен и вернуть его */
    private Token advance() {
        Token t = tokens.get(pos);
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    /** Потребовать токен определённого типа; при несовпадении — ошибка */
    private Token expect(TokenType type, String what) {
        if (check(type)) {
            return advance();
        }
        throw error("Ожидалось " + what + ", но найдено '" + peek().getValue()
                + "' (" + peek().getType() + ")");
    }

    /** Текущая позиция в исходнике для диагностики */
    private SourcePos currentPos() {
        Token t = peek();
        return new SourcePos(t.getLine(), t.getColumn());
    }

    /** Создать исключение с сообщением об ошибке */
    private ParseException error(String message) {
        Token t = peek();
        String full = Diagnostic.format("[ОШИБКА СИНТАКСИСА]", message,
                t.getLine(), t.getColumn(), sourceLines);
        return new ParseException(full);
    }

    /**
     * Восстановление после ошибки (panic mode).
     * Пропускаем токены до ближайшей точки синхронизации:
     * ';', '}', или начало нового statement (if, while, let, ...).
     */
    private void synchronize() {
        while (!check(TokenType.EOF)) {
            // После ';' — хорошая точка для продолжения
            if (peek().getType() == TokenType.SEMICOLON) {
                advance();
                return;
            }
            // После '}' — конец блока
            if (peek().getType() == TokenType.RBRACE) {
                return;
            }
            // Начало нового statement
            switch (peek().getType()) {
                case LET, IF, WHILE, FOR, FUNCTION, RETURN, BREAK, CONTINUE, CONSOLE:
                    return;
                default:
                    advance();
            }
        }
    }

    /** Внутреннее исключение для ошибок разбора */
    private static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
