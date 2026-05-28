package lexer;

import diagnostic.Diagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Лексический анализатор (токенизатор) для MiniJS.
 *
 * Реализован вручную (без ANTLR/Flex). Принцип работы:
 * посимвольно читаем входной текст и группируем символы в токены,
 * используя конечный автомат (switch по текущему символу).
 */
public class Lexer {
    private final String source;
    private int pos;       // текущая позиция в source
    private int line;      // текущая строка (для диагностики)
    private int column;    // текущий столбец
    private final List<String> errors = new ArrayList<>();
    private final List<String> sourceLines;

    /** Таблица ключевых слов */
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("let",       TokenType.LET);
        KEYWORDS.put("if",        TokenType.IF);
        KEYWORDS.put("else",      TokenType.ELSE);
        KEYWORDS.put("while",     TokenType.WHILE);
        KEYWORDS.put("for",       TokenType.FOR);
        KEYWORDS.put("break",     TokenType.BREAK);
        KEYWORDS.put("continue",  TokenType.CONTINUE);
        KEYWORDS.put("function",  TokenType.FUNCTION);
        KEYWORDS.put("return",    TokenType.RETURN);
        KEYWORDS.put("true",      TokenType.TRUE);
        KEYWORDS.put("false",     TokenType.FALSE);
        KEYWORDS.put("null",      TokenType.NULL);
        KEYWORDS.put("undefined", TokenType.UNDEFINED);
        KEYWORDS.put("div",       TokenType.DIV_KW);
        KEYWORDS.put("mod",       TokenType.MOD_KW);
        KEYWORDS.put("console",   TokenType.CONSOLE);
    }

    public Lexer(String source) {
        this.source = source;
        this.sourceLines = Diagnostic.splitLines(source);
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    public List<String> getSourceLines() {
        return sourceLines;
    }

    private String diag(String message, int ln, int col) {
        return Diagnostic.format("[ОШИБКА ЛЕКСЕРА]", message, ln, col, sourceLines);
    }

    public List<String> getErrors() {
        return errors;
    }

    /**
     * Основной метод: преобразует весь исходный код в список токенов.
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < source.length()) {
            skipWhitespaceAndComments();
            if (pos >= source.length()) break;

            char c = current();
            int startLine = line;
            int startCol = column;

            Token token = null;

            // Числа: 0-9 → NUMBER
            if (Character.isDigit(c)) {
                token = readNumber(startLine, startCol);
            }
            // Строки: " или ' → STRING
            else if (c == '"' || c == '\'') {
                token = readString(startLine, startCol);
            }
            // Идентификаторы и ключевые слова: a-z, A-Z, _, $
            else if (Character.isLetter(c) || c == '_' || c == '$') {
                token = readIdentifierOrKeyword(startLine, startCol);
            }
            // Операторы и разделители
            else {
                token = readOperatorOrDelimiter(startLine, startCol);
            }

            if (token != null) {
                tokens.add(token);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    // ====== Чтение токенов по категориям ======

    /** Чтение числового литерала: целого или дробного */
    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && Character.isDigit(current())) {
            sb.append(current());
            advance();
        }
        // Дробная часть
        if (pos < source.length() && current() == '.' && pos + 1 < source.length()
                && Character.isDigit(source.charAt(pos + 1))) {
            sb.append('.');
            advance();
            while (pos < source.length() && Character.isDigit(current())) {
                sb.append(current());
                advance();
            }
        }
        return new Token(TokenType.NUMBER, sb.toString(), startLine, startCol);
    }

    /** Чтение строкового литерала с поддержкой escape-последовательностей */
    private Token readString(int startLine, int startCol) {
        char quote = current();
        advance(); // пропускаем открывающую кавычку
        StringBuilder sb = new StringBuilder();
        sb.append(quote);

        while (pos < source.length() && current() != quote) {
            if (current() == '\\') {
                sb.append(current());
                advance();
                if (pos < source.length()) {
                    sb.append(current());
                    advance();
                }
            } else if (current() == '\n') {
                // Незакрытая строка
                errors.add(diag("Незакрытый строковый литерал", startLine, startCol));
                return new Token(TokenType.ERROR, sb.toString(), startLine, startCol);
            } else {
                sb.append(current());
                advance();
            }
        }

        if (pos >= source.length()) {
            errors.add(diag("Незакрытый строковый литерал (конец файла)", startLine, startCol));
            return new Token(TokenType.ERROR, sb.toString(), startLine, startCol);
        }

        sb.append(quote);
        advance(); // пропускаем закрывающую кавычку
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    /** Чтение идентификатора или ключевого слова */
    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() &&
                (Character.isLetterOrDigit(current()) || current() == '_' || current() == '$')) {
            sb.append(current());
            advance();
        }
        String word = sb.toString();

        // Проверяем, является ли слово ключевым
        TokenType kwType = KEYWORDS.get(word);
        if (kwType != null) {
            return new Token(kwType, word, startLine, startCol);
        }
        return new Token(TokenType.IDENTIFIER, word, startLine, startCol);
    }

    /** Чтение оператора или разделителя (одно- и двухсимвольных) */
    private Token readOperatorOrDelimiter(int startLine, int startCol) {
        char c = current();
        advance();

        switch (c) {
            case '+': return new Token(TokenType.PLUS,     "+", startLine, startCol);
            case '-': return new Token(TokenType.MINUS,    "-", startLine, startCol);
            case '*': return new Token(TokenType.STAR,     "*", startLine, startCol);
            case '/': return new Token(TokenType.SLASH,    "/", startLine, startCol);
            case '(': return new Token(TokenType.LPAREN,   "(", startLine, startCol);
            case ')': return new Token(TokenType.RPAREN,   ")", startLine, startCol);
            case '{': return new Token(TokenType.LBRACE,   "{", startLine, startCol);
            case '}': return new Token(TokenType.RBRACE,   "}", startLine, startCol);
            case '[': return new Token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']': return new Token(TokenType.RBRACKET, "]", startLine, startCol);
            case ';': return new Token(TokenType.SEMICOLON,";", startLine, startCol);
            case ',': return new Token(TokenType.COMMA,    ",", startLine, startCol);
            case ':': return new Token(TokenType.COLON,    ":", startLine, startCol);
            case '.': return new Token(TokenType.DOT,      ".", startLine, startCol);

            case '=':
                if (pos < source.length() && current() == '=') {
                    advance();
                    return new Token(TokenType.EQ, "==", startLine, startCol);
                }
                return new Token(TokenType.ASSIGN, "=", startLine, startCol);

            case '!':
                if (pos < source.length() && current() == '=') {
                    advance();
                    return new Token(TokenType.NEQ, "!=", startLine, startCol);
                }
                return new Token(TokenType.NOT, "!", startLine, startCol);

            case '<':
                if (pos < source.length() && current() == '=') {
                    advance();
                    return new Token(TokenType.LE, "<=", startLine, startCol);
                }
                return new Token(TokenType.LT, "<", startLine, startCol);

            case '>':
                if (pos < source.length() && current() == '=') {
                    advance();
                    return new Token(TokenType.GE, ">=", startLine, startCol);
                }
                return new Token(TokenType.GT, ">", startLine, startCol);

            case '&':
                if (pos < source.length() && current() == '&') {
                    advance();
                    return new Token(TokenType.AND, "&&", startLine, startCol);
                }
                errors.add(diag("Неожиданный символ '&' (ожидалось '&&')", startLine, startCol));
                return new Token(TokenType.ERROR, "&", startLine, startCol);

            case '|':
                if (pos < source.length() && current() == '|') {
                    advance();
                    return new Token(TokenType.OR, "||", startLine, startCol);
                }
                errors.add(diag("Неожиданный символ '|' (ожидалось '||')", startLine, startCol));
                return new Token(TokenType.ERROR, "|", startLine, startCol);

            default:
                errors.add(diag(String.format("Неизвестный символ '%c'", c), startLine, startCol));
                return new Token(TokenType.ERROR, String.valueOf(c), startLine, startCol);
        }
    }

    // ====== Вспомогательные методы ======

    /** Пропускает пробелы, табуляции, переносы строк и комментарии */
    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char c = current();

            // Пробельные символы
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
                continue;
            }

            // Однострочный комментарий: // ...
            if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                while (pos < source.length() && current() != '\n') {
                    advance();
                }
                continue;
            }

            // Многострочный комментарий: /* ... */
            if (c == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                int commentLine = line;
                int commentCol = column;
                advance(); // /
                advance(); // *
                while (pos < source.length()) {
                    if (current() == '*' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                        advance(); // *
                        advance(); // /
                        break;
                    }
                    advance();
                }
                if (pos >= source.length()) {
                    errors.add(diag("Незакрытый блочный комментарий", commentLine, commentCol));
                }
                continue;
            }

            break; // не пробел и не комментарий — выходим
        }
    }

    /** Текущий символ */
    private char current() {
        return source.charAt(pos);
    }

    /** Продвинуться на один символ, обновляя строку/столбец */
    private void advance() {
        if (pos < source.length()) {
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            pos++;
        }
    }
}
