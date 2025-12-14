package net.borisshoes.borislib.testmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public final class WorldMarker {
   public String id;
   public BlockPos pos;
   public static final Codec<BlockPos> POS = Codec.INT.listOf().xmap(list -> new BlockPos(list.get(0), list.get(1), list.get(2)), p -> java.util.List.of(p.getX(), p.getY(), p.getZ()));
   public static final Codec<WorldMarker> CODEC = RecordCodecBuilder.create(i -> i.group(
         Codec.STRING.fieldOf("id").forGetter(o -> o.id),
         POS.fieldOf("pos").forGetter(o -> o.pos)
   ).apply(i, (id, pos) -> {
      WorldMarker m = new WorldMarker();
      m.id = id;
      m.pos = pos;
      return m;
   }));
   public static final Codec<List<WorldMarker>> LIST = RecordCodecBuilder.create(instance ->
         instance.group(
               CODEC.listOf().fieldOf("markers").forGetter(list -> list)
         ).apply(instance, markers -> markers)
   );
   public static final DataKey<List<WorldMarker>> KEY = DataRegistry.register(DataKey.ofWorld(Identifier.fromNamespaceAndPath(MOD_ID, "markers"),LIST,(key) -> new ArrayList<>()));
}