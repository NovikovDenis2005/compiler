package interpreter;

import ast.ASTNode;

/**
 * Операции и приведения типов miniJS. Вынес их из Interpreter в отдельный класс,
 * чтобы и интерпретатор, и виртуальная машина считали по одним и тем же правилам.
 * Методы статические и от окружения не зависят.
 */
public final class Ops {

    private Ops() {}

    /** Сложение с JS-семантикой: строка с чем угодно — конкатенация, иначе число. */
    public static Value plus(Value l, Value r, ASTNode.SourcePos pos) {
        if (l instanceof Value.StringVal || r instanceof Value.StringVal) {
            return new Value.StringVal(l.display() + r.display());
        }
        if (l instanceof Value.NumberVal ln && r instanceof Value.NumberVal rn) {
            return new Value.NumberVal(ln.v() + rn.v());
        }
        // одна сторона — число, другая — bool/null — приводим к числу
        Double ld = toNumberOrNull(l);
        Double rd = toNumberOrNull(r);
        if (ld != null && rd != null) return new Value.NumberVal(ld + rd);
        throw new RuntimeErrorJS("Сложение несовместимых типов", pos);
    }

    /** Приведение к числу или ошибка. */
    public static double num(Value v, ASTNode.SourcePos pos) {
        Double d = toNumberOrNull(v);
        if (d == null) throw new RuntimeErrorJS(
                "Ожидалось число, получено " + v.display(), pos);
        return d;
    }

    /** Мягкое приведение к числу: null если не приводится. */
    public static Double toNumberOrNull(Value v) {
        return switch (v) {
            case Value.NumberVal n -> n.v();
            case Value.BooleanVal b -> b.v() ? 1.0 : 0.0;
            case Value.NullVal nl -> 0.0;
            case Value.StringVal s -> {
                try { yield Double.parseDouble(s.v()); }
                catch (NumberFormatException e) { yield null; }
            }
            default -> null;
        };
    }

    /** Сравнение: строки лексикографически, остальное — численно. */
    public static int compare(Value l, Value r, ASTNode.SourcePos pos) {
        if (l instanceof Value.StringVal ls && r instanceof Value.StringVal rs) {
            return ls.v().compareTo(rs.v());
        }
        return Double.compare(num(l, pos), num(r, pos));
    }

    /** Нестрогое равенство ==: одинаковые типы по значению, null == undefined. */
    public static boolean equalsLoose(Value l, Value r) {
        if (l.getClass() == r.getClass()) {
            return switch (l) {
                case Value.NumberVal ln -> ln.v() == ((Value.NumberVal) r).v();
                case Value.StringVal ls -> ls.v().equals(((Value.StringVal) r).v());
                case Value.BooleanVal lb -> lb.v() == ((Value.BooleanVal) r).v();
                case Value.NullVal nl -> true;
                case Value.UndefinedVal un -> true;
                default -> l == r; // ссылочное равенство для массивов/объектов/функций
            };
        }
        // null и undefined считаем равными между собой
        if ((l instanceof Value.NullVal || l instanceof Value.UndefinedVal)
                && (r instanceof Value.NullVal || r instanceof Value.UndefinedVal)) {
            return true;
        }
        return false;
    }

    /** Истинность значения в булевом контексте (как в JS). */
    public static boolean truthy(Value v) {
        return switch (v) {
            case Value.BooleanVal b -> b.v();
            case Value.NumberVal n -> n.v() != 0.0;
            case Value.StringVal s -> !s.v().isEmpty();
            case Value.NullVal nl -> false;
            case Value.UndefinedVal u -> false;
            default -> true;
        };
    }
}
