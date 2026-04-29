package interpreter;

import ast.ASTNode;

/** Имя с суффиксом JS — чтобы не пересечься с java.lang.RuntimeException. */
public class RuntimeErrorJS extends RuntimeException {
    private final ASTNode.SourcePos pos;

    public RuntimeErrorJS(String message, ASTNode.SourcePos pos) {
        super(message);
        this.pos = pos;
    }

    public ASTNode.SourcePos pos() { return pos; }

    public String formatted() {
        if (pos == null) return "[ОШИБКА ВЫПОЛНЕНИЯ] " + getMessage();
        return "[ОШИБКА ВЫПОЛНЕНИЯ] Строка " + pos + " -> " + getMessage();
    }
}
