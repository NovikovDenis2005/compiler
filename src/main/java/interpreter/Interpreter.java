package interpreter;

import ast.ASTNode;
import ast.ASTNode.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AST-интерпретатор miniJS.
 *
 * Управление потоком (break / continue / return) реализовано через
 * непроверяемые исключения-сигналы. Это нормальная техника для древесных
 * интерпретаторов: корректное место их обработки — это узлы цикла и тело
 * функции, и стек размотается ровно туда, куда нужно.
 *
 * Передача параметров — по значению (для массивов и объектов это значит
 * разделяемая ссылка, как в JS).
 */
public class Interpreter {

    private final Environment globals = new Environment(null);

    // ==================== Сигналы управления потоком ====================

    private static final class BreakSignal extends RuntimeException {
        BreakSignal() { super(null, null, false, false); }
    }
    private static final class ContinueSignal extends RuntimeException {
        ContinueSignal() { super(null, null, false, false); }
    }
    private static final class ReturnSignal extends RuntimeException {
        final Value value;
        ReturnSignal(Value v) { super(null, null, false, false); this.value = v; }
    }

    private static final BreakSignal BREAK = new BreakSignal();
    private static final ContinueSignal CONTINUE = new ContinueSignal();

    // ==================== Точка входа ====================

    public void run(Program program) {
        // Проход по верхнему уровню: функции цепляем заранее (как в семантике)
        for (ASTNode s : program.statements()) {
            if (s instanceof FunctionDecl f) {
                globals.define(f.name(), new Value.FunctionVal(f, globals));
            }
        }
        for (ASTNode s : program.statements()) {
            if (!(s instanceof FunctionDecl)) {
                exec(s, globals);
            }
        }
    }

    // ==================== Инструкции ====================

    private void exec(ASTNode node, Environment env) {
        switch (node) {
            case Block b -> {
                Environment inner = new Environment(env);
                for (ASTNode s : b.statements()) exec(s, inner);
            }
            case VarDecl v -> {
                Value init = v.init() == null
                        ? Value.UndefinedVal.INSTANCE
                        : eval(v.init(), env);
                env.define(v.name(), init);
            }
            case Assignment a -> doAssign(a, env);
            case IfStatement i -> {
                if (truthy(eval(i.condition(), env))) {
                    exec(i.thenBranch(), env);
                } else if (i.elseBranch() != null) {
                    exec(i.elseBranch(), env);
                }
            }
            case WhileStatement w -> {
                while (truthy(eval(w.condition(), env))) {
                    try { exec(w.body(), env); }
                    catch (BreakSignal br) { break; }
                    catch (ContinueSignal c) { /* следующая итерация */ }
                }
            }
            case ForStatement f -> {
                Environment loop = new Environment(env);
                if (f.init() != null) exec(f.init(), loop);
                while (f.condition() == null || truthy(eval(f.condition(), loop))) {
                    try { exec(f.body(), loop); }
                    catch (BreakSignal br) { break; }
                    catch (ContinueSignal c) { /* fallthrough к update */ }
                    if (f.update() != null) eval(f.update(), loop);
                }
            }
            case BreakStatement br -> { throw BREAK; }
            case ContinueStatement c -> { throw CONTINUE; }
            case PrintStatement p -> doPrint(p, env);
            case FunctionDecl f -> env.define(f.name(), new Value.FunctionVal(f, env));
            case ReturnStatement r -> {
                Value v = r.value() == null
                        ? Value.UndefinedVal.INSTANCE
                        : eval(r.value(), env);
                throw new ReturnSignal(v);
            }
            case ExpressionStatement e -> eval(e.expression(), env);
            default -> eval(node, env); // на случай выражения как «инструкции»
        }
    }

    private void doAssign(Assignment a, Environment env) {
        Value rhs = eval(a.value(), env);
        switch (a.target()) {
            case Identifier id -> {
                if (!env.set(id.name(), rhs)) {
                    // в семантике это уже отлавливается, но на всякий
                    env.define(id.name(), rhs);
                }
            }
            case MemberAccess m -> {
                Value obj = eval(m.object(), env);
                if (obj instanceof Value.ObjectVal o) {
                    o.fields().put(m.property(), rhs);
                } else {
                    throw new RuntimeErrorJS(
                            "Присваивание свойства не объекту", m.pos());
                }
            }
            case IndexAccess ix -> {
                Value obj = eval(ix.object(), env);
                Value idx = eval(ix.index(), env);
                if (obj instanceof Value.ArrayVal arr && idx instanceof Value.NumberVal n) {
                    int i = (int) n.v();
                    while (arr.elements().size() <= i) {
                        arr.elements().add(Value.UndefinedVal.INSTANCE);
                    }
                    arr.elements().set(i, rhs);
                } else if (obj instanceof Value.ObjectVal o && idx instanceof Value.StringVal s) {
                    o.fields().put(s.v(), rhs);
                } else {
                    throw new RuntimeErrorJS(
                            "Не удаётся присвоить по индексу", ix.pos());
                }
            }
            default -> throw new RuntimeErrorJS(
                    "Недопустимая цель присваивания", a.pos());
        }
    }

    private void doPrint(PrintStatement p, Environment env) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.arguments().size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(eval(p.arguments().get(i), env).display());
        }
        System.out.println(sb);
    }

    // ==================== Выражения ====================

    private Value eval(ASTNode node, Environment env) {
        return switch (node) {
            case NumberLiteral n -> new Value.NumberVal(n.value());
            case StringLiteral s -> new Value.StringVal(s.value());
            case BooleanLiteral b -> new Value.BooleanVal(b.value());
            case NullLiteral nl -> Value.NullVal.INSTANCE;
            case UndefinedLiteral ul -> Value.UndefinedVal.INSTANCE;
            case Identifier id -> env.get(id.name());

            case ArrayLiteral a -> {
                List<Value> els = new ArrayList<>(a.elements().size());
                for (ASTNode el : a.elements()) els.add(eval(el, env));
                yield new Value.ArrayVal(els);
            }
            case ObjectLiteral o -> {
                Map<String, Value> map = new LinkedHashMap<>();
                for (int i = 0; i < o.keys().size(); i++) {
                    map.put(o.keys().get(i), eval(o.values().get(i), env));
                }
                yield new Value.ObjectVal(map);
            }

            case BinaryExpr b -> evalBinary(b, env);
            case UnaryExpr u -> evalUnary(u, env);
            case FunctionCall c -> evalCall(c, env);
            case MemberAccess m -> evalMember(m, env);
            case IndexAccess ix -> evalIndex(ix, env);

            // Выражение-инструкция или присваивание как выражение — пусть exec разберёт
            case Assignment a -> {
                doAssign(a, env);
                yield eval(a.target(), env);
            }
            default -> throw new RuntimeErrorJS(
                    "Не выражение: " + node.getClass().getSimpleName(), null);
        };
    }

    private Value evalBinary(BinaryExpr b, Environment env) {
        // Логические — короткое замыкание, считаем левое первым
        if (b.operator().equals("&&")) {
            Value l = eval(b.left(), env);
            return truthy(l) ? eval(b.right(), env) : l;
        }
        if (b.operator().equals("||")) {
            Value l = eval(b.left(), env);
            return truthy(l) ? l : eval(b.right(), env);
        }

        Value l = eval(b.left(), env);
        Value r = eval(b.right(), env);

        return switch (b.operator()) {
            case "+" -> plus(l, r, b.pos());
            case "-" -> new Value.NumberVal(num(l, b.pos()) - num(r, b.pos()));
            case "*" -> new Value.NumberVal(num(l, b.pos()) * num(r, b.pos()));
            case "/" -> {
                double rv = num(r, b.pos());
                if (rv == 0) throw new RuntimeErrorJS("Деление на ноль", b.pos());
                yield new Value.NumberVal(num(l, b.pos()) / rv);
            }
            case "div" -> {
                double rv = num(r, b.pos());
                if (rv == 0) throw new RuntimeErrorJS("Деление на ноль (div)", b.pos());
                yield new Value.NumberVal((long) (num(l, b.pos()) / rv));
            }
            case "mod" -> {
                double rv = num(r, b.pos());
                if (rv == 0) throw new RuntimeErrorJS("mod от нуля", b.pos());
                yield new Value.NumberVal(num(l, b.pos()) % rv);
            }
            case "==" -> new Value.BooleanVal(equalsLoose(l, r));
            case "!=" -> new Value.BooleanVal(!equalsLoose(l, r));
            case "<" -> new Value.BooleanVal(compare(l, r, b.pos()) < 0);
            case ">" -> new Value.BooleanVal(compare(l, r, b.pos()) > 0);
            case "<=" -> new Value.BooleanVal(compare(l, r, b.pos()) <= 0);
            case ">=" -> new Value.BooleanVal(compare(l, r, b.pos()) >= 0);
            default -> throw new RuntimeErrorJS(
                    "Неизвестная операция '" + b.operator() + "'", b.pos());
        };
    }

    private Value evalUnary(UnaryExpr u, Environment env) {
        Value v = eval(u.operand(), env);
        return switch (u.operator()) {
            case "-" -> new Value.NumberVal(-num(v, u.pos()));
            case "!" -> new Value.BooleanVal(!truthy(v));
            default -> throw new RuntimeErrorJS(
                    "Неизвестный унарный оператор '" + u.operator() + "'", u.pos());
        };
    }

    private Value evalCall(FunctionCall c, Environment env) {
        // Спец-случай console.log(...) — может быть и FunctionCall(MemberAccess(...))
        if (isConsoleLog(c.callee())) {
            for (int i = 0; i < c.arguments().size(); i++) {
                if (i > 0) System.out.print(" ");
                System.out.print(eval(c.arguments().get(i), env).display());
            }
            System.out.println();
            return Value.UndefinedVal.INSTANCE;
        }

        Value callee = eval(c.callee(), env);
        if (!(callee instanceof Value.FunctionVal fv)) {
            throw new RuntimeErrorJS("Вызов не-функции", c.pos());
        }
        FunctionDecl decl = fv.decl();
        if (c.arguments().size() != decl.params().size()) {
            // в JS это не ошибка, но для аккуратности предупредим через undefined
        }
        Environment frame = new Environment(fv.closure());
        for (int i = 0; i < decl.params().size(); i++) {
            Value arg = i < c.arguments().size()
                    ? eval(c.arguments().get(i), env)
                    : Value.UndefinedVal.INSTANCE;
            frame.define(decl.params().get(i), arg);
        }
        try {
            exec(decl.body(), frame);
        } catch (ReturnSignal r) {
            return r.value;
        }
        return Value.UndefinedVal.INSTANCE;
    }

    private static boolean isConsoleLog(ASTNode callee) {
        return callee instanceof MemberAccess m
                && m.object() instanceof Identifier id
                && id.name().equals("console")
                && m.property().equals("log");
    }

    private Value evalMember(MemberAccess m, Environment env) {
        // console.log как значение — отдадим маркер, чтобы FunctionCall сработал
        if (isConsoleLog(m)) return Value.UndefinedVal.INSTANCE;
        Value obj = eval(m.object(), env);
        if (obj instanceof Value.ObjectVal o) {
            Value v = o.fields().get(m.property());
            return v == null ? Value.UndefinedVal.INSTANCE : v;
        }
        if (obj instanceof Value.ArrayVal a && m.property().equals("length")) {
            return new Value.NumberVal(a.elements().size());
        }
        if (obj instanceof Value.StringVal s && m.property().equals("length")) {
            return new Value.NumberVal(s.v().length());
        }
        return Value.UndefinedVal.INSTANCE;
    }

    private Value evalIndex(IndexAccess ix, Environment env) {
        Value obj = eval(ix.object(), env);
        Value idx = eval(ix.index(), env);
        if (obj instanceof Value.ArrayVal a && idx instanceof Value.NumberVal n) {
            int i = (int) n.v();
            if (i < 0 || i >= a.elements().size()) return Value.UndefinedVal.INSTANCE;
            return a.elements().get(i);
        }
        if (obj instanceof Value.ObjectVal o) {
            String key = idx instanceof Value.StringVal s ? s.v() : idx.display();
            Value v = o.fields().get(key);
            return v == null ? Value.UndefinedVal.INSTANCE : v;
        }
        if (obj instanceof Value.StringVal s && idx instanceof Value.NumberVal n) {
            int i = (int) n.v();
            if (i < 0 || i >= s.v().length()) return Value.UndefinedVal.INSTANCE;
            return new Value.StringVal(String.valueOf(s.v().charAt(i)));
        }
        return Value.UndefinedVal.INSTANCE;
    }

    // ==================== Семантика операций / приведения ====================

    private static Value plus(Value l, Value r, ASTNode.SourcePos pos) {
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

    private static double num(Value v, ASTNode.SourcePos pos) {
        Double d = toNumberOrNull(v);
        if (d == null) throw new RuntimeErrorJS(
                "Ожидалось число, получено " + v.display(), pos);
        return d;
    }

    private static Double toNumberOrNull(Value v) {
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

    private static int compare(Value l, Value r, ASTNode.SourcePos pos) {
        if (l instanceof Value.StringVal ls && r instanceof Value.StringVal rs) {
            return ls.v().compareTo(rs.v());
        }
        return Double.compare(num(l, pos), num(r, pos));
    }

    private static boolean equalsLoose(Value l, Value r) {
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

    private static boolean truthy(Value v) {
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
