package transform;

/** Счётчики сработавших оптимизаций — заполняются {@link AstTransformer}. */
public final class OptimizationStats {

    public int constantsFolded;        // 2 + 3 -> 5
    public int branchesEliminated;     // if (false) ... -> вырезано
    public int varsPropagated;         // x подставлен литералом из объявления
    public int algebraicSimplified;    // x + 0 -> x, x * 1 -> x, !!x -> x
    public int unreachableRemoved;     // инструкции после return / break / continue
    public int deadVarsRemoved;        // let x = 5; и x нигде не используется

    public int total() {
        return constantsFolded + branchesEliminated + varsPropagated
                + algebraicSimplified + unreachableRemoved + deadVarsRemoved;
    }

    public String report() {
        if (total() == 0) {
            return "  Оптимизации не сработали — оптимизировать нечего.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  Статистика оптимизаций:\n");
        line(sb, "свёрнуто константных выражений",     constantsFolded);
        line(sb, "удалено мёртвых веток",              branchesEliminated);
        line(sb, "проброшено констант через let",      varsPropagated);
        line(sb, "применено алгебраических тождеств",  algebraicSimplified);
        line(sb, "выброшено инструкций после return/break/continue", unreachableRemoved);
        line(sb, "удалено неиспользуемых переменных",  deadVarsRemoved);
        sb.append(String.format("    %-50s %d%n", "ВСЕГО изменений", total()));
        return sb.toString();
    }

    private static void line(StringBuilder sb, String name, int value) {
        if (value > 0) sb.append(String.format("    %-50s %d%n", name, value));
    }
}
