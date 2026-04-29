package interpreter;

import ast.ASTNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-значение miniJS. Sealed — позволяет switch-у быть исчерпывающим.
 *
 * Числа храним как double (как в JS). Целые печатаем без точки.
 * NULL и UNDEFINED — синглтоны.
 */
public sealed interface Value {

    String display();

    record NumberVal(double v) implements Value {
        @Override
        public String display() {
            if (Double.isNaN(v)) return "NaN";
            if (v == (long) v) return Long.toString((long) v);
            return Double.toString(v);
        }
    }

    record StringVal(String v) implements Value {
        @Override public String display() { return v; }
    }

    record BooleanVal(boolean v) implements Value {
        @Override public String display() { return Boolean.toString(v); }
    }

    final class NullVal implements Value {
        public static final NullVal INSTANCE = new NullVal();
        private NullVal() {}
        @Override public String display() { return "null"; }
    }

    final class UndefinedVal implements Value {
        public static final UndefinedVal INSTANCE = new UndefinedVal();
        private UndefinedVal() {}
        @Override public String display() { return "undefined"; }
    }

    final class ArrayVal implements Value {
        private final List<Value> elements;
        public ArrayVal(List<Value> elements) { this.elements = elements; }
        public List<Value> elements() { return elements; }
        @Override
        public String display() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                Value e = elements.get(i);
                sb.append(e instanceof StringVal s ? "\"" + s.v() + "\"" : e.display());
            }
            return sb.append("]").toString();
        }
    }

    final class ObjectVal implements Value {
        private final Map<String, Value> fields;
        public ObjectVal() { this.fields = new LinkedHashMap<>(); }
        public ObjectVal(Map<String, Value> fields) { this.fields = fields; }
        public Map<String, Value> fields() { return fields; }
        @Override
        public String display() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var e : fields.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append(": ");
                Value v = e.getValue();
                sb.append(v instanceof StringVal s ? "\"" + s.v() + "\"" : v.display());
            }
            return sb.append("}").toString();
        }
    }

    record FunctionVal(ASTNode.FunctionDecl decl, Environment closure) implements Value {
        @Override public String display() { return "function " + decl.name() + "()"; }
    }
}
