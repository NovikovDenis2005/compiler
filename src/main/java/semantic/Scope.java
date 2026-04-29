package semantic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Лексическая область видимости. Поиск идёт по цепочке родителей вверх.
 * LinkedHashMap — чтобы при печати таблицы символы шли в порядке объявления.
 */
public class Scope {
    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope parent() {
        return parent;
    }

    public Map<String, Symbol> symbols() {
        return symbols;
    }

    /** false — если имя уже занято в этой же области. */
    public boolean declare(Symbol s) {
        if (symbols.containsKey(s.name())) return false;
        symbols.put(s.name(), s);
        return true;
    }

    /** Возвращает символ из текущей области или любой объемлющей. */
    public Symbol resolve(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            Symbol sym = s.symbols.get(name);
            if (sym != null) return sym;
        }
        return null;
    }

    /** Только в текущей области (для проверки повторного объявления). */
    public Symbol resolveLocal(String name) {
        return symbols.get(name);
    }
}
