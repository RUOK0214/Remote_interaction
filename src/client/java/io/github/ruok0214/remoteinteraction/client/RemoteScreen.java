package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class RemoteScreen extends Screen {
    private final Screen parent;
    private EditBox search;
    private String query = "", notice = "";
    private UUID selected;
    private int page, left, top, panelWidth, panelHeight, rows, noticeTicks;
    private List<RemotePayloads.Entry> visible = List.of();
    public RemoteScreen(Screen parent) { super(tr("title")); this.parent = parent; }
    public static Component tr(String key, Object... args) { return Component.translatable("remote_interaction." + key, args); }
    public static Component label(RemotePayloads.Entry entry) {
        return entry.name().isBlank() ? BuiltInRegistries.BLOCK.getValue(entry.block()).getName() : Component.literal(entry.name());
    }
    public static Component stateLabel(RemotePayloads.Entry entry) {
        if (!entry.status().equals("ready")) return tr("status."+entry.status());
        return entry.state().startsWith("note_") ? tr("state.note",entry.state().substring(5)) : tr("state."+entry.state());
    }
    @Override protected void init() {
        panelWidth = Math.min(500, width - 16); panelHeight = height - 16;
        left = (width - panelWidth) / 2; top = 8;
        rows = Math.max(1, (panelHeight - 190) / 42);
        search = addRenderableWidget(new EditBox(font, left + 10, top + 44, panelWidth - 20, 20, tr("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(tr("search"));
        search.setResponder(value -> { query = value; page = 0; rebuildRows(); });
        rebuildRows(); RemoteClient.send(RemotePayloads.Request.list());
    }
    private List<RemotePayloads.Entry> filtered() {
        String needle = query.toLowerCase(Locale.ROOT);
        return RemoteClient.entries().stream().filter(e -> (label(e).getString() + " " + e.pos().toShortString())
                .toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private RemotePayloads.Entry selection() {
        return RemoteClient.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null);
    }
    private void rebuildRows() {
        for (var child : List.copyOf(children())) if (child != search) removeWidget(child);
        var all = filtered();
        int pages = Math.max(1, (all.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        visible = all.subList(Math.min(page * rows, all.size()), Math.min((page + 1) * rows, all.size()));
        for (int i = 0; i < visible.size(); i++) {
            var entry = visible.get(i); int y = top + 74 + i * 42;
            var select = Button.builder(Component.literal((entry.id().equals(selected) ? "▸ " : "") + font.plainSubstrByWidth(label(entry).getString(), panelWidth - 126)),
                    b -> { selected = entry.id(); rebuildRows(); }).bounds(left + 10, y, panelWidth - 104, 20).build();
            select.setTooltip(Tooltip.create(Component.literal(label(entry).getString() + "\n" + entry.dimension() + "  " + entry.pos().toShortString())));
            addRenderableWidget(select);
            var activate = addRenderableWidget(Button.builder(tr("activate"), b -> {
                RemoteClient.send(new RemotePayloads.Request(RemotePayloads.Action.ACTIVATE, entry.id(), ""));
                b.active = false;
            }).bounds(left + panelWidth - 88, y, 78, 20).build());
            activate.active = RemoteClient.available() && entry.status().equals("ready");
            activate.setTooltip(Tooltip.create(tr("status." + entry.status())));
        }
        int nav = top + panelHeight - 108;
        var prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildRows(); }).bounds(left+10, nav, 28, 20).build());
        prev.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildRows(); }).bounds(left+panelWidth-38, nav, 28, 20).build());
        next.active = page+1 < pages;
        int tools = top+panelHeight-54, toolWidth=(panelWidth-28)/3;
        var attach=addRenderableWidget(Button.builder(tr("attach"),b->RemoteClient.startPlacing()).bounds(left+10,tools,toolWidth,20).build());
        attach.active=RemoteClient.available();
        addRenderableWidget(Button.builder(tr("keys.title"),b->minecraft.gui.setScreen(new KeySettingsScreen(this))).bounds(left+14+toolWidth,tools,toolWidth,20).build());
        addRenderableWidget(Button.builder(tr("slots.title"),b->minecraft.gui.setScreen(new QuickSettingsScreen(this))).bounds(left+18+2*toolWidth,tools,toolWidth,20).build());
        int footer = top + panelHeight - 28, gap = 4, w = (panelWidth - 32) / 4;
        var rename = addRenderableWidget(Button.builder(tr("rename"), b -> minecraft.gui.setScreen(new RenameScreen(this, selection())))
                .bounds(left+10, footer, w, 20).build());
        var remove = addRenderableWidget(Button.builder(tr("remove"), b -> {
            var entry = selection();
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.gui.setScreen(this);
                if (confirmed) RemoteClient.send(new RemotePayloads.Request(RemotePayloads.Action.REMOVE, entry.id(), ""));
            }, tr("remove_title"), tr("remove_confirm", label(entry))));
        }).bounds(left+10+w+gap, footer, w, 20).build());
        rename.active = remove.active = selection() != null && RemoteClient.available();
        addRenderableWidget(Button.builder(tr("help"), b -> minecraft.gui.setScreen(new HelpScreen(this))).bounds(left+10+(w+gap)*2, footer, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left+10+(w+gap)*3, footer, w, 20).build());
    }
    public void updated(String message) {
        if (!message.isEmpty()) { notice = message; noticeTicks = 80; }
        if (search != null) rebuildRows();
    }
    @Override public void tick() { if (noticeTicks > 0) noticeTicks--; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractRenderState(g, x, y, dt);
        g.centeredText(font, title, width/2, top+8, 0xFFFFFFFF);
        g.centeredText(font, tr("hint"), width/2, top+26, 0xFFAEC3D6);
        for (int i = 0; i < visible.size(); i++) {
            var entry = visible.get(i);
            String detail = entry.pos().toShortString() + "  ·  " + tr("status." + entry.status()).getString();
            if (!entry.state().isEmpty()) detail += "  ·  " + (entry.state().startsWith("note_")
                    ? tr("state.note", entry.state().substring(5)) : tr("state." + entry.state())).getString();
            g.text(font, font.plainSubstrByWidth(detail, panelWidth-24), left+12, top+98+i*42,
                    entry.status().equals("ready") ? 0xFF7FE5C7 : 0xFFDBAE7E);
        }
        if (visible.isEmpty()) {
            String key = !RemoteClient.available() ? (minecraft.player == null ? "join_world" : "server_required")
                    : query.isEmpty() ? "empty" : "no_results";
            g.centeredText(font, tr(key), width/2, top+83, 0xFFE0C28A);
        }
        int total = filtered().size(), pages = Math.max(1, (total+rows-1)/rows);
        g.centeredText(font, tr("page", page+1, pages, RemoteClient.entries().size()), width/2, top+panelHeight-102, 0xFFCAD5E3);
        Component message = noticeTicks > 0 ? tr("message."+notice) : tr("select_hint");
        g.centeredText(font, message, width/2, top+panelHeight-74, 0xFFF1DB9B);
    }
}
