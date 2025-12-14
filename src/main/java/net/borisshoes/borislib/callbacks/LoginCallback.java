package net.borisshoes.borislib.callbacks;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

public abstract class LoginCallback {
   public static final Codec<LoginCallback> LOGIN_CALLBACK_CODEC = new Codec<>() {
      @Override public <T> DataResult<T> encode(LoginCallback input, DynamicOps<T> ops, T prefix) {
         Identifier id = input.getId();
         String uuid = input.getPlayer() == null ? "" : input.getPlayer();
         CompoundTag data = input.getData() == null ? new CompoundTag() : input.getData();
         var mb = ops.mapBuilder();
         mb.add("id", ops.createString(id.toString()));
         mb.add("uuid", ops.createString(uuid));
         mb.add("data", CompoundTag.CODEC.encodeStart(ops, data).result().orElseGet(ops::empty));
         return mb.build(prefix);
      }
      @Override public <T> DataResult<Pair<LoginCallback, T>> decode(DynamicOps<T> ops, T input) {
         return ops.getMap(input).flatMap(map -> {
            T idEl = map.get("id");
            T uuidEl = map.get("uuid");
            T dataEl = map.get("data");
            if (idEl == null || dataEl == null) return DataResult.error(() -> "Missing id or data");
            return ops.getStringValue(idEl).flatMap(idStr -> {
               final Identifier id;
               try { id = Identifier.parse(idStr); } catch (Exception e) { return DataResult.error(() -> "Bad id: " + idStr); }
               LoginCallback cb = BorisLib.createCallback(id);
               if (cb == null) return DataResult.error(() -> "Unregistered callback id: " + id);
               String uuid = uuidEl == null ? "" : ops.getStringValue(uuidEl).result().orElse("");
               return CompoundTag.CODEC.decode(ops, dataEl).map(p -> {
                  cb.setPlayer(uuid);
                  cb.setData(p.getFirst());
                  return Pair.of(cb, input);
               });
            });
         });
      }
   };
   
   protected String playerUUID;
   protected final Identifier id;
   protected CompoundTag data;
   
   protected LoginCallback(Identifier id){
      this.id = id;
   }
   
   public abstract void onLogin(ServerGamePacketListenerImpl netHandler, MinecraftServer server);
   
   public abstract void setData(CompoundTag data);
   
   public abstract CompoundTag getData();
   
   public abstract boolean canCombine(LoginCallback callback);
   
   public abstract boolean combineCallbacks(LoginCallback callback);
   
   public abstract LoginCallback makeNew();
   
   public void setPlayer(String playerUUID){ this.playerUUID = playerUUID;}
   
   public Identifier getId(){
      return id;
   }
   
   public String getPlayer(){
      return playerUUID;
   }
}
