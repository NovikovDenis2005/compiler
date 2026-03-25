package ast;

import java.util.List;

/**
 * Иерархия узлов абстрактного синтаксического дерева (AST).
 *
 * Каждый класс — отдельный вид узла дерева. Базовый интерфейс ASTNode
 * позволяет обходить дерево единообразно (паттерн Visitor можно
 * добавить на 2-й аттестации для семантического анализа и кодогенерации).
 */
public sealed interface ASTNode {

    /** Позиция в исходном коде (для диагностики ошибок) */
    record SourcePos(int line, int column) {
        @Override public String toString() { return line + ":" + column; }
    }

    // ==================== Программа ====================

    /** Корень дерева: список инструкций */
    record Program(List<ASTNode> statements) implements ASTNode {}

    // ==================== Инструкции (Statements) ====================

    /** Объявление переменной: let x = expr; */
    record VarDecl(String name, ASTNode init, SourcePos pos) implements ASTNode {}

    /** Присваивание: x = expr; */
    record Assignment(ASTNode target, ASTNode value, SourcePos pos) implements ASTNode {}

    /** Блок: { stmt1; stmt2; ... } */
    record Block(List<ASTNode> statements) implements ASTNode {}

    /** if (cond) { ... } else { ... } */
    record IfStatement(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch, SourcePos pos) implements ASTNode {}

    /** while (cond) { ... } */
    record WhileStatement(ASTNode condition, ASTNode body, SourcePos pos) implements ASTNode {}

    /** for (init; cond; update) { ... } */
    record ForStatement(ASTNode init, ASTNode condition, ASTNode update, ASTNode body, SourcePos pos) implements ASTNode {}

    /** break; */
    record BreakStatement(SourcePos pos) implements ASTNode {}

    /** continue; */
    record ContinueStatement(SourcePos pos) implements ASTNode {}

    /** console.log(expr1, expr2, ...); */
    record PrintStatement(List<ASTNode> arguments, SourcePos pos) implements ASTNode {}

    /** function name(params) { ... } */
    record FunctionDecl(String name, List<String> params, ASTNode body, SourcePos pos) implements ASTNode {}

    /** return expr; */
    record ReturnStatement(ASTNode value, SourcePos pos) implements ASTNode {}

    /** Отдельное выражение как инструкция: expr; */
    record ExpressionStatement(ASTNode expression) implements ASTNode {}

    // ==================== Выражения (Expressions) ====================

    /** Бинарная операция: left op right */
    record BinaryExpr(ASTNode left, String operator, ASTNode right, SourcePos pos) implements ASTNode {}

    /** Унарная операция: op expr (!expr, -expr) */
    record UnaryExpr(String operator, ASTNode operand, SourcePos pos) implements ASTNode {}

    /** Вызов функции: callee(args) */
    record FunctionCall(ASTNode callee, List<ASTNode> arguments, SourcePos pos) implements ASTNode {}

    /** Доступ к свойству: obj.prop */
    record MemberAccess(ASTNode object, String property, SourcePos pos) implements ASTNode {}

    /** Индексация: arr[index] */
    record IndexAccess(ASTNode object, ASTNode index, SourcePos pos) implements ASTNode {}

    // ==================== Литералы ====================

    /** Числовой литерал: 42, 3.14 */
    record NumberLiteral(double value, SourcePos pos) implements ASTNode {}

    /** Строковый литерал: "hello" */
    record StringLiteral(String value, SourcePos pos) implements ASTNode {}

    /** true / false */
    record BooleanLiteral(boolean value, SourcePos pos) implements ASTNode {}

    /** null */
    record NullLiteral(SourcePos pos) implements ASTNode {}

    /** undefined */
    record UndefinedLiteral(SourcePos pos) implements ASTNode {}

    /** Идентификатор (ссылка на переменную) */
    record Identifier(String name, SourcePos pos) implements ASTNode {}

    /** Массив: [1, 2, 3] */
    record ArrayLiteral(List<ASTNode> elements, SourcePos pos) implements ASTNode {}

    /** Объект (хеш): { key: value, ... } */
    record ObjectLiteral(List<String> keys, List<ASTNode> values, SourcePos pos) implements ASTNode {}
}
