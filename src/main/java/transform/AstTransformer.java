package transform;

import ast.ASTNode;
import ast.ASTNode.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Типозависимая модификация AST: свёртка констант + удаление мёртвых веток.
 *
 * Записи (records) неизменяемы, поэтому строим новое дерево «функционально» —
 * для каждой узла собираем новые дочерние и оборачиваем в новый record.
 *
 * Свёртка применяется только когда оба операнда — литералы соответствующих
 * типов. Деление на ноль в свёртку не идёт — пусть упадёт runtime, чтобы
 * пользователь увидел диагностику с позицией.
 */
public final class AstTransformer {

    private AstTransformer() {}

    public static ASTNode transform(ASTNode node) {
        if (node == null) return null;
        return switch (node) {
            case Program p -> new Program(transformList(p.statements()));
            case Block b -> new Block(transformList(b.statements()));

            case VarDecl v -> new VarDecl(v.name(), transform(v.init()), v.pos());
            case Assignment a -> new Assignment(transform(a.target()), transform(a.value()), a.pos());

            case IfStatement i -> foldIf(i);
            case WhileStatement w -> foldWhile(w);
            case ForStatement f -> foldFor(f);

            case BreakStatement br -> br;
            case ContinueStatement c -> c;
            case PrintStatement p -> new PrintStatement(transformList(p.arguments()), p.pos());
            case FunctionDecl fd -> new FunctionDecl(fd.name(), fd.params(), transform(fd.body()), fd.pos());
            case ReturnStatement r -> new ReturnStatement(transform(r.value()), r.pos());
            case ExpressionStatement es -> new ExpressionStatement(transform(es.expression()));

            case BinaryExpr be -> foldBinary(be);
            case UnaryExpr ue -> foldUnary(ue);
            case FunctionCall fc -> new FunctionCall(transform(fc.callee()), transformList(fc.arguments()), fc.pos());
            case MemberAccess m -> new MemberAccess(transform(m.object()), m.property(), m.pos());
            case IndexAccess ix -> new IndexAccess(transform(ix.object()), transform(ix.index()), ix.pos());

            // Литералы и идентификаторы — без изменений
            case NumberLiteral n -> n;
            case StringLiteral s -> s;
            case BooleanLiteral bl -> bl;
            case NullLiteral nl -> nl;
            case UndefinedLiteral ul -> ul;
            case Identifier id -> id;
            case ArrayLiteral a -> new ArrayLiteral(transformList(a.elements()), a.pos());
            case ObjectLiteral o -> new ObjectLiteral(o.keys(), transformList(o.values()), o.pos());
        };
    }

    private static List<ASTNode> transformList(List<ASTNode> list) {
        List<ASTNode> out = new ArrayList<>(list.size());
        for (ASTNode n : list) out.add(transform(n));
        return out;
    }

    // ==================== Свёртка бинарных операций ====================

    private static ASTNode foldBinary(BinaryExpr b) {
        ASTNode left = transform(b.left());
        ASTNode right = transform(b.right());

        // Числа
        if (left instanceof NumberLiteral ln && right instanceof NumberLiteral rn) {
            Double v = applyNumeric(b.operator(), ln.value(), rn.value());
            if (v != null) return new NumberLiteral(v, b.pos());
            Boolean cmp = applyNumberCompare(b.operator(), ln.value(), rn.value());
            if (cmp != null) return new BooleanLiteral(cmp, b.pos());
        }

        // Строки
        if (left instanceof StringLiteral ls && right instanceof StringLiteral rs) {
            switch (b.operator()) {
                case "+" -> { return new StringLiteral(ls.value() + rs.value(), b.pos()); }
                case "==" -> { return new BooleanLiteral(ls.value().equals(rs.value()), b.pos()); }
                case "!=" -> { return new BooleanLiteral(!ls.value().equals(rs.value()), b.pos()); }
                case "<" -> { return new BooleanLiteral(ls.value().compareTo(rs.value()) < 0, b.pos()); }
                case ">" -> { return new BooleanLiteral(ls.value().compareTo(rs.value()) > 0, b.pos()); }
                case "<=" -> { return new BooleanLiteral(ls.value().compareTo(rs.value()) <= 0, b.pos()); }
                case ">=" -> { return new BooleanLiteral(ls.value().compareTo(rs.value()) >= 0, b.pos()); }
            }
        }

        // Конкатенация строки с числом / булем — JS-семантика
        if (b.operator().equals("+")) {
            String s = stringIfConcatable(left, right);
            if (s != null) return new StringLiteral(s, b.pos());
        }

        // Булевы && / || с известным левым
        if (b.operator().equals("&&") && left instanceof BooleanLiteral bl) {
            return bl.value() ? right : bl;
        }
        if (b.operator().equals("||") && left instanceof BooleanLiteral bl) {
            return bl.value() ? bl : right;
        }

        return new BinaryExpr(left, b.operator(), right, b.pos());
    }

    private static Double applyNumeric(String op, double a, double b) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? null : a / b;          // не сворачиваем
            case "div" -> b == 0 ? null : (double) ((long) (a / b));
            case "mod" -> b == 0 ? null : a % b;
            default -> null;
        };
    }

    private static Boolean applyNumberCompare(String op, double a, double b) {
        return switch (op) {
            case "==" -> a == b;
            case "!=" -> a != b;
            case "<" -> a < b;
            case ">" -> a > b;
            case "<=" -> a <= b;
            case ">=" -> a >= b;
            default -> null;
        };
    }

    /** Вернуть результат конкатенации, если хотя бы одна сторона — строка-литерал. */
    private static String stringIfConcatable(ASTNode l, ASTNode r) {
        String ls = literalToStringOrNull(l);
        String rs = literalToStringOrNull(r);
        if (ls == null || rs == null) return null;
        // обе должны быть приводимы, но хотя бы одна — реально строка
        if (l instanceof StringLiteral || r instanceof StringLiteral) {
            return ls + rs;
        }
        return null;
    }

    private static String literalToStringOrNull(ASTNode n) {
        return switch (n) {
            case StringLiteral s -> s.value();
            case NumberLiteral num -> formatNumber(num.value());
            case BooleanLiteral b -> Boolean.toString(b.value());
            case NullLiteral nl -> "null";
            case UndefinedLiteral u -> "undefined";
            default -> null;
        };
    }

    private static String formatNumber(double v) {
        if (v == (long) v) return Long.toString((long) v);
        return Double.toString(v);
    }

    // ==================== Свёртка унарных ====================

    private static ASTNode foldUnary(UnaryExpr u) {
        ASTNode arg = transform(u.operand());
        return switch (u.operator()) {
            case "-" -> arg instanceof NumberLiteral n
                    ? new NumberLiteral(-n.value(), u.pos())
                    : new UnaryExpr("-", arg, u.pos());
            case "!" -> {
                Boolean t = truthy(arg);
                yield t == null
                        ? new UnaryExpr("!", arg, u.pos())
                        : new BooleanLiteral(!t, u.pos());
            }
            default -> new UnaryExpr(u.operator(), arg, u.pos());
        };
    }

    /** truthy/falsy для литералов; null если динамика. */
    private static Boolean truthy(ASTNode n) {
        return switch (n) {
            case BooleanLiteral b -> b.value();
            case NumberLiteral num -> num.value() != 0.0;
            case StringLiteral s -> !s.value().isEmpty();
            case NullLiteral nl -> false;
            case UndefinedLiteral u -> false;
            default -> null;
        };
    }

    // ==================== Удаление мёртвых веток ====================

    private static ASTNode foldIf(IfStatement i) {
        ASTNode cond = transform(i.condition());
        ASTNode then = transform(i.thenBranch());
        ASTNode els = i.elseBranch() == null ? null : transform(i.elseBranch());
        Boolean t = truthy(cond);
        if (t == null) {
            return new IfStatement(cond, then, els, i.pos());
        }
        if (t) return then;
        return els != null ? els : new Block(List.of());
    }

    private static ASTNode foldWhile(WhileStatement w) {
        ASTNode cond = transform(w.condition());
        ASTNode body = transform(w.body());
        Boolean t = truthy(cond);
        if (Boolean.FALSE.equals(t)) {
            return new Block(List.of());
        }
        return new WhileStatement(cond, body, w.pos());
    }

    private static ASTNode foldFor(ForStatement f) {
        ASTNode init = transform(f.init());
        ASTNode cond = transform(f.condition());
        ASTNode upd = transform(f.update());
        ASTNode body = transform(f.body());
        Boolean t = cond == null ? null : truthy(cond);
        if (Boolean.FALSE.equals(t)) {
            // условие заведомо ложное — выполняется только инициализация
            return init == null ? new Block(List.of()) : init;
        }
        return new ForStatement(init, cond, upd, body, f.pos());
    }
}
