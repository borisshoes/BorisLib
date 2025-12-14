package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.util.Map;

public final class FlatNamespacedMap {
   public static final Codec<CompoundTag> NBT = Codec.PASSTHROUGH.comapFlatMap(d -> {
      var v = d.convert(NbtOps.INSTANCE).getValue();
      return v instanceof CompoundTag c ? DataResult.success(c) : DataResult.error(() -> "Expected NbtCompound");
   }, c -> new Dynamic<>(NbtOps.INSTANCE, c));
   
   public static final Codec<java.util.Map<String, Map<String, CompoundTag>>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, NBT));
}