package com.lineage2bot.craft;

public record Item(
        int id,
        String name,
        String grade,
        String typeMain,
        String typeSub,
        String typeSlot,
        String icon
) {
    public String searchable() {
        return (name + " " + grade + " " + typeMain + " " + typeSub + " " + typeSlot).toLowerCase();
    }
}
