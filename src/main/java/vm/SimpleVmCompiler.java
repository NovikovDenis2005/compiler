package vm;

import ast.ASTNode;
import ast.ASTNode.*;
import interpreter.RuntimeErrorJS;
import interpreter.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static vm.SimpleVmOpCode.*;

/**
 * Перевожу оптимизированный AST в байт-код для стековой машины (этап 6).
 *
 * if/while/for собираю из прыжков. Адрес прыжка вперёд заранее не знаю, поэтому
 * сначала ставлю прыжок с заглушкой, а как дойду до нужного места — проставляю
 * адрес (это называется backpatching). break/continue складываю в стек циклов и
 * проставляю, когда цикл закрывается; перед таким прыжком добавляю LEAVE_SCOPE,
 * чтобы выйти из блоков, открытых внутри тела цикла.
 *
 * Арифметику/сравнения считаю через interpreter.Ops — те же правила, что в
 * интерпретаторе.
 */
public final class SimpleVmCompiler {

    /** Контекст цикла для разрешения break/continue. */
    private static final class LoopCtx {
        final int depth;                                   // глубина скоупов на уровне тела цикла
        final List<Integer> breakSites = new ArrayList<>();
        final List<Integer> continueSites = new ArrayList<>();
        LoopCtx(int depth) { this.depth = depth; }
    }

    private SimpleVmProgram program;
    private List<SimpleVmInstruction> code;   // текущий буфер (main или тело функции)
    private Deque<LoopCtx> loops = new ArrayDeque<>();
    private int scopeDepth = 0;               // вложенность ENTER_SCOPE/LEAVE_SCOPE

    public SimpleVmProgram compile(ASTNode node) {
        if (!(node instanceof Program p)) {
            throw new RuntimeErrorJS("Ожидалась программа для компиляции", null);
        }
        program = new SimpleVmProgram();
        code = program.instructions();

        // Шапка дизассемблера: объявленные на верхнем уровне имена.
        for (ASTNode s : p.statements()) {
            if (s instanceof FunctionDecl f) program.declaredVars().add(f.name() + " : function");
            else if (s instanceof VarDecl v) program.declaredVars().add(v.name() + " : var");
        }

        // Хойстинг функций верхнего уровня (как в Interpreter.run): сперва объявляем.
        for (ASTNode s : p.statements()) {
            if (s instanceof FunctionDecl f) {
                registerFunction(f);
                emit(MAKE_FUNCTION, f);
                emit(DECLARE_VAR, f.name());
            }
        }
        // Остальные инструкции верхнего уровня по порядку.
        for (ASTNode s : p.statements()) {
            if (!(s instanceof FunctionDecl)) compileStmt(s);
        }
        emit(HALT);
        return program;
    }

    // ==================== Функции ====================

    private void registerFunction(FunctionDecl f) {
        if (program.functions().containsKey(f)) return;
        // Сохраняем состояние и компилируем тело в отдельный буфер.
        List<SimpleVmInstruction> outerCode = code;
        Deque<LoopCtx> outerLoops = loops;
        int outerDepth = scopeDepth;

        List<SimpleVmInstruction> body = new ArrayList<>();
        code = body;
        loops = new ArrayDeque<>();
        scopeDepth = 0;
        compileStmt(f.body());                      // обычно Block
        emit(PUSH_CONST, Value.UndefinedVal.INSTANCE);  // неявный return undefined
        emit(RET);

        program.functions().put(f, new SimpleVmFunction(f.name(), f.params(), body));

        code = outerCode;
        loops = outerLoops;
        scopeDepth = outerDepth;
    }

    // ==================== Инструкции ====================

    private void compileStmt(ASTNode node) {
        switch (node) {
            case VarDecl v -> {
                if (v.init() == null) emit(PUSH_CONST, Value.UndefinedVal.INSTANCE);
                else compileExpr(v.init());
                emit(DECLARE_VAR, v.name());
            }
            case Assignment a -> compileAssign(a);
            case Block b -> {
                enterScope();
                for (ASTNode s : b.statements()) compileStmt(s);
                leaveScope();
            }
            case IfStatement i -> compileIf(i);
            case WhileStatement w -> compileWhile(w);
            case ForStatement f -> compileFor(f);
            case BreakStatement br -> compileJumpOut(true);
            case ContinueStatement c -> compileJumpOut(false);
            case PrintStatement p -> {
                for (ASTNode arg : p.arguments()) compileExpr(arg);
                emit(PRINT, p.arguments().size());
            }
            case FunctionDecl f -> {
                registerFunction(f);
                emit(MAKE_FUNCTION, f);
                emit(DECLARE_VAR, f.name());
            }
            case ReturnStatement r -> {
                if (r.value() == null) emit(PUSH_CONST, Value.UndefinedVal.INSTANCE);
                else compileExpr(r.value());
                emit(RET);
            }
            case ExpressionStatement e -> {
                compileExpr(e.expression());
                emit(POP);
            }
            default -> {                 // выражение в роли инструкции
                compileExpr(node);
                emit(POP);
            }
        }
    }

    /** Присваивание как инструкция — на стеке ничего не оставляет.
     *  Порядок вычислений повторяет Interpreter.doAssign: сначала значение, потом цель. */
    private void compileAssign(Assignment a) {
        switch (a.target()) {
            case Identifier id -> {
                compileExpr(a.value());
                emit(STORE_VAR, id.name());
            }
            case MemberAccess m -> {
                compileExpr(a.value());     // value
                compileExpr(m.object());    // obj
                emit(SET_PROP, m.property());
            }
            case IndexAccess ix -> {
                compileExpr(a.value());     // value
                compileExpr(ix.object());   // obj
                compileExpr(ix.index());    // index
                emit(SET_INDEX);
            }
            default -> throw new RuntimeErrorJS("Недопустимая цель присваивания", a.pos());
        }
    }

    private void compileIf(IfStatement i) {
        compileExpr(i.condition());
        int jFalse = emitJump(JUMP_IF_FALSE);
        compileStmt(i.thenBranch());
        if (i.elseBranch() != null) {
            int jEnd = emitJump(JUMP);
            patch(jFalse, here());
            compileStmt(i.elseBranch());
            patch(jEnd, here());
        } else {
            patch(jFalse, here());
        }
    }

    private void compileWhile(WhileStatement w) {
        int start = here();
        compileExpr(w.condition());
        int jEnd = emitJump(JUMP_IF_FALSE);

        LoopCtx ctx = new LoopCtx(scopeDepth);
        loops.push(ctx);
        compileStmt(w.body());
        emit(JUMP, start);
        loops.pop();

        int end = here();
        patch(jEnd, end);
        for (int s : ctx.breakSites) patch(s, end);
        for (int s : ctx.continueSites) patch(s, start);
    }

    private void compileFor(ForStatement f) {
        enterScope();                                   // цикл for владеет одним скоупом
        if (f.init() != null) compileStmt(f.init());

        int start = here();
        int jEnd = -1;
        if (f.condition() != null) {
            compileExpr(f.condition());
            jEnd = emitJump(JUMP_IF_FALSE);
        }

        LoopCtx ctx = new LoopCtx(scopeDepth);
        loops.push(ctx);
        compileStmt(f.body());
        int updLabel = here();
        if (f.update() != null) compileStmt(f.update());  // statement-контекст (Assignment ничего не оставляет)
        emit(JUMP, start);
        loops.pop();

        int end = here();
        if (jEnd >= 0) patch(jEnd, end);
        for (int s : ctx.breakSites) patch(s, end);
        for (int s : ctx.continueSites) patch(s, updLabel);
        leaveScope();
    }

    /** break (out=true) / continue (out=false): свернуть блочные скоупы и прыгнуть. */
    private void compileJumpOut(boolean isBreak) {
        LoopCtx ctx = loops.peek();
        if (ctx == null) {  // защита; семантика это уже проверила
            throw new RuntimeErrorJS((isBreak ? "break" : "continue") + " вне цикла", null);
        }
        for (int d = scopeDepth; d > ctx.depth; d--) emit(LEAVE_SCOPE);
        int site = emitJump(JUMP);
        (isBreak ? ctx.breakSites : ctx.continueSites).add(site);
    }

    // ==================== Выражения ====================

    private void compileExpr(ASTNode node) {
        switch (node) {
            case NumberLiteral n -> emit(PUSH_CONST, new Value.NumberVal(n.value()));
            case StringLiteral s -> emit(PUSH_CONST, new Value.StringVal(s.value()));
            case BooleanLiteral b -> emit(PUSH_CONST, new Value.BooleanVal(b.value()));
            case NullLiteral nl -> emit(PUSH_CONST, Value.NullVal.INSTANCE);
            case UndefinedLiteral ul -> emit(PUSH_CONST, Value.UndefinedVal.INSTANCE);
            case Identifier id -> emit(LOAD_VAR, id.name());

            case ArrayLiteral a -> {
                for (ASTNode el : a.elements()) compileExpr(el);
                emit(NEW_ARRAY, a.elements().size());
            }
            case ObjectLiteral o -> {
                for (ASTNode v : o.values()) compileExpr(v);
                emit(NEW_OBJECT, o.keys());
            }

            case BinaryExpr b -> compileBinary(b);
            case UnaryExpr u -> {
                compileExpr(u.operand());
                switch (u.operator()) {
                    case "-" -> emit(NEGATE);
                    case "!" -> emit(NOT);
                    default -> throw new RuntimeErrorJS(
                            "Неизвестный унарный оператор '" + u.operator() + "'", u.pos());
                }
            }
            case FunctionCall c -> {
                compileExpr(c.callee());
                for (ASTNode arg : c.arguments()) compileExpr(arg);
                emit(CALL, c.arguments().size());
            }
            case MemberAccess m -> {
                compileExpr(m.object());
                emit(GET_PROP, m.property());
            }
            case IndexAccess ix -> {
                compileExpr(ix.object());
                compileExpr(ix.index());
                emit(GET_INDEX);
            }

            // присваивание как выражение: присвоить, затем вернуть значение цели
            case Assignment a -> {
                compileAssign(a);
                compileExpr(a.target());
            }
            default -> throw new RuntimeErrorJS(
                    "Не выражение: " + node.getClass().getSimpleName(), null);
        }
    }

    private void compileBinary(BinaryExpr b) {
        String op = b.operator();
        // Короткое замыкание — через прыжки, чтобы вернуть значение операнда (как в JS).
        if (op.equals("&&")) {
            compileExpr(b.left());
            int j = emitJump(JUMP_IF_FALSE_KEEP);   // левое ложно → оставить его как результат
            emit(POP);                               // левое истинно → выбросить и считать правое
            compileExpr(b.right());
            patch(j, here());
            return;
        }
        if (op.equals("||")) {
            compileExpr(b.left());
            int j = emitJump(JUMP_IF_TRUE_KEEP);     // левое истинно → оставить его как результат
            emit(POP);
            compileExpr(b.right());
            patch(j, here());
            return;
        }
        compileExpr(b.left());
        compileExpr(b.right());
        emit(binaryOpcode(op, b.pos()));
    }

    private static SimpleVmOpCode binaryOpcode(String op, ASTNode.SourcePos pos) {
        return switch (op) {
            case "+" -> ADD;
            case "-" -> SUB;
            case "*" -> MUL;
            case "/" -> DIV;
            case "div" -> DIVIDE_INT;
            case "mod" -> MOD;
            case "==" -> EQUAL;
            case "!=" -> NOT_EQUAL;
            case "<" -> LESS_THAN;
            case ">" -> GREATER_THAN;
            case "<=" -> LESS_EQUAL;
            case ">=" -> GREATER_EQUAL;
            default -> throw new RuntimeErrorJS("Неизвестная операция '" + op + "'", pos);
        };
    }

    // ==================== Эмиссия / backpatching ====================

    private void emit(SimpleVmOpCode op) { code.add(new SimpleVmInstruction(op)); }
    private void emit(SimpleVmOpCode op, Object operand) { code.add(new SimpleVmInstruction(op, operand)); }

    /** Эмитит прыжок с адресом-заглушкой, возвращает индекс для последующей правки. */
    private int emitJump(SimpleVmOpCode op) {
        int idx = code.size();
        code.add(new SimpleVmInstruction(op, -1));
        return idx;
    }

    private void patch(int site, int target) {
        code.set(site, new SimpleVmInstruction(code.get(site).op(), target));
    }

    private int here() { return code.size(); }

    private void enterScope() { emit(ENTER_SCOPE); scopeDepth++; }
    private void leaveScope() { emit(LEAVE_SCOPE); scopeDepth--; }
}
