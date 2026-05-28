package diagnostic;

import java.util.List;

/**
 * Форматирование сообщения об ошибке: заголовок, позиция, текст и (если есть
 * исходный код) фрагмент строки с кареткой под местом ошибки. Используется
 * лексером, парсером и семантическим анализатором.
 */
public final class Diagnostic {

    private Diagnostic() {}

    public static String format(String prefix, String message,
                                int line, int column, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" Строка ").append(line).append(':').append(column)
                .append(" -> ").append(message);
        String snippet = snippetOrEmpty(line, column, lines);
        if (!snippet.isEmpty()) {
            sb.append('\n').append(snippet);
        }
        return sb.toString();
    }

    public static List<String> splitLines(String source) {
        // limit=-1: сохраняем пустые хвостовые строки, чтобы нумерация совпадала с исходником.
        return List.of(source.split("\\R", -1));
    }

    private static String snippetOrEmpty(int line, int column, List<String> lines) {
        if (lines == null || line < 1 || line > lines.size()) return "";
        String src = lines.get(line - 1);
        String numbered = String.format("    %d | %s", line, src);
        StringBuilder caret = new StringBuilder("    ");
        int prefixLen = String.valueOf(line).length() + 3; // " | "
        for (int i = 0; i < prefixLen; i++) caret.append(' ');
        int col = Math.max(1, column);
        for (int i = 1; i < col; i++) {
            char c = i - 1 < src.length() ? src.charAt(i - 1) : ' ';
            caret.append(c == '\t' ? '\t' : ' ');
        }
        caret.append('^');
        return numbered + "\n" + caret;
    }
}