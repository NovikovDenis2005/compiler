package semantic;

import ast.ASTNode;
import ast.ASTNode.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Семантический анализ AST одним проходом.
 *
 * Что проверяется:
 *   - использование необъявленной переменной
 *   - повторное объявление имени в одной и той же области
 *   - break / continue вне цикла
 *   - return вне функции
 *   - литеральные ошибки типов в арифметике (null + null, "abc" * 2 и т.п.)
 *
 * Побочный продукт — карта типов для констант-фолдинга в AstTransformer.
 */
public class SemanticAnalyzer {

    private final List<SemanticError> errors = new ArrayList<>();
    // IdentityHashMap — иначе два литерала "0" с разной позицией стянутся в один ключ
    private final Map<ASTNode, Type> typeMap = new IdentityHashMap<>();

    private Scope currentScope;
    private Scope globalScope;
    private int loopDepth = 0;
    private int functionDepth = 0;

    public AnalysisResult analyze(Program program) {
        globalScope = new Scope(null);
        currentScope = globalScope;

        // Сначала «прохайстим» функции верхнего уровня — чтобы можно было
        // вызывать функцию до её объявления (как в JavaScript)
        for (ASTNode s : program.statements()) {
            if (s instanceof FunctionDecl f) {
                declareFunction(f);
            }
        }

        for (ASTNode s : program.statements()) {
            visit(s);
        }

        return new AnalysisResult(errors, typeMap, globalScope);
    }

    // ==================== Обход AST ====================

    private void visit(ASTNode node) {
        if (node == null) return;
        switch (node) {
            case Program p -> {
                for (ASTNode s : p.statements()) visit(s);
            }
            case Block b -> {
                Scope saved = currentScope;
                currentScope = new Scope(saved);
                for (ASTNode s : b.statements()) visit(s);
                currentScope = saved;
            }
            case VarDecl v -> visitVarDecl(v);
            case Assignment a -> visitAssignment(a);
            case IfStatement i -> {
                visit(i.condition());
                visit(i.thenBranch());
                if (i.elseBranch() != null) visit(i.elseBranch());
            }
            case WhileStatement w -> {
                visit(w.condition());
                loopDepth++;
                visit(w.body());
                loopDepth--;
            }
            case ForStatement f -> {
                // for-инициализатор живёт в собственной мини-области
                Scope saved = currentScope;
                currentScope = new Scope(saved);
                if (f.init() != null) visit(f.init());
                if (f.condition() != null) visit(f.condition());
                if (f.update() != null) visit(f.update());
                loopDepth++;
                visit(f.body());
                loopDepth--;
                currentScope = saved;
            }
            case BreakStatement br -> {
                if (loopDepth == 0) {
                    errors.add(new SemanticError("break вне цикла", br.pos()));
                }
            }
            case ContinueStatement c -> {
                if (loopDepth == 0) {
                    errors.add(new SemanticError("continue вне цикла", c.pos()));
                }
            }
            case PrintStatement p -> {
                for (ASTNode arg : p.arguments()) visit(arg);
            }
            case FunctionDecl f -> {
                // На верхнем уровне уже объявлена; вложенные функции — здесь
                if (currentScope != globalScope) {
                    declareFunction(f);
                }
                visitFunctionBody(f);
            }
            case ReturnStatement r -> {
                if (functionDepth == 0) {
                    errors.add(new SemanticError("return вне функции", r.pos()));
                }
                if (r.value() != null) visit(r.value());
            }
            case ExpressionStatement e -> visit(e.expression());

            // Выражения — здесь проверки типов и заполнение typeMap
            case BinaryExpr b -> visitBinary(b);
            case UnaryExpr u -> visitUnary(u);
            case FunctionCall c -> visitCall(c);
            case MemberAccess m -> {
                visit(m.object());
                typeMap.put(m, Type.ANY);
            }
            case IndexAccess ix -> {
                visit(ix.object());
                visit(ix.index());
                typeMap.put(ix, Type.ANY);
            }

            case NumberLiteral n -> typeMap.put(n, Type.NUMBER);
            case StringLiteral s -> typeMap.put(s, Type.STRING);
            case BooleanLiteral bl -> typeMap.put(bl, Type.BOOLEAN);
            case NullLiteral nl -> typeMap.put(nl, Type.NULL);
            case UndefinedLiteral ul -> typeMap.put(ul, Type.UNDEFINED);
            case Identifier id -> visitIdentifier(id);
            case ArrayLiteral a -> {
                for (ASTNode el : a.elements()) visit(el);
                typeMap.put(a, Type.ARRAY);
            }
            case ObjectLiteral o -> {
                for (ASTNode v : o.values()) visit(v);
                typeMap.put(o, Type.OBJECT);
            }
        }
    }

    // ==================== Отдельные обработчики ====================

    private void visitVarDecl(VarDecl v) {
        if (currentScope.resolveLocal(v.name()) != null) {
            errors.add(new SemanticError(
                    "Повторное объявление переменной '" + v.name() + "'",
                    v.pos()));
        } else {
            currentScope.declare(new Symbol(v.name(), Symbol.Kind.VAR, v.pos()));
        }
        if (v.init() != null) visit(v.init());
    }

    private void visitAssignment(Assignment a) {
        // Цель присваивания: либо идентификатор, либо член/индекс
        if (a.target() instanceof Identifier id) {
            if (currentScope.resolve(id.name()) == null) {
                errors.add(new SemanticError(
                        "Присваивание необъявленной переменной '" + id.name() + "'",
                        id.pos()));
            }
            // не вызываем visit(a.target()) — иначе получим вторую такую же ошибку
        } else {
            visit(a.target());
        }
        visit(a.value());
    }

    private void visitIdentifier(Identifier id) {
        if (currentScope.resolve(id.name()) == null) {
            errors.add(new SemanticError(
                    "Использование необъявленной переменной '" + id.name() + "'",
                    id.pos()));
        }
        typeMap.put(id, Type.ANY);
    }

    private void declareFunction(FunctionDecl f) {
        if (currentScope.resolveLocal(f.name()) != null) {
            errors.add(new SemanticError(
                    "Повторное объявление функции '" + f.name() + "'",
                    f.pos()));
            return;
        }
        currentScope.declare(new Symbol(f.name(), Symbol.Kind.FUNCTION, f.pos()));
    }

    private void visitFunctionBody(FunctionDecl f) {
        Scope saved = currentScope;
        currentScope = new Scope(saved);
        for (String p : f.params()) {
            if (currentScope.resolveLocal(p) != null) {
                errors.add(new SemanticError(
                        "Дублирование параметра '" + p + "' в функции '" + f.name() + "'",
                        f.pos()));
            } else {
                currentScope.declare(new Symbol(p, Symbol.Kind.PARAM, f.pos()));
            }
        }
        functionDepth++;
        visit(f.body());
        functionDepth--;
        currentScope = saved;
    }

    private void visitCall(FunctionCall c) {
        visit(c.callee());
        for (ASTNode a : c.arguments()) visit(a);
        typeMap.put(c, Type.ANY);
    }

    // ==================== Проверки операций ====================

    private void visitBinary(BinaryExpr b) {
        visit(b.left());
        visit(b.right());

        Type lt = typeMap.getOrDefault(b.left(), Type.ANY);
        Type rt = typeMap.getOrDefault(b.right(), Type.ANY);
        Type result = inferBinary(b, lt, rt);
        typeMap.put(b, result);
    }

    private Type inferBinary(BinaryExpr b, Type lt, Type rt) {
        String op = b.operator();
        return switch (op) {
            case "+" -> {
                if (lt == Type.NUMBER && rt == Type.NUMBER) yield Type.NUMBER;
                if (lt == Type.STRING || rt == Type.STRING) yield Type.STRING;
                // Если оба статически известны и оба не число и не строка — это ошибка
                if (isLiteralType(lt) && isLiteralType(rt)) {
                    errors.add(new SemanticError(
                            "Операция '+' не определена для " + lt + " и " + rt,
                            b.pos()));
                }
                yield Type.ANY;
            }
            case "-", "*", "/", "div", "mod" -> {
                if (lt == Type.NUMBER && rt == Type.NUMBER) yield Type.NUMBER;
                if (isLiteralType(lt) && isLiteralType(rt)) {
                    errors.add(new SemanticError(
                            "Операция '" + op + "' требует чисел, а получены " + lt + " и " + rt,
                            b.pos()));
                }
                yield Type.NUMBER;
            }
            case "==", "!=" -> Type.BOOLEAN;
            case "<", ">", "<=", ">=" -> {
                boolean okNum = lt == Type.NUMBER && rt == Type.NUMBER;
                boolean okStr = lt == Type.STRING && rt == Type.STRING;
                if (!okNum && !okStr && isLiteralType(lt) && isLiteralType(rt)) {
                    errors.add(new SemanticError(
                            "Сравнение '" + op + "' не определено для " + lt + " и " + rt,
                            b.pos()));
                }
                yield Type.BOOLEAN;
            }
            case "&&", "||" -> Type.ANY;
            default -> Type.ANY;
        };
    }

    private void visitUnary(UnaryExpr u) {
        visit(u.operand());
        Type t = typeMap.getOrDefault(u.operand(), Type.ANY);
        Type result = switch (u.operator()) {
            case "-" -> {
                if (t != Type.NUMBER && t != Type.ANY && isLiteralType(t)) {
                    errors.add(new SemanticError(
                            "Унарный '-' требует число, а получен " + t,
                            u.pos()));
                }
                yield Type.NUMBER;
            }
            case "!" -> Type.BOOLEAN;
            default -> Type.ANY;
        };
        typeMap.put(u, result);
    }

    /** Можно ли по этому типу выносить статическое суждение. */
    private static boolean isLiteralType(Type t) {
        return t != Type.ANY;
    }
}
