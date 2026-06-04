package vm;

import ast.ASTNode;
import interpreter.Environment;
import interpreter.Ops;
import interpreter.RuntimeErrorJS;
import interpreter.Value;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Стековая виртуальная машина: исполняет байт-код {@link SimpleVmProgram}.
 *
 * Есть общий стек операндов и стек кадров вызова. Каждый кадр держит свой код,
 * указатель инструкции (ip) и окружение {@link Environment} — то же окружение,
 * что и у интерпретатора, поэтому замыкания работают как надо.
 *
 * if/while/for делаются через прыжки: JUMP и JUMP_IF_FALSE ставят ip и идут
 * дальше, остальные инструкции просто двигают ip вперёд.
 */
public final class SimpleVirtualMachine {

    /** Кадр вызова: код функции, текущий ip и окружение. */
    private static final class Frame {
        final List<SimpleVmInstruction> code;
        int ip;
        Environment env;
        Frame(List<SimpleVmInstruction> code, Environment env) {
            this.code = code;
            this.ip = 0;
            this.env = env;
        }
    }

    private SimpleVmProgram program;
    private Deque<Value> stack;
    private Deque<Frame> frames;
    private Frame current;
    private PrintStream out = System.out;

    /** Исполнить программу, печатая вывод вживую (как интерпретатор на этапе 5). */
    public void execute(SimpleVmProgram p) {
        this.program = p;
        this.stack = new ArrayDeque<>();
        this.frames = new ArrayDeque<>();
        this.current = new Frame(p.instructions(), new Environment(null));
        run();
    }

    /** Исполнить и вернуть весь напечатанный вывод строкой (для тестов/сравнения). */
    public String executeCaptured(SimpleVmProgram p) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream prev = this.out;
        try {
            this.out = new PrintStream(baos, true, StandardCharsets.UTF_8);
            execute(p);
        } finally {
            this.out = prev;
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // ==================== Основной цикл ====================

    private void run() {
        while (true) {
            SimpleVmInstruction in = current.code.get(current.ip);
            switch (in.op()) {
                case PUSH_CONST -> push((Value) in.operand());
                case LOAD_VAR -> push(current.env.get((String) in.operand()));
                case STORE_VAR -> {
                    String name = (String) in.operand();
                    Value v = pop();
                    if (!current.env.set(name, v)) current.env.define(name, v);
                }
                case DECLARE_VAR -> current.env.define((String) in.operand(), pop());

                case ENTER_SCOPE -> current.env = new Environment(current.env);
                case LEAVE_SCOPE -> current.env = current.env.parent();

                case ADD -> { Value b = pop(), a = pop(); push(Ops.plus(a, b, null)); }
                case SUB -> { double b = num(pop()), a = num(pop()); push(new Value.NumberVal(a - b)); }
                case MUL -> { double b = num(pop()), a = num(pop()); push(new Value.NumberVal(a * b)); }
                case DIV -> {
                    double b = num(pop()), a = num(pop());
                    if (b == 0) throw new RuntimeErrorJS("Деление на ноль", null);
                    push(new Value.NumberVal(a / b));
                }
                case DIVIDE_INT -> {
                    double b = num(pop()), a = num(pop());
                    if (b == 0) throw new RuntimeErrorJS("Деление на ноль (div)", null);
                    push(new Value.NumberVal((long) (a / b)));
                }
                case MOD -> {
                    double b = num(pop()), a = num(pop());
                    if (b == 0) throw new RuntimeErrorJS("mod от нуля", null);
                    push(new Value.NumberVal(a % b));
                }

                case EQUAL -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(Ops.equalsLoose(a, b))); }
                case NOT_EQUAL -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(!Ops.equalsLoose(a, b))); }
                case LESS_THAN -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(Ops.compare(a, b, null) < 0)); }
                case GREATER_THAN -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(Ops.compare(a, b, null) > 0)); }
                case LESS_EQUAL -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(Ops.compare(a, b, null) <= 0)); }
                case GREATER_EQUAL -> { Value b = pop(), a = pop(); push(new Value.BooleanVal(Ops.compare(a, b, null) >= 0)); }

                case NEGATE -> push(new Value.NumberVal(-num(pop())));
                case NOT -> push(new Value.BooleanVal(!Ops.truthy(pop())));

                case POP -> pop();

                case JUMP -> { current.ip = (int) in.operand(); continue; }
                case JUMP_IF_FALSE -> {
                    if (!Ops.truthy(pop())) { current.ip = (int) in.operand(); continue; }
                }
                case JUMP_IF_FALSE_KEEP -> {
                    if (!Ops.truthy(peek())) { current.ip = (int) in.operand(); continue; }
                }
                case JUMP_IF_TRUE_KEEP -> {
                    if (Ops.truthy(peek())) { current.ip = (int) in.operand(); continue; }
                }

                case PRINT -> doPrint((int) in.operand());

                case NEW_ARRAY -> doNewArray((int) in.operand());
                case NEW_OBJECT -> doNewObject(in.operand());
                case GET_INDEX -> { Value idx = pop(), obj = pop(); push(indexGet(obj, idx)); }
                case SET_INDEX -> { Value idx = pop(), obj = pop(), val = pop(); indexSet(obj, idx, val); }
                case GET_PROP -> push(propGet(pop(), (String) in.operand()));
                case SET_PROP -> {
                    Value obj = pop(), val = pop();
                    if (obj instanceof Value.ObjectVal o) o.fields().put((String) in.operand(), val);
                    else throw new RuntimeErrorJS("Присваивание свойства не объекту", null);
                }

                case MAKE_FUNCTION ->
                        push(new Value.FunctionVal((ASTNode.FunctionDecl) in.operand(), current.env));
                case CALL -> { doCall((int) in.operand()); continue; }
                case RET -> { if (doReturn()) continue; else return; }
                case HALT -> { return; }
            }
            current.ip++;
        }
    }

    // ==================== Вызовы функций ====================

    private void doCall(int argc) {
        Value[] args = new Value[argc];
        for (int k = argc - 1; k >= 0; k--) args[k] = pop();
        Value calleeV = pop();
        if (!(calleeV instanceof Value.FunctionVal fv)) {
            throw new RuntimeErrorJS("Вызов не-функции", null);
        }
        SimpleVmFunction fn = program.function(fv.decl());
        if (fn == null) throw new RuntimeErrorJS("Нет кода функции " + fv.decl().name(), null);

        Environment frameEnv = new Environment(fv.closure());
        List<String> params = fv.decl().params();
        for (int k = 0; k < params.size(); k++) {
            frameEnv.define(params.get(k), k < argc ? args[k] : Value.UndefinedVal.INSTANCE);
        }

        current.ip++;               // после возврата продолжим со следующей инструкции
        frames.push(current);
        current = new Frame(fn.code(), frameEnv);
    }

    /** true — продолжить исполнение у вызывающего; false — остановить машину. */
    private boolean doReturn() {
        Value rv = pop();
        if (frames.isEmpty()) { push(rv); return false; }
        current = frames.pop();
        push(rv);
        return true;
    }

    // ==================== Структуры данных ====================

    private void doPrint(int n) {
        Value[] vals = new Value[n];
        for (int k = n - 1; k >= 0; k--) vals[k] = pop();
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            if (k > 0) sb.append(' ');
            sb.append(vals[k].display());
        }
        out.println(sb);
    }

    private void doNewArray(int n) {
        List<Value> els = new ArrayList<>(n);
        for (int k = 0; k < n; k++) els.add(Value.UndefinedVal.INSTANCE);
        for (int k = n - 1; k >= 0; k--) els.set(k, pop());
        push(new Value.ArrayVal(els));
    }

    private void doNewObject(Object operand) {
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) operand;
        int n = keys.size();
        Value[] vals = new Value[n];
        for (int k = n - 1; k >= 0; k--) vals[k] = pop();
        Map<String, Value> map = new LinkedHashMap<>();
        for (int k = 0; k < n; k++) map.put(keys.get(k), vals[k]);
        push(new Value.ObjectVal(map));
    }

    // как в Interpreter.evalMember
    private static Value propGet(Value obj, String prop) {
        if (obj instanceof Value.ObjectVal o) {
            Value v = o.fields().get(prop);
            return v == null ? Value.UndefinedVal.INSTANCE : v;
        }
        if (obj instanceof Value.ArrayVal a && prop.equals("length")) {
            return new Value.NumberVal(a.elements().size());
        }
        if (obj instanceof Value.StringVal s && prop.equals("length")) {
            return new Value.NumberVal(s.v().length());
        }
        return Value.UndefinedVal.INSTANCE;
    }

    // как в Interpreter.evalIndex
    private static Value indexGet(Value obj, Value idx) {
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

    // как в Interpreter.doAssign (IndexAccess)
    private static void indexSet(Value obj, Value idx, Value val) {
        if (obj instanceof Value.ArrayVal arr && idx instanceof Value.NumberVal n) {
            int i = (int) n.v();
            while (arr.elements().size() <= i) arr.elements().add(Value.UndefinedVal.INSTANCE);
            arr.elements().set(i, val);
        } else if (obj instanceof Value.ObjectVal o && idx instanceof Value.StringVal s) {
            o.fields().put(s.v(), val);
        } else {
            throw new RuntimeErrorJS("Не удаётся присвоить по индексу", null);
        }
    }

    // ==================== Стек ====================

    private void push(Value v) { stack.push(v); }
    private Value pop() {
        if (stack.isEmpty()) throw new RuntimeErrorJS("Стек пуст (ошибка байт-кода)", null);
        return stack.pop();
    }
    private Value peek() {
        Value v = stack.peek();
        if (v == null) throw new RuntimeErrorJS("Стек пуст (ошибка байт-кода)", null);
        return v;
    }
    private double num(Value v) { return Ops.num(v, null); }
}
