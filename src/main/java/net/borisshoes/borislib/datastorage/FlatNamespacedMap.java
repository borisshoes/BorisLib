package net.borisshoes.borislib.datastorage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.util.Map;

/**
 * Codec utilities for the two-level {@code mod id → key → NBT compound} layout used by
 * {@link GlobalState}, {@link WorldState} and {@link PlayerObjectStore}.
 */
public final class FlatNamespacedMap {
   /** Codec that round-trips an arbitrary {@link CompoundTag} via the Mojang DFU pass-through codec. */
   public static final Codec<CompoundTag> NBT = Codec.PASSTHROUGH.comapFlatMap(d -> {
      var v = d.convert(NbtOps.INSTANCE).getValue();
      return v instanceof CompoundTag c ? DataResult.success(c) : DataResult.error(() -> "Expected NbtCompound");
   }, c -> new Dynamic<>(NbtOps.INSTANCE, c));
   
   /** Codec for the nested {@code String → String → CompoundTag} map used to store all mod data. */
   public static final Codec<java.util.Map<String, Map<String, CompoundTag>>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, NBT));
}