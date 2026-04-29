import ast.ASTNode;
import ast.ASTPrinter;
import interpreter.Interpreter;
import interpreter.RuntimeErrorJS;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import semantic.AnalysisResult;
import semantic.SemanticAnalyzer;
import semantic.SemanticError;
import semantic.Symbol;
import transform.AstTransformer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

/**
 * Точка входа компилятора MiniJS.
 *
 * Этапы вывода:
 *   1. Лексический анализ
 *   2. Синтаксический анализ + AST (LISP / дерево)
 *   3. Семантический анализ + диагностика + типы
 *   4. Модификация AST (свёртка констант, мёртвые ветки)
 *   5. Выполнение (AST-интерпретатор)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String code;
        String source;

        if (args.length > 0) {
            code = Files.readString(Path.of(args[0]));
            source = args[0];
        } else {
            Path chosen = pickInteractive();
            if (chosen == null) {
                code = BUILTIN_PROGRAM;
                source = "встроенный пример";
            } else {
                code = Files.readString(chosen);
                source = chosen.toString();
            }
        }

        banner("MiniJS Compiler — Подмножество JavaScript", "источник: " + source);

        // ===== 1. Лексер =====
        section("ЭТАП 1: ЛЕКСИЧЕСКИЙ АНАЛИЗ");
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();
        int show = Math.min(tokens.size(), 30);
        for (int i = 0; i < show; i++) System.out.println("  " + tokens.get(i));
        if (tokens.size() > show) System.out.println("  ... и ещё " + (tokens.size() - show) + " токенов");
        System.out.println("\n  Всего токенов: " + tokens.size());
        if (!lexer.getErrors().isEmpty()) {
            System.out.println("\n  Ошибки лексера:");
            for (String err : lexer.getErrors()) System.err.println("  " + err);
            return;
        }

        // ===== 2. Парсер =====
        section("ЭТАП 2: СИНТАКСИЧЕСКИЙ АНАЛИЗ");
        Parser parser = new Parser(tokens);
        ASTNode ast = parser.parseProgram();
        if (!parser.getErrors().isEmpty()) {
            for (String err : parser.getErrors()) System.err.println("  " + err);
            return;
        }
        System.out.println("  Разбор успешен.");

        section("ЭТАП 2A: AST (LISP)");
        System.out.println(ASTPrinter.toLisp(ast));
        section("ЭТАП 2B: AST (дерево)");
        System.out.println(ASTPrinter.toTree(ast));

        // ===== 3. Семантика =====
        section("ЭТАП 3: СЕМАНТИЧЕСКИЙ АНАЛИЗ");
        AnalysisResult res = new SemanticAnalyzer().analyze((ASTNode.Program) ast);
        if (res.hasErrors()) {
            System.out.println("  Найдены семантические ошибки:");
            for (SemanticError e : res.errors()) System.out.println("  " + e);
            System.out.println("\n  Этапы 4-5 пропущены.");
            return;
        }
        System.out.println("  Семантика без ошибок.");
        printGlobalScope(res);

        section("ЭТАП 3A: AST с аннотацией типов");
        System.out.println(ASTPrinter.toTreeWithTypes(ast, res.typeMap()));

        // ===== 4. Трансформация AST =====
        section("ЭТАП 4: МОДИФИКАЦИЯ AST (свёртка констант, мёртвые ветки)");
        ASTNode transformed = AstTransformer.transform(ast);
        System.out.println(ASTPrinter.toTree(transformed));

        // ===== 5. Выполнение =====
        section("ЭТАП 5: ВЫПОЛНЕНИЕ");
        try {
            new Interpreter().run((ASTNode.Program) transformed);
        } catch (RuntimeErrorJS e) {
            System.err.println(e.formatted());
        }
    }

    private static void printGlobalScope(AnalysisResult res) {
        var syms = res.globalScope().symbols();
        if (syms.isEmpty()) return;
        System.out.println("\n  Глобальная таблица символов:");
        for (Symbol s : syms.values()) {
            System.out.printf("    %-15s %-10s %s%n", s.name(), s.kind(), s.declaredAt());
        }
    }

    private static void banner(String title, String subtitle) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  " + title);
        System.out.println("  " + subtitle);
        System.out.println("═══════════════════════════════════════════════════");
    }

    private static void section(String title) {
        System.out.println("\n─── " + title + " ───\n");
    }

    /**
     * Возвращает выбранный пользователем .js-файл из examples/,
     * либо null если выбран встроенный пример или ввод некорректен.
     */
    private static Path pickInteractive() {
        List<Path> files = listExamples();

        System.out.println("Выберите программу для запуска:");
        System.out.println("  0 — встроенный пример");
        for (int i = 0; i < files.size(); i++) {
            System.out.printf("  %d — %s%n", i + 1, files.get(i).getFileName());
        }
        System.out.print("Номер: ");

        Scanner sc = new Scanner(System.in);
        if (!sc.hasNextLine()) return null;
        String line = sc.nextLine().trim();

        int n;
        try { n = Integer.parseInt(line); }
        catch (NumberFormatException e) {
            System.out.println("Не число — запускаю встроенный пример.\n");
            return null;
        }
        if (n == 0) return null;
        if (n < 1 || n > files.size()) {
            System.out.println("Нет такого пункта — запускаю встроенный пример.\n");
            return null;
        }
        return files.get(n - 1);
    }

    private static List<Path> listExamples() {
        Path dir = Path.of("examples");
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> out = new ArrayList<>();
            s.filter(p -> p.getFileName().toString().endsWith(".js"))
             .sorted()
             .forEach(out::add);
            return out;
        } catch (java.io.IOException e) {
            return List.of();
        }
    }

    // Встроенный пример программы — запускается, если файл не передан
    private static final String BUILTIN_PROGRAM = """
            let limit = 10;
            let count = 0;
            let constExpr = 2 + 3 * 4;

            function add(a, b) {
                return a + b;
            }

            while (count < limit) {
                if (count mod 2 == 0) {
                    console.log("even", count);
                } else {
                    console.log("odd", count);
                }
                count = count + 1;
            }

            console.log("sum =", add(constExpr, count));

            if (false) {
                console.log("этого не будет");
            } else {
                console.log("done");
            }
            """;
}
