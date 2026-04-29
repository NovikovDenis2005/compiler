package semantic;

import ast.ASTNode;

/** Запись таблицы символов. Тип значения здесь не храним — динамика. */
public record Symbol(String name, Kind kind, ASTNode.SourcePos declaredAt) {
    public enum Kind { VAR, FUNCTION, PARAM }
}
