package io.github.ruok0214.remoteinteraction.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class ShortcutSettings {
    public enum Action { MENU, ATTACH, QUICK }
    private final Path file;
    private final EnumMap<Action, Shortcut> bindings = new EnumMap<>(Action.class);
    public ShortcutSettings(Path file) { this.file = file; for (var action : Action.values()) bindings.put(action, Shortcut.UNBOUND); }
    public static ShortcutSettings load() {
        var settings = new ShortcutSettings(FabricLoader.getInstance().getConfigDir().resolve("remote-interaction-keys.json"));
        settings.read(); return settings;
    }
    public Shortcut get(Action action) { return bindings.get(action); }
    public String assign(Action action, Shortcut binding) {
        if (binding.bound()) for (var other : Action.values())
            if (other != action && binding.equals(get(other))) return "keys.duplicate";
        var previous = bindings.put(action, binding.bound() ? binding : Shortcut.UNBOUND);
        try {
            Files.createDirectories(file.getParent());
            var json = new JsonObject();
            for (var a : Action.values()) {
                var b = new JsonObject(); b.addProperty("key", get(a).key()); b.addProperty("modifiers", get(a).modifiers());
                json.add(a.name(), b);
            }
            var temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            return "keys.saved";
        } catch (IOException e) { bindings.put(action, previous); LoggerFactory.getLogger(getClass()).warn("Cannot save shortcuts", e); return "keys.save_failed"; }
    }
    public void read() {
        if (!Files.exists(file)) return;
        try {
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            var loaded = new EnumMap<Action, Shortcut>(Action.class);
            for (var a : Action.values()) {
                Shortcut b = Shortcut.UNBOUND;
                if (json.has(a.name())) {
                    var value = json.getAsJsonObject(a.name());
                    var parsed = new Shortcut(value.get("key").getAsInt(), value.get("modifiers").getAsInt());
                    if (parsed.bound() && !loaded.containsValue(parsed)) b = parsed;
                }
                loaded.put(a, b);
            }
            bindings.putAll(loaded);
        } catch (IOException | RuntimeException e) { LoggerFactory.getLogger(getClass()).warn("Cannot read shortcuts; using unbound defaults", e); }
    }
}
