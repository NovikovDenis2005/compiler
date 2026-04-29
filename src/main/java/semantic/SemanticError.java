package semantic;

import ast.ASTNode;

public record SemanticError(String message, ASTNode.SourcePos pos) {

    @Override
    public String toString() {
        if (pos == null) {
            return "[ОШИБКА СЕМАНТИКИ] " + message;
        }
        return "[ОШИБКА СЕМАНТИКИ] Строка " + pos + " -> " + message;
    }
}
