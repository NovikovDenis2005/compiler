package vm;

/**
 * Набор инструкций (опкодов) простой стековой виртуальной машины miniJS.
 *
 * Машина — стековая: операнды кладутся на стек, операции снимают аргументы
 * и кладут результат. Управление потоком (if / while / for / break / continue)
 * реализовано прыжками JUMP / JUMP_IF_FALSE на абсолютные адреса инструкций.
 */
public enum SimpleVmOpCode {

    // --- Константы и переменные ---
    PUSH_CONST,     // операнд: Value — положить литерал на стек
    LOAD_VAR,       // операнд: String — положить значение переменной (поиск вверх по скоупам)
    STORE_VAR,      // операнд: String — снять значение и присвоить (env.set, иначе define) — для '='
    DECLARE_VAR,    // операнд: String — снять значение и объявить в текущем скоупе — для 'let'

    // --- Области видимости ---
    ENTER_SCOPE,    // войти в новый вложенный скоуп (блок / тело for)
    LEAVE_SCOPE,    // выйти из скоупа

    // --- Арифметика (снять b, снять a, положить результат) ---
    ADD,            // a + b  (JS: строка → конкатенация, иначе число)
    SUB,            // a - b
    MUL,            // a * b
    DIV,            // a / b  (вещественное)
    DIVIDE_INT,     // a div b (целочисленное)
    MOD,            // a mod b

    // --- Сравнения (положить boolean) ---
    EQUAL,          // ==
    NOT_EQUAL,      // !=
    LESS_THAN,      // <
    GREATER_THAN,   // >
    LESS_EQUAL,     // <=
    GREATER_EQUAL,  // >=

    // --- Унарные ---
    NEGATE,         // -x
    NOT,            // !x

    // --- Стек ---
    POP,            // выбросить верхушку (результат выражения-инструкции)

    // --- Управление потоком (операнд: Integer — адрес инструкции) ---
    JUMP,                // безусловный переход
    JUMP_IF_FALSE,       // снять; если ложь — перейти
    JUMP_IF_FALSE_KEEP,  // посмотреть (не снимая); если ложь — перейти (для &&)
    JUMP_IF_TRUE_KEEP,   // посмотреть (не снимая); если истина — перейти (для ||)

    // --- Ввод-вывод ---
    PRINT,          // операнд: Integer n — снять n значений, напечатать строку через пробел (console.log)

    // --- Массивы и объекты ---
    NEW_ARRAY,      // операнд: Integer n — собрать массив из n верхних значений
    NEW_OBJECT,     // операнд: List<String> keys — собрать объект из значений + ключей
    GET_INDEX,      // снять index, снять obj — положить obj[index]
    SET_INDEX,      // снять value, index, obj — присвоить obj[index] = value
    GET_PROP,       // операнд: String — снять obj, положить obj.prop
    SET_PROP,       // операнд: String — снять value, obj — присвоить obj.prop = value

    // --- Функции ---
    MAKE_FUNCTION,  // операнд: ASTNode.FunctionDecl — положить замыкание (FunctionVal с текущим окружением)
    CALL,           // операнд: Integer argc — вызвать функцию с argc аргументами
    RET,            // вернуть значение с верхушки стека вызывающему
    HALT;           // остановить машину (конец main-кода)

    /** Это опкод перехода (операнд — адрес инструкции)? Для дизассемблера. */
    public boolean isJump() {
        return this == JUMP || this == JUMP_IF_FALSE
                || this == JUMP_IF_FALSE_KEEP || this == JUMP_IF_TRUE_KEEP;
    }
}
