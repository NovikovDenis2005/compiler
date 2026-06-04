package vm;

import ast.ASTNode;
import interpreter.Value;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Скомпилированная программа для виртуальной машины: имя, объявленные
 * переменные (для шапки дизассемблера), байт-код верхнего уровня (main) и
 * таблица функций (по их объявлению из AST).
 *
 * Ещё печатает дизассемблер — каждая строка с префиксом {@code [VM][BYTECODE]}.
 */
public final class SimpleVmProgram {

    private String name = "main";
    private final List<String> declaredVars = new ArrayList<>();
    private final List<SimpleVmInstruction> instructions = new ArrayList<>();
    /** Функция по её объявлению; LinkedHashMap — для стабильного порядка вывода. */
    private final Map<ASTNode.FunctionDecl, SimpleVmFunction> functions = new LinkedHashMap<>();

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> declaredVars() { return declaredVars; }
    public List<SimpleVmInstruction> instructions() { return instructions; }
    public Map<ASTNode.FunctionDecl, SimpleVmFunction> functions() { return functions; }

    public SimpleVmFunction function(ASTNode.FunctionDecl decl) { return functions.get(decl); }

    // ==================== Дизассемблер ====================

    private static final String P = "[VM][BYTECODE] ";

    public void printDisassembly(PrintStream out) {
        out.print(disassemble());
    }

    public String disassemble() {
        StringBuilder sb = new StringBuilder();
        sb.append(P).append("Program: ").append(name).append('\n');

        sb.append(P).append("Variables:").append('\n');
        if (declaredVars.isEmpty()) {
            sb.append(P).append("  (нет)").append('\n');
        } else {
            for (String v : declaredVars) sb.append(P).append("  - ").append(v).append('\n');
        }

        sb.append(P).append("Instructions:").append('\n');
        appendCode(sb, instructions);

        for (SimpleVmFunction fn : functions.values()) {
            sb.append(P).append('\n');
            sb.append(P).append("Function: ").append(fn.name())
              .append('(').append(String.join(", ", fn.params())).append(')').append('\n');
            appendCode(sb, fn.code());
        }
        return sb.toString();
    }

    private static void appendCode(StringBuilder sb, List<SimpleVmInstruction> code) {
        for (int ip = 0; ip < code.size(); ip++) {
            SimpleVmInstruction in = code.get(ip);
            String operand = formatOperand(in);
            sb.append(P).append(String.format("%04X: %-16s%s", ip, in.op().name(), operand)).append('\n');
        }
    }

    private static String formatOperand(SimpleVmInstruction in) {
        Object o = in.operand();
        if (o == null) return "";
        if (in.op().isJump()) {
            return String.format("0x%X", (Integer) o);
        }
        return switch (in.op()) {
            case PUSH_CONST -> {
                Value v = (Value) o;
                yield v instanceof Value.StringVal s ? "\"" + s.v() + "\"" : v.display();
            }
            case MAKE_FUNCTION -> ((ASTNode.FunctionDecl) o).name();
            case NEW_OBJECT -> {
                @SuppressWarnings("unchecked")
                List<String> keys = (List<String>) o;
                yield String.join(", ", keys);
            }
            default -> String.valueOf(o);
        };
    }
}
