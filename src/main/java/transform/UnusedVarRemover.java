package transform;

import ast.ASTNode;
import ast.ASTNode.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Удаление let-переменных, к которым нет ни одного {@code Identifier}-обращения
 * во всём дереве и чей инициализатор без побочных эффектов. Шейдинг имён в
 * разных скоупах не учитывается — в учебных примерах его нет.
 */
final class UnusedVarRemover {

    private UnusedVarRemover() {}

    static ASTNode remove(ASTNode root, OptimizationStats stats) {
        Map<String, Integer> uses = new HashMap<>();
        Set<String> params = new HashSet<>();
        collectUses(root, uses, params);
        Pass pass = new Pass(uses, params, stats);
        return pass.rewrite(root);
    }

    private static final class Pass {
        final Map<String, Integer> uses;
        final Set<String> params;
        final OptimizationStats stats;

        Pass(Map<String, Integer> uses, Set<String> params, OptimizationStats stats) {
            this.uses = uses;
            this.params = params;
            this.stats = stats;
        }

        ASTNode rewrite(ASTNode node) {
            if (node == null) return null;
            return switch (node) {
                case Program p -> new Program(rewriteStmts(p.statements()));
                case Block b -> new Block(rewriteStmts(b.statements()));
                case IfStatement i -> new IfStatement(
                        i.condition(),
                        rewrite(i.thenBranch()),
                        rewrite(i.elseBranch()),
                        i.pos());
                case WhileStatement w -> new WhileStatement(
                        w.condition(), rewrite(w.body()), w.pos());
                case ForStatement f -> new ForStatement(
                        rewriteOrSame(f.init()), f.condition(), f.update(),
                        rewrite(f.body()), f.pos());
                case FunctionDecl fd -> new FunctionDecl(
                        fd.name(), fd.params(), rewrite(fd.body()), fd.pos());
                default -> node;
            };
        }

        ASTNode rewriteOrSame(ASTNode n) {
            return n == null ? null : rewrite(n);
        }

        List<ASTNode> rewriteStmts(List<ASTNode> list) {
            List<ASTNode> out = new ArrayList<>(list.size());
            for (ASTNode n : list) {
                if (n instanceof VarDecl v && isDead(v)) {
                    stats.deadVarsRemoved++;
                    continue;
                }
                out.add(rewrite(n));
            }
            return out;
        }

        boolean isDead(VarDecl v) {
            if (params.contains(v.name())) return false;
            if (uses.getOrDefault(v.name(), 0) > 0) return false;
            return !hasSideEffects(v.init());
        }
    }

    private static boolean hasSideEffects(ASTNode n) {
        if (n == null) return false;
        return switch (n) {
            case FunctionCall fc -> true;
            case Assignment a -> true;
            case BinaryExpr b -> hasSideEffects(b.left()) || hasSideEffects(b.right());
            case UnaryExpr u -> hasSideEffects(u.operand());
            case MemberAccess m -> hasSideEffects(m.object());
            case IndexAccess ix -> hasSideEffects(ix.object()) || hasSideEffects(ix.index());
            case ArrayLiteral a -> a.elements().stream().anyMatch(UnusedVarRemover::hasSideEffects);
            case ObjectLiteral o -> o.values().stream().anyMatch(UnusedVarRemover::hasSideEffects);
            default -> false;
        };
    }

    private static void collectUses(ASTNode n, Map<String, Integer> uses, Set<String> params) {
        if (n == null) return;
        switch (n) {
            case Identifier id -> uses.merge(id.name(), 1, Integer::sum);
            case Assignment a -> {
                // Запись в переменную тоже считаем использованием — иначе удалим
                // декларацию, в которую потом пишут.
                if (a.target() instanceof Identifier id) {
                    uses.merge(id.name(), 1, Integer::sum);
                } else {
                    collectUses(a.target(), uses, params);
                }
                collectUses(a.value(), uses, params);
            }
            case Program p -> { for (ASTNode s : p.statements()) collectUses(s, uses, params); }
            case Block b -> { for (ASTNode s : b.statements()) collectUses(s, uses, params); }
            case VarDecl v -> collectUses(v.init(), uses, params);
            case IfStatement i -> {
                collectUses(i.condition(), uses, params);
                collectUses(i.thenBranch(), uses, params);
                collectUses(i.elseBranch(), uses, params);
            }
            case WhileStatement w -> {
                collectUses(w.condition(), uses, params);
                collectUses(w.body(), uses, params);
            }
            case ForStatement f -> {
                collectUses(f.init(), uses, params);
                collectUses(f.condition(), uses, params);
                collectUses(f.update(), uses, params);
                collectUses(f.body(), uses, params);
            }
            case FunctionDecl fd -> {
                params.addAll(fd.params());
                collectUses(fd.body(), uses, params);
            }
            case ReturnStatement r -> collectUses(r.value(), uses, params);
            case PrintStatement p -> { for (ASTNode a : p.arguments()) collectUses(a, uses, params); }
            case ExpressionStatement e -> collectUses(e.expression(), uses, params);
            case BinaryExpr be -> {
                collectUses(be.left(), uses, params);
                collectUses(be.right(), uses, params);
            }
            case UnaryExpr ue -> collectUses(ue.operand(), uses, params);
            case FunctionCall fc -> {
                collectUses(fc.callee(), uses, params);
                for (ASTNode a : fc.arguments()) collectUses(a, uses, params);
            }
            case MemberAccess m -> collectUses(m.object(), uses, params);
            case IndexAccess ix -> {
                collectUses(ix.object(), uses, params);
                collectUses(ix.index(), uses, params);
            }
            case ArrayLiteral a -> { for (ASTNode e : a.elements()) collectUses(e, uses, params); }
            case ObjectLiteral o -> { for (ASTNode v : o.values()) collectUses(v, uses, params); }
            default -> { }
        }
    }
}