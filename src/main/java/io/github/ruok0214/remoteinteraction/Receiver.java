package io.github.ruok0214.remoteinteraction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record Receiver(UUID id, UUID owner, Identifier dimension, BlockPos pos, Direction face,
                       Identifier block, String name, boolean paid, boolean lost) {
    public static final int MAX_NAME = 32;
    public static final Codec<Receiver> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Receiver::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Receiver::owner),
            Identifier.CODEC.fieldOf("dimension").forGetter(Receiver::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Receiver::pos),
            Direction.CODEC.fieldOf("face").forGetter(Receiver::face),
            Identifier.CODEC.fieldOf("block").forGetter(Receiver::block),
            Codec.STRING.fieldOf("name").forGetter(Receiver::name),
            Codec.BOOL.fieldOf("paid").forGetter(Receiver::paid),
            Codec.BOOL.optionalFieldOf("lost", false).forGetter(Receiver::lost)
    ).apply(i, Receiver::new));

    public Receiver renamed(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new Receiver(id, owner, dimension, pos, face, block, clean, paid, lost);
    }
    public Receiver missing() { return new Receiver(id, owner, dimension, pos, face, block, name, paid, true); }
}
