package interpreter;

import java.util.HashMap;
import java.util.Map;

/** Runtime-область видимости. Параллельна семантической Scope, но хранит значения. */
public class Environment {
    private final Environment parent;
    private final Map<String, Value> bindings = new HashMap<>();

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Value v) {
        bindings.put(name, v);
    }

    public Value get(String name) {
        for (Environment e = this; e != null; e = e.parent) {
            if (e.bindings.containsKey(name)) return e.bindings.get(name);
        }
        return Value.UndefinedVal.INSTANCE;
    }

    /** Присваивание поднимается по цепочке к месту объявления. */
    public boolean set(String name, Value v) {
        for (Environment e = this; e != null; e = e.parent) {
            if (e.bindings.containsKey(name)) {
                e.bindings.put(name, v);
                return true;
            }
        }
        return false;
    }
}
