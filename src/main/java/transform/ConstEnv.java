package transform;

import ast.ASTNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Стек скоупов с известными константами. Ключ — имя переменной, значение —
 * литерал AST или null (значение есть, но не известно статически).
 * Идентификатор, чьё имя нашлось здесь литералом, можно подменить этим литералом.
 */
public final class ConstEnv {

    private final Deque<Map<String, ASTNode>> scopes = new ArrayDeque<>();

    public ConstEnv() {
        scopes.push(new HashMap<>());
    }

    public void enter() {
        scopes.push(new HashMap<>());
    }

    public void leave() {
        scopes.pop();
    }

    public void declare(String name, ASTNode literalOrNull) {
        scopes.peek().put(name, literalOrNull);
    }

    public ASTNode lookup(String name) {
        for (Map<String, ASTNode> s : scopes) {
            if (s.containsKey(name)) return s.get(name);
        }
        return null;
    }

    public void invalidate(String name) {
        for (Map<String, ASTNode> s : scopes) {
            if (s.containsKey(name)) {
                s.put(name, null);
                return;
            }
        }
    }

    public void invalidateAll() {
        for (Map<String, ASTNode> s : scopes) {
            for (var e : s.entrySet()) e.setValue(null);
        }
    }
}
