package transform;

import ast.ASTNode;
import ast.ASTNode.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Свёртка констант, проброс известных литералов через let-переменные,
 * алгебраические тождества, удаление мёртвых веток и недостижимого кода
 * после return/break/continue. В самом конце прогоняется {@link UnusedVarRemover}.
 * Records неизменяемы — дерево строим функционально, оборачивая каждый узел заново.
 */
public final class AstTransformer {

    private final OptimizationStats stats;
    private final ConstEnv env = new ConstEnv();

    private AstTransformer(OptimizationStats stats) {
        this.stats = stats;
    }

    public static ASTNode transform(ASTNode node) {
        return transform(node, new OptimizationStats());
    }

    public static ASTNode transform(ASTNode node, OptimizationStats stats) {
        AstTransformer t = new AstTransformer(stats);
        ASTNode r = t.tx(node);
        r = UnusedVarRemover.remove(r, stats);
        return r;
    }

    private ASTNode tx(ASTNode node) {
        if (node == null) return null;
        return switch (node) {
            case Program p -> new Program(txStatementList(p.statements()));
            case Block b -> {
                env.enter();
                List<ASTNode> body = txStatementList(b.statements());
                env.leave();
                yield new Block(body);
            }
            case VarDecl v -> txVarDecl(v);
            case Assignment a -> txAssignment(a);
            case IfStatement i -> foldIf(i);
            case WhileStatement w -> foldWhile(w);
            case ForStatement f -> foldFor(f);
            case BreakStatement br -> br;
            case ContinueStatement c -> c;
            case PrintStatement p -> new PrintStatement(txExprList(p.arguments()), p.pos());
            case FunctionDecl fd -> txFunctionDecl(fd);
            case ReturnStatement r -> new ReturnStatement(tx(r.value()), r.pos());
            case ExpressionStatement es -> new ExpressionStatement(tx(es.expression()));
            case BinaryExpr be -> foldBinary(be);
            case UnaryExpr ue -> foldUnary(ue);
            case FunctionCall fc -> txCall(fc);
            case MemberAccess m -> new MemberAccess(tx(m.object()), m.property(), m.pos());
            case IndexAccess ix -> new IndexAccess(tx(ix.object()), tx(ix.index()), ix.pos());
            case Identifier id -> substituteIdentifier(id);
            case NumberLiteral n -> n;
            case StringLiteral s -> s;
            case BooleanLiteral bl -> bl;
            case NullLiteral nl -> nl;
            case UndefinedLiteral ul -> ul;
            case ArrayLiteral a -> new ArrayLiteral(txExprList(a.elements()), a.pos());
            case ObjectLiteral o -> new ObjectLiteral(o.keys(), txExprList(o.values()), o.pos());
        };
    }

    private FunctionCall txCall(FunctionCall fc) {
        ASTNode callee = tx(fc.callee());
        List<ASTNode> args = txExprList(fc.arguments());
        return new FunctionCall(callee, args, fc.pos());
    }

    private List<ASTNode> txExprList(List<ASTNode> list) {
        List<ASTNode> out = new ArrayList<>(list.size());
        for (ASTNode n : list) out.add(tx(n));
        return out;
    }

    private List<ASTNode> txStatementList(List<ASTNode> list) {
        List<ASTNode> out = new ArrayList<>(list.size());
        boolean unreachable = false;
        for (ASTNode n : list) {
            if (unreachable) {
                stats.unreachableRemoved++;
                continue;
            }
            ASTNode tn = tx(n);
            if (tn instanceof Block b && b.statements().isEmpty()) {
                continue;
            }
            out.add(tn);
            if (isUnconditionalJump(tn)) {
                unreachable = true;
            }
        }
        return out;
    }

    private static boolean isUnconditionalJump(ASTNode n) {
        return n instanceof ReturnStatement
                || n instanceof BreakStatement
                || n instanceof ContinueStatement;
    }

    private ASTNode txVarDecl(VarDecl v) {
        ASTNode init = tx(v.init());
        env.declare(v.name(), literalOrNull(init));
        return new VarDecl(v.name(), init, v.pos());
    }

    private ASTNode txAssignment(Assignment a) {
        ASTNode target = txAssignTarget(a.target());
        ASTNode value = tx(a.value());
        if (target instanceof Identifier id) {
            env.invalidate(id.name());
        }
        return new Assignment(target, value, a.pos());
    }

    private ASTNode txAssignTarget(ASTNode target) {
        return switch (target) {
            case Identifier id -> id;
            case MemberAccess m -> new MemberAccess(tx(m.object()), m.property(), m.pos());
            case IndexAccess ix -> new IndexAccess(tx(ix.object()), tx(ix.index()), ix.pos());
            default -> tx(target);
        };
    }

    private FunctionDecl txFunctionDecl(FunctionDecl f) {
        env.enter();
        for (String p : f.params()) env.declare(p, null);
        ASTNode body = tx(f.body());
        env.leave();
        return new FunctionDecl(f.name(), f.params(), body, f.pos());
    }

    private ASTNode substituteIdentifier(Identifier id) {
        ASTNode lit = env.lookup(id.name());
        if (lit == null) return id;
        stats.varsPropagated++;
        return withPos(lit, id.pos());
    }

    private static ASTNode withPos(ASTNode lit, SourcePos pos) {
        return switch (lit) {
            case NumberLiteral n -> new NumberLiteral(n.value(), pos);
            case StringLiteral s -> new StringLiteral(s.value(), pos);
            case BooleanLiteral b -> new BooleanLiteral(b.value(), pos);
            case NullLiteral nl -> new NullLiteral(pos);
            case UndefinedLiteral u -> new UndefinedLiteral(pos);
            default -> lit;
        };
    }

    private static ASTNode literalOrNull(ASTNode n) {
        if (n == null) return null;
        return switch (n) {
            case NumberLiteral x -> x;
            case StringLiteral x -> x;
            case BooleanLiteral x -> x;
            case NullLiteral x -> x;
            case UndefinedLiteral x -> x;
            default -> null;
        };
    }

    private ASTNode foldBinary(BinaryExpr b) {
        ASTNode left = tx(b.left());
        ASTNode right = tx(b.right());

        if (left instanceof NumberLiteral ln && right instanceof NumberLiteral rn) {
            Double v = applyNumeric(b.operator(), ln.value(), rn.value());
            if (v != null) {
                stats.constantsFolded++;
                return new NumberLiteral(v, b.pos());
            }
            Boolean cmp = applyNumberCompare(b.operator(), ln.value(), rn.value());
            if (cmp != null) {
                stats.constantsFolded++;
                return new BooleanLiteral(cmp, b.pos());
            }
        }

        if (left instanceof StringLiteral ls && right instanceof StringLiteral rs) {
            ASTNode folded = foldStringBinary(b.operator(), ls.value(), rs.value(), b.pos());
            if (folded != null) {
                stats.constantsFolded++;
                return folded;
            }
        }

        if (b.operator().equals("+")) {
            String s = stringIfConcatable(left, right);
            if (s != null) {
                stats.constantsFolded++;
                return new StringLiteral(s, b.pos());
            }
        }

        if (b.operator().equals("&&") && left instanceof BooleanLiteral bl) {
            stats.constantsFolded++;
            return bl.value() ? right : bl;
        }
        if (b.operator().equals("||") && left instanceof BooleanLiteral bl) {
            stats.constantsFolded++;
            return bl.value() ? bl : right;
        }

        ASTNode alg = applyAlgebraic(b.operator(), left, right, b.pos());
        if (alg != null) {
            stats.algebraicSimplified++;
            return alg;
        }

        return new BinaryExpr(left, b.operator(), right, b.pos());
    }

    private static ASTNode foldStringBinary(String op, String l, String r, SourcePos pos) {
        return switch (op) {
            case "+" -> new StringLiteral(l + r, pos);
            case "==" -> new BooleanLiteral(l.equals(r), pos);
            case "!=" -> new BooleanLiteral(!l.equals(r), pos);
            case "<" -> new BooleanLiteral(l.compareTo(r) < 0, pos);
            case ">" -> new BooleanLiteral(l.compareTo(r) > 0, pos);
            case "<=" -> new BooleanLiteral(l.compareTo(r) <= 0, pos);
            case ">=" -> new BooleanLiteral(l.compareTo(r) >= 0, pos);
            default -> null;
        };
    }

    private static ASTNode applyAlgebraic(String op, ASTNode l, ASTNode r, SourcePos pos) {
        switch (op) {
            case "+" -> {
                if (isZero(r) && isStaticallyNumeric(l)) return l;
                if (isZero(l) && isStaticallyNumeric(r)) return r;
            }
            case "-" -> {
                if (isZero(r) && isStaticallyNumeric(l)) return l;
                if (l instanceof Identifier li && r instanceof Identifier ri
                        && li.name().equals(ri.name())) {
                    return new NumberLiteral(0, pos);
                }
            }
            case "*" -> {
                if (isOne(r) && isStaticallyNumeric(l)) return l;
                if (isOne(l) && isStaticallyNumeric(r)) return r;
                if (isZero(r) && isStaticallyNumeric(l)) return new NumberLiteral(0, pos);
                if (isZero(l) && isStaticallyNumeric(r)) return new NumberLiteral(0, pos);
            }
            case "/" -> {
                if (isOne(r) && isStaticallyNumeric(l)) return l;
            }
            case "&&" -> {
                // JS-семантика: x && y возвращает x, если x falsy, иначе y.
                // Подмена возможна только когда x статически булев.
                if (r instanceof BooleanLiteral rb && isStaticallyBoolean(l)) {
                    return rb.value() ? l : new BooleanLiteral(false, pos);
                }
            }
            case "||" -> {
                if (r instanceof BooleanLiteral rb && isStaticallyBoolean(l)) {
                    return rb.value() ? new BooleanLiteral(true, pos) : l;
                }
            }
            default -> { }
        }
        return null;
    }

    private static boolean isZero(ASTNode n) {
        return n instanceof NumberLiteral nl && nl.value() == 0.0;
    }

    private static boolean isOne(ASTNode n) {
        return n instanceof NumberLiteral nl && nl.value() == 1.0;
    }

    private static boolean isStaticallyNumeric(ASTNode n) {
        return switch (n) {
            case NumberLiteral x -> true;
            case UnaryExpr u -> u.operator().equals("-") && isStaticallyNumeric(u.operand());
            case BinaryExpr b -> {
                String op = b.operator();
                // Если оба операнда статически числовые — `+` тоже даёт число
                // (строковой конкатенации в принципе быть не может).
                yield (op.equals("+") || op.equals("-") || op.equals("*")
                        || op.equals("/") || op.equals("div") || op.equals("mod"))
                        && isStaticallyNumeric(b.left())
                        && isStaticallyNumeric(b.right());
            }
            default -> false;
        };
    }

    private static boolean isStaticallyBoolean(ASTNode n) {
        return switch (n) {
            case BooleanLiteral x -> true;
            case UnaryExpr u -> u.operator().equals("!");
            case BinaryExpr b -> switch (b.operator()) {
                case "==", "!=", "<", ">", "<=", ">=" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    private static Double applyNumeric(String op, double a, double b) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? null : a / b;
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

    private static String stringIfConcatable(ASTNode l, ASTNode r) {
        String ls = literalToStringOrNull(l);
        String rs = literalToStringOrNull(r);
        if (ls == null || rs == null) return null;
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

    private ASTNode foldUnary(UnaryExpr u) {
        ASTNode arg = tx(u.operand());
        switch (u.operator()) {
            case "-" -> {
                if (arg instanceof NumberLiteral n) {
                    stats.constantsFolded++;
                    return new NumberLiteral(-n.value(), u.pos());
                }
                return new UnaryExpr("-", arg, u.pos());
            }
            case "!" -> {
                Boolean t = truthy(arg);
                if (t != null) {
                    stats.constantsFolded++;
                    return new BooleanLiteral(!t, u.pos());
                }
                if (arg instanceof UnaryExpr inner && inner.operator().equals("!")
                        && isStaticallyBoolean(inner.operand())) {
                    stats.algebraicSimplified++;
                    return inner.operand();
                }
                return new UnaryExpr("!", arg, u.pos());
            }
            default -> {
                return new UnaryExpr(u.operator(), arg, u.pos());
            }
        }
    }

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

    private ASTNode foldIf(IfStatement i) {
        ASTNode cond = tx(i.condition());

        Set<String> mutated = collectAssignedNames(i.thenBranch());
        if (i.elseBranch() != null) mutated.addAll(collectAssignedNames(i.elseBranch()));
        for (String name : mutated) env.invalidate(name);

        ASTNode then = tx(i.thenBranch());
        ASTNode els = i.elseBranch() == null ? null : tx(i.elseBranch());

        Boolean t = truthy(cond);
        if (t == null) {
            return new IfStatement(cond, then, els, i.pos());
        }
        stats.branchesEliminated++;
        if (t) return then;
        return els != null ? els : new Block(List.of());
    }

    private ASTNode foldWhile(WhileStatement w) {
        for (String name : collectAssignedNames(w.body())) env.invalidate(name);

        ASTNode cond = tx(w.condition());
        ASTNode body = tx(w.body());
        Boolean t = truthy(cond);
        if (Boolean.FALSE.equals(t)) {
            stats.branchesEliminated++;
            return new Block(List.of());
        }
        return new WhileStatement(cond, body, w.pos());
    }

    private ASTNode foldFor(ForStatement f) {
        env.enter();

        // Сначала обрабатываем init — это может объявить новую переменную.
        ASTNode init = tx(f.init());

        // Всё, что меняется в теле/обновлении ИЛИ объявлено в init, не может
        // считаться константой: значение меняется между итерациями.
        Set<String> mutated = new HashSet<>();
        if (f.body() != null) mutated.addAll(collectAssignedNames(f.body()));
        if (f.update() != null) mutated.addAll(collectAssignedNames(f.update()));
        if (init instanceof VarDecl v) mutated.add(v.name());
        for (String name : mutated) env.invalidate(name);

        ASTNode cond = tx(f.condition());
        ASTNode upd = tx(f.update());
        ASTNode body = tx(f.body());

        env.leave();

        Boolean t = cond == null ? null : truthy(cond);
        if (Boolean.FALSE.equals(t)) {
            stats.branchesEliminated++;
            return init == null ? new Block(List.of()) : init;
        }
        return new ForStatement(init, cond, upd, body, f.pos());
    }

    static Set<String> collectAssignedNames(ASTNode root) {
        Set<String> out = new HashSet<>();
        collectAssignedNames(root, out);
        return out;
    }

    private static void collectAssignedNames(ASTNode n, Set<String> out) {
        if (n == null) return;
        switch (n) {
            case Assignment a -> {
                if (a.target() instanceof Identifier id) out.add(id.name());
                collectAssignedNames(a.value(), out);
            }
            case Program p -> { for (ASTNode s : p.statements()) collectAssignedNames(s, out); }
            case Block b -> { for (ASTNode s : b.statements()) collectAssignedNames(s, out); }
            case VarDecl v -> collectAssignedNames(v.init(), out);
            case IfStatement i -> {
                collectAssignedNames(i.condition(), out);
                collectAssignedNames(i.thenBranch(), out);
                collectAssignedNames(i.elseBranch(), out);
            }
            case WhileStatement w -> {
                collectAssignedNames(w.condition(), out);
                collectAssignedNames(w.body(), out);
            }
            case ForStatement f -> {
                collectAssignedNames(f.init(), out);
                collectAssignedNames(f.condition(), out);
                collectAssignedNames(f.update(), out);
                collectAssignedNames(f.body(), out);
            }
            case FunctionDecl fd -> collectAssignedNames(fd.body(), out);
            case ReturnStatement r -> collectAssignedNames(r.value(), out);
            case PrintStatement p -> { for (ASTNode a : p.arguments()) collectAssignedNames(a, out); }
            case ExpressionStatement e -> collectAssignedNames(e.expression(), out);
            case BinaryExpr be -> {
                collectAssignedNames(be.left(), out);
                collectAssignedNames(be.right(), out);
            }
            case UnaryExpr ue -> collectAssignedNames(ue.operand(), out);
            case FunctionCall fc -> {
                collectAssignedNames(fc.callee(), out);
                for (ASTNode a : fc.arguments()) collectAssignedNames(a, out);
            }
            case MemberAccess m -> collectAssignedNames(m.object(), out);
            case IndexAccess ix -> {
                collectAssignedNames(ix.object(), out);
                collectAssignedNames(ix.index(), out);
            }
            case ArrayLiteral a -> { for (ASTNode e : a.elements()) collectAssignedNames(e, out); }
            case ObjectLiteral o -> { for (ASTNode v : o.values()) collectAssignedNames(v, out); }
            default -> { }
        }
    }
}
