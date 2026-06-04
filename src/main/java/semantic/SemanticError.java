package semantic;

import ast.ASTNode;
import diagnostic.Diagnostic;

import java.util.List;

public record SemanticError(String message, ASTNode.SourcePos pos) {

    @Override
    public String toString() {
        return format(null);
    }

    public String format(List<String> sourceLines) {
        if (pos == null) {
            return "[ОШИБКА СЕМАНТИКИ] " + message;
        }
        return Diagnostic.format("[ОШИБКА СЕМАНТИКИ]", message,
                pos.line(), pos.column(), sourceLines);
    }
}
