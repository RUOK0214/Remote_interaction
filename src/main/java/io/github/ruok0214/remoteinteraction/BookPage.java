package io.github.ruok0214.remoteinteraction;

/** One-based page information carried in the existing receiver state field. */
public record BookPage(int current, int total) {
    public boolean canTurn(int direction) {
        return direction == -1 ? current > 1 : direction == 1 && current < total;
    }
    public String state() { return "page_" + current + "_" + total; }
    public static BookPage parse(String state) {
        if (!state.startsWith("page_")) return null;
        String[] parts = state.split("_");
        if (parts.length != 3) return null;
        try {
            int current = Integer.parseInt(parts[1]), total = Integer.parseInt(parts[2]);
            return current >= 1 && current <= total ? new BookPage(current, total) : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}
