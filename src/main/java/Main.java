import ast.ASTNode;
import ast.ASTPrinter;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;

import java.util.List;

/**
 * Точка входа компилятора MiniJS.
 *
 * Демонстрирует работу:
 * 1. Лексического анализатора (токенизация)
 * 2. Синтаксического анализатора (рекурсивный спуск → AST)
 * 3. Печать AST-дерева в двух форматах
 * 4. Диагностику синтаксических ошибок
 */
public class Main {
    public static void main(String[] args) {

        String code = """
                // Объявление переменных с различными типами
                let limit = 10;
                let count = 0;
                let name = "World";
                let flag = true;
                let nothing = null;
                let arr = [1, 2, 3, 4, 5];
                let obj = { x: 10, y: 20 };
                
                // Цикл while с if/else и операторами mod, div
                while (count < limit) {
                    if (count mod 2 == 0) {
                        console.log("Even");
                    } else {
                        console.log("Odd");
                    }
                    
                    let half = count div 2;
                    count = count + 1;
                }
                
                // Цикл for
                for (let i = 0; i < 5; i = i + 1) {
                    if (i == 3) {
                        break;
                    }
                    console.log(i);
                }
                
                // Объявление и вызов функции
                function add(a, b) {
                    return a + b;
                }
                
                let result = add(3, 4);
                console.log(result);
                
                // Доступ к элементам массива и объекта
                let first = arr[0];
                let xVal = obj.x;
                
                // else if
                if (count == 0) {
                    console.log("zero");
                } else if (count == 1) {
                    console.log("one");
                } else {
                    console.log("other");
                }

                // Присваивание элементу массива и свойству объекта
                arr[0] = 99;
                obj.x = 42;

                // Сложные выражения с приоритетом
                let complex = 2 + 3 * 4 - 1;
                let logic = (count > 0) && (flag != false) || !flag;
                """;

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  MiniJS Compiler — Подмножество JavaScript");
        System.out.println("  1-я аттестация: Лексер + Парсер + AST");
        System.out.println("═══════════════════════════════════════════════════");

        // ===== 1. Лексический анализ =====
        System.out.println("\n─── ЭТАП 1: ЛЕКСИЧЕСКИЙ АНАЛИЗ ───\n");
        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();

        // Выводим первые N токенов для демонстрации
        int showTokens = Math.min(tokens.size(), 30);
        for (int i = 0; i < showTokens; i++) {
            System.out.println("  " + tokens.get(i));
        }
        if (tokens.size() > showTokens) {
            System.out.println("  ... и ещё " + (tokens.size() - showTokens) + " токенов");
        }
        System.out.println("\n  Всего токенов: " + tokens.size());

        if (!lexer.getErrors().isEmpty()) {
            System.out.println("\n  Ошибки лексера:");
            for (String err : lexer.getErrors()) {
                System.err.println("  " + err);
            }
        }

        // ===== 2. Синтаксический анализ =====
        System.out.println("\n─── ЭТАП 2: СИНТАКСИЧЕСКИЙ АНАЛИЗ (РЕКУРСИВНЫЙ СПУСК) ───\n");
        Parser parser = new Parser(tokens);
        ASTNode ast = parser.parseProgram();

        if (!parser.getErrors().isEmpty()) {
            System.out.println("  Ошибки парсера:");
            for (String err : parser.getErrors()) {
                System.err.println("  " + err);
            }
        } else {
            System.out.println("  Разбор прошёл успешно, ошибок нет.");
        }

        // ===== 3. Печать AST =====
        System.out.println("\n─── ЭТАП 3: AST В LISP-СТИЛЕ ───\n");
        System.out.println(ASTPrinter.toLisp(ast));

        System.out.println("\n─── ЭТАП 4: ИЕРАРХИЧЕСКОЕ AST-ДЕРЕВО ───\n");
        System.out.println(ASTPrinter.toTree(ast));

        // ===== 4. Демонстрация диагностики ошибок =====
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  ДЕМОНСТРАЦИЯ ДИАГНОСТИКИ ОШИБОК");
        System.out.println("═══════════════════════════════════════════════════\n");

        String brokenCode = """
                let x = 10
                let y = ;
                if (x > 0 {
                    console.log("hello");
                }
                let = 5;
                """;

        System.out.println("  Код с ошибками:");
        System.out.println("  ───────────────");
        for (String line : brokenCode.split("\n")) {
            System.out.println("    " + line);
        }
        System.out.println();

        Lexer brokenLexer = new Lexer(brokenCode);
        List<Token> brokenTokens = brokenLexer.tokenize();
        Parser brokenParser = new Parser(brokenTokens);
        brokenParser.parseProgram();

        System.out.println("  Обнаруженные ошибки:");
        for (String err : brokenLexer.getErrors()) {
            System.out.println("  " + err);
        }
        for (String err : brokenParser.getErrors()) {
            System.out.println("  " + err);
        }
        if (brokenLexer.getErrors().isEmpty() && brokenParser.getErrors().isEmpty()) {
            System.out.println("  (ошибок не обнаружено)");
        }
    }
}
