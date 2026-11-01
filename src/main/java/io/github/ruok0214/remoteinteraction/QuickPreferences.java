package io.github.ruok0214.remoteinteraction;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.*;

/** Per-player, per-world preferences, kept separate to preserve the existing receiver save format. */
public final class QuickPreferences extends SavedData {
    public static final Codec<QuickPreferences> CODEC=UUIDUtil.CODEC.listOf().xmap(QuickPreferences::new,p->List.copyOf(p.enabled));
    public static final SavedDataType<QuickPreferences> TYPE=new SavedDataType<>(RemoteInteraction.id("quick_preferences"),QuickPreferences::new,CODEC,null);
    private final Set<UUID> enabled=new LinkedHashSet<>();
    public QuickPreferences(){}
    private QuickPreferences(List<UUID> owners){enabled.addAll(owners);}
    public static QuickPreferences get(MinecraftServer server){return server.getDataStorage().computeIfAbsent(TYPE);}
    public boolean autoAssign(UUID owner){return enabled.contains(owner);}
    public void setAutoAssign(UUID owner,boolean value){
        if(value?enabled.add(owner):enabled.remove(owner))setDirty();
    }
}
