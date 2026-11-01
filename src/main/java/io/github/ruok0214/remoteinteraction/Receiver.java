package io.github.ruok0214.remoteinteraction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import java.util.*;

public record Receiver(UUID id, UUID owner, Identifier dimension, BlockPos pos, Direction face,
                       Identifier block, String name, boolean paid, boolean lost, List<Integer> slots) {
    public static final int MAX_NAME = 32;
    public static final int SLOT_COUNT = 15;
    public static final Codec<Receiver> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Receiver::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Receiver::owner),
            Identifier.CODEC.fieldOf("dimension").forGetter(Receiver::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Receiver::pos),
            Direction.CODEC.fieldOf("face").forGetter(Receiver::face),
            Identifier.CODEC.fieldOf("block").forGetter(Receiver::block),
            Codec.STRING.fieldOf("name").forGetter(Receiver::name),
            Codec.BOOL.optionalFieldOf("paid", false).forGetter(Receiver::paid),
            Codec.BOOL.optionalFieldOf("lost", false).forGetter(Receiver::lost),
            Codec.intRange(0, SLOT_COUNT - 1).listOf().optionalFieldOf("slots", List.of()).forGetter(Receiver::slots)
    ).apply(i, Receiver::new));

    public Receiver {
        slots = slots.stream().filter(s -> s >= 0 && s < SLOT_COUNT).distinct().sorted().toList();
    }
    public Receiver(UUID id, UUID owner, Identifier dimension, BlockPos pos, Direction face,
                    Identifier block, String name, boolean paid, boolean lost) {
        this(id, owner, dimension, pos, face, block, name, paid, lost, List.of());
    }

    public Receiver renamed(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new Receiver(id, owner, dimension, pos, face, block, clean, paid, lost, slots);
    }
    public Receiver missing() { return new Receiver(id, owner, dimension, pos, face, block, name, paid, true, slots); }
    public Receiver withSlot(int slot, boolean enabled) {
        var next = new ArrayList<>(slots); next.remove(Integer.valueOf(slot));
        if (enabled) next.add(slot);
        return new Receiver(id, owner, dimension, pos, face, block, name, paid, lost, next);
    }
}
