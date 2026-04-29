package semantic;

import ast.ASTNode;

import java.util.List;
import java.util.Map;

public record AnalysisResult(
        List<SemanticError> errors,
        Map<ASTNode, Type> typeMap,
        Scope globalScope) {

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
