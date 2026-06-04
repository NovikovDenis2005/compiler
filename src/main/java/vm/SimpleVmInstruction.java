package vm;

/**
 * Одна инструкция байт-кода: опкод и (необязательный) операнд.
 *
 * Тип операнда зависит от опкода:
 *   PUSH_CONST                         = interpreter.Value
 *   LOAD_VAR, STORE_VAR, DECLARE_VAR   = String (имя)
 *   GET_PROP, SET_PROP                 = String (имя свойства)
 *   JUMP-опкоды, PRINT, CALL, NEW_ARRAY = Integer
 *   NEW_OBJECT                         = java.util.List&lt;String&gt; (ключи)
 *   MAKE_FUNCTION                      = ast.ASTNode.FunctionDecl
 *   остальные                          = null
 */
public record SimpleVmInstruction(SimpleVmOpCode op, Object operand) {

    public SimpleVmInstruction(SimpleVmOpCode op) {
        this(op, null);
    }
}
