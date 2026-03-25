package lexer;

/**
 * Типы токенов для лексического анализатора MiniJS.
 */
public enum TokenType {
    // Литералы
    NUMBER,         // 42, 3.14
    STRING,         // "hello", 'world'
    IDENTIFIER,     // myVar, _count, $el

    // Ключевые слова
    LET,            // let
    IF,             // if
    ELSE,           // else
    WHILE,          // while
    FOR,            // for
    BREAK,          // break
    CONTINUE,       // continue
    FUNCTION,       // function
    RETURN,         // return
    TRUE,           // true
    FALSE,          // false
    NULL,           // null
    UNDEFINED,      // undefined
    DIV_KW,         // div (целочисленное деление)
    MOD_KW,         // mod (остаток)
    CONSOLE,        // console
    LOG,            // log

    // Операторы
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    ASSIGN,         // =
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    LE,             // <=
    GE,             // >=
    NOT,            // !
    AND,            // &&
    OR,             // ||
    DOT,            // .

    // Разделители
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    LBRACKET,       // [
    RBRACKET,       // ]
    SEMICOLON,      // ;
    COMMA,          // ,
    COLON,          // :

    // Служебные
    EOF,            // конец файла
    ERROR           // ошибочный токен
}
