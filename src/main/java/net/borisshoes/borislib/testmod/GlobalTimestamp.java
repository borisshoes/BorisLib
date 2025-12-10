package net.borisshoes.borislib.testmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class GlobalTimestamp {
   public long timestamp = System.currentTimeMillis();
   public static final Codec<GlobalTimestamp> CODEC = RecordCodecBuilder.create(i -> i.group(
         Codec.LONG.fieldOf("timestamp").forGetter(o -> o.timestamp)
   ).apply(i, (time) -> {
      GlobalTimestamp t = new GlobalTimestamp();
      t.timestamp = time;
      return t;
   }));
   public static final DataKey<GlobalTimestamp> KEY = DataRegistry.register(DataKey.ofGlobal(Identifier.of(MOD_ID, "timestamp"),CODEC, GlobalTimestamp::new));
}
