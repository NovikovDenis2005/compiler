package ast;

import ast.ASTNode.*;

import java.util.Map;

/**
 * Красивая печать AST-дерева в двух форматах:
 * 1) LISP-стиль: (Program (VarDecl "x" (NumberLiteral 5)))
 * 2) Иерархический (лесенкой):
 *    Program
 *      VarDecl: x
 *        NumberLiteral: 5
 */
public class ASTPrinter {

    // ==================== LISP-стиль ====================

    public static String toLisp(ASTNode node) {
        if (node == null) return "null";
        StringBuilder sb = new StringBuilder();
        toLisp(node, sb);
        return sb.toString();
    }

    private static void toLisp(ASTNode node, StringBuilder sb) {
        switch (node) {
            case Program p -> {
                sb.append("(Program");
                for (ASTNode s : p.statements()) { sb.append(" "); toLisp(s, sb); }
                sb.append(")");
            }
            case VarDecl v -> {
                sb.append("(VarDecl \"").append(v.name()).append("\"");
                if (v.init() != null) { sb.append(" "); toLisp(v.init(), sb); }
                sb.append(")");
            }
            case Assignment a -> {
                sb.append("(Assign ");
                toLisp(a.target(), sb);
                sb.append(" ");
                toLisp(a.value(), sb);
                sb.append(")");
            }
            case Block b -> {
                sb.append("(Block");
                for (ASTNode s : b.statements()) { sb.append(" "); toLisp(s, sb); }
                sb.append(")");
            }
            case IfStatement i -> {
                sb.append("(If ");
                toLisp(i.condition(), sb);
                sb.append(" ");
                toLisp(i.thenBranch(), sb);
                if (i.elseBranch() != null) { sb.append(" "); toLisp(i.elseBranch(), sb); }
                sb.append(")");
            }
            case WhileStatement w -> {
                sb.append("(While ");
                toLisp(w.condition(), sb);
                sb.append(" ");
                toLisp(w.body(), sb);
                sb.append(")");
            }
            case ForStatement f -> {
                sb.append("(For ");
                toLisp(f.init(), sb);
                sb.append(" ");
                toLisp(f.condition(), sb);
                sb.append(" ");
                toLisp(f.update(), sb);
                sb.append(" ");
                toLisp(f.body(), sb);
                sb.append(")");
            }
            case BreakStatement ignored -> sb.append("(Break)");
            case ContinueStatement ignored -> sb.append("(Continue)");
            case PrintStatement p -> {
                sb.append("(Print");
                for (ASTNode a : p.arguments()) { sb.append(" "); toLisp(a, sb); }
                sb.append(")");
            }
            case FunctionDecl f -> {
                sb.append("(FunctionDecl \"").append(f.name()).append("\" (");
                sb.append(String.join(", ", f.params()));
                sb.append(") ");
                toLisp(f.body(), sb);
                sb.append(")");
            }
            case ReturnStatement r -> {
                sb.append("(Return");
                if (r.value() != null) { sb.append(" "); toLisp(r.value(), sb); }
                sb.append(")");
            }
            case ExpressionStatement e -> {
                sb.append("(ExprStmt ");
                toLisp(e.expression(), sb);
                sb.append(")");
            }
            case BinaryExpr b -> {
                sb.append("(").append(b.operator()).append(" ");
                toLisp(b.left(), sb);
                sb.append(" ");
                toLisp(b.right(), sb);
                sb.append(")");
            }
            case UnaryExpr u -> {
                sb.append("(").append(u.operator()).append(" ");
                toLisp(u.operand(), sb);
                sb.append(")");
            }
            case FunctionCall c -> {
                sb.append("(Call ");
                toLisp(c.callee(), sb);
                for (ASTNode a : c.arguments()) { sb.append(" "); toLisp(a, sb); }
                sb.append(")");
            }
            case MemberAccess m -> {
                sb.append("(MemberAccess ");
                toLisp(m.object(), sb);
                sb.append(" \"").append(m.property()).append("\")");
            }
            case IndexAccess i -> {
                sb.append("(Index ");
                toLisp(i.object(), sb);
                sb.append(" ");
                toLisp(i.index(), sb);
                sb.append(")");
            }
            case NumberLiteral n -> sb.append(n.value());
            case StringLiteral s -> sb.append("\"").append(s.value()).append("\"");
            case BooleanLiteral b -> sb.append(b.value());
            case NullLiteral ignored -> sb.append("null");
            case UndefinedLiteral ignored -> sb.append("undefined");
            case Identifier id -> sb.append(id.name());
            case ArrayLiteral a -> {
                sb.append("[");
                for (int i = 0; i < a.elements().size(); i++) {
                    if (i > 0) sb.append(", ");
                    toLisp(a.elements().get(i), sb);
                }
                sb.append("]");
            }
            case ObjectLiteral o -> {
                sb.append("{");
                for (int i = 0; i < o.keys().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(o.keys().get(i)).append(": ");
                    toLisp(o.values().get(i), sb);
                }
                sb.append("}");
            }
        }
    }

    // ==================== Иерархический формат (лесенкой) ====================

    public static String toTree(ASTNode node) {
        StringBuilder sb = new StringBuilder();
        toTree(node, sb, "", true);
        return sb.toString();
    }

    /**
     * То же дерево, но с аннотацией статического типа возле выражений:
     *   BinaryExpr: + : NUMBER
     * Используется на 2-й аттестации после семантического анализа.
     */
    public static String toTreeWithTypes(ASTNode node, Map<ASTNode, ?> typeMap) {
        StringBuilder sb = new StringBuilder();
        toTreeAnnotated(node, sb, "", true, typeMap);
        return sb.toString();
    }

    private static String typeSuffix(ASTNode n, Map<ASTNode, ?> typeMap) {
        if (typeMap == null) return "";
        Object t = typeMap.get(n);
        return t == null ? "" : "  : " + t;
    }

    private static void toTreeAnnotated(ASTNode node, StringBuilder sb,
                                        String prefix, boolean isLast,
                                        Map<ASTNode, ?> typeMap) {
        if (node == null) {
            sb.append(prefix).append(isLast ? "└── " : "├── ").append("<null>\n");
            return;
        }
        String connector = isLast ? "└── " : "├── ";
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        String tag = typeSuffix(node, typeMap);

        switch (node) {
            case Program p -> {
                sb.append(prefix).append(connector).append("Program\n");
                printChildrenA(p.statements(), sb, childPrefix, typeMap);
            }
            case Block b -> {
                sb.append(prefix).append(connector).append("Block\n");
                printChildrenA(b.statements(), sb, childPrefix, typeMap);
            }
            case VarDecl v -> {
                sb.append(prefix).append(connector).append("VarDecl: ").append(v.name()).append("\n");
                if (v.init() != null) toTreeAnnotated(v.init(), sb, childPrefix, true, typeMap);
            }
            case Assignment a -> {
                sb.append(prefix).append(connector).append("Assign\n");
                toTreeAnnotated(a.target(), sb, childPrefix, false, typeMap);
                toTreeAnnotated(a.value(), sb, childPrefix, true, typeMap);
            }
            case IfStatement i -> {
                sb.append(prefix).append(connector).append("If\n");
                sb.append(childPrefix).append("├── Condition:\n");
                toTreeAnnotated(i.condition(), sb, childPrefix + "│   ", true, typeMap);
                if (i.elseBranch() != null) {
                    sb.append(childPrefix).append("├── Then:\n");
                    toTreeAnnotated(i.thenBranch(), sb, childPrefix + "│   ", true, typeMap);
                    sb.append(childPrefix).append("└── Else:\n");
                    toTreeAnnotated(i.elseBranch(), sb, childPrefix + "    ", true, typeMap);
                } else {
                    sb.append(childPrefix).append("└── Then:\n");
                    toTreeAnnotated(i.thenBranch(), sb, childPrefix + "    ", true, typeMap);
                }
            }
            case WhileStatement w -> {
                sb.append(prefix).append(connector).append("While\n");
                sb.append(childPrefix).append("├── Condition:\n");
                toTreeAnnotated(w.condition(), sb, childPrefix + "│   ", true, typeMap);
                sb.append(childPrefix).append("└── Body:\n");
                toTreeAnnotated(w.body(), sb, childPrefix + "    ", true, typeMap);
            }
            case ForStatement f -> {
                sb.append(prefix).append(connector).append("For\n");
                sb.append(childPrefix).append("├── Init:\n");
                toTreeAnnotated(f.init(), sb, childPrefix + "│   ", true, typeMap);
                sb.append(childPrefix).append("├── Condition:\n");
                toTreeAnnotated(f.condition(), sb, childPrefix + "│   ", true, typeMap);
                sb.append(childPrefix).append("├── Update:\n");
                toTreeAnnotated(f.update(), sb, childPrefix + "│   ", true, typeMap);
                sb.append(childPrefix).append("└── Body:\n");
                toTreeAnnotated(f.body(), sb, childPrefix + "    ", true, typeMap);
            }
            case BreakStatement ignored ->
                sb.append(prefix).append(connector).append("Break\n");
            case ContinueStatement ignored ->
                sb.append(prefix).append(connector).append("Continue\n");
            case PrintStatement p -> {
                sb.append(prefix).append(connector).append("Print\n");
                printChildrenA(p.arguments(), sb, childPrefix, typeMap);
            }
            case FunctionDecl f -> {
                sb.append(prefix).append(connector)
                  .append("FunctionDecl: ").append(f.name())
                  .append("(").append(String.join(", ", f.params())).append(")\n");
                toTreeAnnotated(f.body(), sb, childPrefix, true, typeMap);
            }
            case ReturnStatement r -> {
                sb.append(prefix).append(connector).append("Return\n");
                if (r.value() != null) toTreeAnnotated(r.value(), sb, childPrefix, true, typeMap);
            }
            case ExpressionStatement e -> {
                sb.append(prefix).append(connector).append("ExprStmt\n");
                toTreeAnnotated(e.expression(), sb, childPrefix, true, typeMap);
            }
            case BinaryExpr b -> {
                sb.append(prefix).append(connector).append("BinaryExpr: ").append(b.operator()).append(tag).append("\n");
                toTreeAnnotated(b.left(), sb, childPrefix, false, typeMap);
                toTreeAnnotated(b.right(), sb, childPrefix, true, typeMap);
            }
            case UnaryExpr u -> {
                sb.append(prefix).append(connector).append("UnaryExpr: ").append(u.operator()).append(tag).append("\n");
                toTreeAnnotated(u.operand(), sb, childPrefix, true, typeMap);
            }
            case FunctionCall c -> {
                sb.append(prefix).append(connector).append("FunctionCall").append(tag).append("\n");
                sb.append(childPrefix).append("├── Callee:\n");
                toTreeAnnotated(c.callee(), sb, childPrefix + "│   ", true, typeMap);
                if (!c.arguments().isEmpty()) {
                    sb.append(childPrefix).append("└── Args:\n");
                    printChildrenA(c.arguments(), sb, childPrefix + "    ", typeMap);
                }
            }
            case MemberAccess m -> {
                sb.append(prefix).append(connector)
                  .append("MemberAccess: .").append(m.property()).append(tag).append("\n");
                toTreeAnnotated(m.object(), sb, childPrefix, true, typeMap);
            }
            case IndexAccess i -> {
                sb.append(prefix).append(connector).append("IndexAccess").append(tag).append("\n");
                toTreeAnnotated(i.object(), sb, childPrefix, false, typeMap);
                toTreeAnnotated(i.index(), sb, childPrefix, true, typeMap);
            }
            case NumberLiteral n ->
                sb.append(prefix).append(connector).append("Number: ").append(n.value()).append(tag).append("\n");
            case StringLiteral s ->
                sb.append(prefix).append(connector).append("String: ").append(s.value()).append(tag).append("\n");
            case BooleanLiteral b ->
                sb.append(prefix).append(connector).append("Boolean: ").append(b.value()).append(tag).append("\n");
            case NullLiteral ignored ->
                sb.append(prefix).append(connector).append("Null").append(tag).append("\n");
            case UndefinedLiteral ignored ->
                sb.append(prefix).append(connector).append("Undefined").append(tag).append("\n");
            case Identifier id ->
                sb.append(prefix).append(connector).append("Id: ").append(id.name()).append(tag).append("\n");
            case ArrayLiteral a -> {
                sb.append(prefix).append(connector).append("Array").append(tag).append("\n");
                printChildrenA(a.elements(), sb, childPrefix, typeMap);
            }
            case ObjectLiteral o -> {
                sb.append(prefix).append(connector).append("Object").append(tag).append("\n");
                for (int i = 0; i < o.keys().size(); i++) {
                    boolean last = i == o.keys().size() - 1;
                    sb.append(childPrefix).append(last ? "└── " : "├── ").append(o.keys().get(i)).append(":\n");
                    toTreeAnnotated(o.values().get(i), sb, childPrefix + (last ? "    " : "│   "), true, typeMap);
                }
            }
        }
    }

    private static void printChildrenA(java.util.List<ASTNode> children, StringBuilder sb,
                                       String prefix, Map<ASTNode, ?> typeMap) {
        for (int i = 0; i < children.size(); i++) {
            toTreeAnnotated(children.get(i), sb, prefix, i == children.size() - 1, typeMap);
        }
    }

    private static void toTree(ASTNode node, StringBuilder sb, String prefix, boolean isLast) {
        if (node == null) {
            sb.append(prefix).append(isLast ? "└── " : "├── ").append("<null>\n");
            return;
        }

        String connector = isLast ? "└── " : "├── ";
        String childPrefix = prefix + (isLast ? "    " : "│   ");

        switch (node) {
            case Program p -> {
                sb.append(prefix).append(connector).append("Program\n");
                printChildren(p.statements(), sb, childPrefix);
            }
            case VarDecl v -> {
                sb.append(prefix).append(connector).append("VarDecl: ").append(v.name()).append("\n");
                if (v.init() != null) toTree(v.init(), sb, childPrefix, true);
            }
            case Assignment a -> {
                sb.append(prefix).append(connector).append("Assign\n");
                toTree(a.target(), sb, childPrefix, false);
                toTree(a.value(), sb, childPrefix, true);
            }
            case Block b -> {
                sb.append(prefix).append(connector).append("Block\n");
                printChildren(b.statements(), sb, childPrefix);
            }
            case IfStatement i -> {
                sb.append(prefix).append(connector).append("If\n");
                sb.append(childPrefix).append("├── Condition:\n");
                toTree(i.condition(), sb, childPrefix + "│   ", true);
                if (i.elseBranch() != null) {
                    sb.append(childPrefix).append("├── Then:\n");
                    toTree(i.thenBranch(), sb, childPrefix + "│   ", true);
                    sb.append(childPrefix).append("└── Else:\n");
                    toTree(i.elseBranch(), sb, childPrefix + "    ", true);
                } else {
                    sb.append(childPrefix).append("└── Then:\n");
                    toTree(i.thenBranch(), sb, childPrefix + "    ", true);
                }
            }
            case WhileStatement w -> {
                sb.append(prefix).append(connector).append("While\n");
                sb.append(childPrefix).append("├── Condition:\n");
                toTree(w.condition(), sb, childPrefix + "│   ", true);
                sb.append(childPrefix).append("└── Body:\n");
                toTree(w.body(), sb, childPrefix + "    ", true);
            }
            case ForStatement f -> {
                sb.append(prefix).append(connector).append("For\n");
                sb.append(childPrefix).append("├── Init:\n");
                toTree(f.init(), sb, childPrefix + "│   ", true);
                sb.append(childPrefix).append("├── Condition:\n");
                toTree(f.condition(), sb, childPrefix + "│   ", true);
                sb.append(childPrefix).append("├── Update:\n");
                toTree(f.update(), sb, childPrefix + "│   ", true);
                sb.append(childPrefix).append("└── Body:\n");
                toTree(f.body(), sb, childPrefix + "    ", true);
            }
            case BreakStatement ignored ->
                sb.append(prefix).append(connector).append("Break\n");
            case ContinueStatement ignored ->
                sb.append(prefix).append(connector).append("Continue\n");
            case PrintStatement p -> {
                sb.append(prefix).append(connector).append("Print\n");
                printChildren(p.arguments(), sb, childPrefix);
            }
            case FunctionDecl f -> {
                sb.append(prefix).append(connector)
                  .append("FunctionDecl: ").append(f.name())
                  .append("(").append(String.join(", ", f.params())).append(")\n");
                toTree(f.body(), sb, childPrefix, true);
            }
            case ReturnStatement r -> {
                sb.append(prefix).append(connector).append("Return\n");
                if (r.value() != null) toTree(r.value(), sb, childPrefix, true);
            }
            case ExpressionStatement e -> {
                sb.append(prefix).append(connector).append("ExprStmt\n");
                toTree(e.expression(), sb, childPrefix, true);
            }
            case BinaryExpr b -> {
                sb.append(prefix).append(connector).append("BinaryExpr: ").append(b.operator()).append("\n");
                toTree(b.left(), sb, childPrefix, false);
                toTree(b.right(), sb, childPrefix, true);
            }
            case UnaryExpr u -> {
                sb.append(prefix).append(connector).append("UnaryExpr: ").append(u.operator()).append("\n");
                toTree(u.operand(), sb, childPrefix, true);
            }
            case FunctionCall c -> {
                sb.append(prefix).append(connector).append("FunctionCall\n");
                sb.append(childPrefix).append("├── Callee:\n");
                toTree(c.callee(), sb, childPrefix + "│   ", true);
                if (!c.arguments().isEmpty()) {
                    sb.append(childPrefix).append("└── Args:\n");
                    printChildren(c.arguments(), sb, childPrefix + "    ");
                }
            }
            case MemberAccess m -> {
                sb.append(prefix).append(connector).append("MemberAccess: .").append(m.property()).append("\n");
                toTree(m.object(), sb, childPrefix, true);
            }
            case IndexAccess i -> {
                sb.append(prefix).append(connector).append("IndexAccess\n");
                toTree(i.object(), sb, childPrefix, false);
                toTree(i.index(), sb, childPrefix, true);
            }
            case NumberLiteral n ->
                sb.append(prefix).append(connector).append("Number: ").append(n.value()).append("\n");
            case StringLiteral s ->
                sb.append(prefix).append(connector).append("String: ").append(s.value()).append("\n");
            case BooleanLiteral b ->
                sb.append(prefix).append(connector).append("Boolean: ").append(b.value()).append("\n");
            case NullLiteral ignored ->
                sb.append(prefix).append(connector).append("Null\n");
            case UndefinedLiteral ignored ->
                sb.append(prefix).append(connector).append("Undefined\n");
            case Identifier id ->
                sb.append(prefix).append(connector).append("Id: ").append(id.name()).append("\n");
            case ArrayLiteral a -> {
                sb.append(prefix).append(connector).append("Array\n");
                printChildren(a.elements(), sb, childPrefix);
            }
            case ObjectLiteral o -> {
                sb.append(prefix).append(connector).append("Object\n");
                for (int i = 0; i < o.keys().size(); i++) {
                    boolean last = i == o.keys().size() - 1;
                    sb.append(childPrefix).append(last ? "└── " : "├── ").append(o.keys().get(i)).append(":\n");
                    toTree(o.values().get(i), sb, childPrefix + (last ? "    " : "│   "), true);
                }
            }
        }
    }

    private static void printChildren(java.util.List<ASTNode> children, StringBuilder sb, String prefix) {
        for (int i = 0; i < children.size(); i++) {
            toTree(children.get(i), sb, prefix, i == children.size() - 1);
        }
    }
}
