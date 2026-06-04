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
import transform.OptimizationStats;
import vm.SimpleVirtualMachine;
import vm.SimpleVmCompiler;
import vm.SimpleVmProgram;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
 *   6. Компиляция в байт-код + выполнение на виртуальной машине
 *
 * Режим самопроверки: {@code --compare} — прогнать все примеры и сравнить
 * вывод интерпретатора (этап 5) с выводом виртуальной машины (этап 6).
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--compare")) {
            runCompare();
            return;
        }

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
        Parser parser = new Parser(tokens, lexer.getSourceLines());
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
            for (SemanticError e : res.errors()) {
                System.out.println("  " + e.format(lexer.getSourceLines()));
            }
            System.out.println("\n  Этапы 4-5 пропущены.");
            return;
        }
        System.out.println("  Семантика без ошибок.");
        printGlobalScope(res);

        section("ЭТАП 3A: AST с аннотацией типов");
        System.out.println(ASTPrinter.toTreeWithTypes(ast, res.typeMap()));

        // ===== 4. Трансформация AST =====
        section("ЭТАП 4: ОПТИМИЗАЦИИ AST");
        OptimizationStats stats = new OptimizationStats();
        ASTNode transformed = AstTransformer.transform(ast, stats);
        System.out.println(ASTPrinter.toTree(transformed));
        System.out.println();
        System.out.print(stats.report());

        // ===== 5. Выполнение (AST-интерпретатор) =====
        section("ЭТАП 5: ВЫПОЛНЕНИЕ (AST-ИНТЕРПРЕТАТОР)");
        try {
            new Interpreter().run((ASTNode.Program) transformed);
        } catch (RuntimeErrorJS e) {
            System.err.println(e.formatted());
        }

        // ===== 6. Компиляция в байт-код и виртуальная машина =====
        section("ЭТАП 6: КОМПИЛЯЦИЯ В БАЙТ-КОД И ВИРТУАЛЬНАЯ МАШИНА");
        try {
            SimpleVmProgram prog = new SimpleVmCompiler().compile(transformed);
            prog.setName(source);
            prog.printDisassembly(System.out);   // дизассемблер: строки [VM][BYTECODE] ...
            System.out.println("\n  Вывод виртуальной машины:\n");
            new SimpleVirtualMachine().execute(prog);   // печать живая, как на этапе 5
        } catch (RuntimeErrorJS e) {
            System.err.println(e.formatted());
        }
    }

    /**
     * Режим самопроверки: для каждого примера из examples/ прогоняет
     * AST-интерпретатор и виртуальную машину и сравнивает их вывод.
     * Печатает OK / DIFF / SKIP по каждому файлу.
     */
    private static void runCompare() throws Exception {
        System.out.println("Сравнение: AST-интерпретатор (этап 5) vs виртуальная машина (этап 6)\n");
        int ok = 0, diff = 0, skip = 0;
        for (Path file : listExamples()) {
            String fname = file.getFileName().toString();
            String code = Files.readString(file);

            Lexer lexer = new Lexer(code);
            List<Token> tokens = lexer.tokenize();
            if (!lexer.getErrors().isEmpty()) { System.out.printf("  SKIP %-26s (лексер)%n", fname); skip++; continue; }

            Parser parser = new Parser(tokens, lexer.getSourceLines());
            ASTNode ast = parser.parseProgram();
            if (!parser.getErrors().isEmpty()) { System.out.printf("  SKIP %-26s (парсер)%n", fname); skip++; continue; }

            AnalysisResult res = new SemanticAnalyzer().analyze((ASTNode.Program) ast);
            if (res.hasErrors()) { System.out.printf("  SKIP %-26s (семантика)%n", fname); skip++; continue; }

            ASTNode transformed = AstTransformer.transform(ast, new OptimizationStats());

            String interp = captureInterpreter((ASTNode.Program) transformed);
            String vm = captureVm(transformed);

            if (interp.equals(vm)) {
                System.out.printf("  OK   %-26s%n", fname);
                ok++;
            } else {
                System.out.printf("  DIFF %-26s%n", fname);
                System.out.println("    --- интерпретатор ---");
                for (String l : interp.split("\n", -1)) System.out.println("    | " + l);
                System.out.println("    --- виртуальная машина ---");
                for (String l : vm.split("\n", -1)) System.out.println("    | " + l);
                diff++;
            }
        }
        System.out.printf("%nИтог: OK=%d, DIFF=%d, SKIP=%d%n", ok, diff, skip);
    }

    private static String captureInterpreter(ASTNode.Program program) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        try {
            System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
            try {
                new Interpreter().run(program);
            } catch (RuntimeErrorJS e) {
                System.out.println(e.formatted());
            }
        } finally {
            System.setOut(prev);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String captureVm(ASTNode transformed) {
        try {
            SimpleVmProgram prog = new SimpleVmCompiler().compile(transformed);
            return new SimpleVirtualMachine().executeCaptured(prog);
        } catch (RuntimeErrorJS e) {
            return e.formatted() + System.lineSeparator();
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
