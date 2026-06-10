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

/**
 * Base class for a piece of work that should run the next time a specific player logs in.
 *
 * <p>Login callbacks are persisted per-player via {@link LoginCallbackContainer} (which is itself stored
 * via the BorisLib data-storage system), so they survive server restarts and apply to players who are
 * offline at the moment the work is queued. When the matching player joins,
 * {@link PlayerConnectionCallback#onPlayerJoin} drains every callback assigned to that player's UUID,
 * fires {@link #onLogin(ServerGamePacketListenerImpl, MinecraftServer)} for each, and removes the served
 * callback from the container.</p>
 *
 * <p>Subclasses must:</p>
 * <ul>
 *    <li>Pass a unique {@link Identifier} to {@link #LoginCallback(Identifier)} and register a factory
 *        for it via {@link BorisLib#registerCallback(Identifier, java.util.function.Supplier)}.</li>
 *    <li>Implement {@link #getData()} / {@link #setData(CompoundTag)} for persistence.</li>
 *    <li>Implement {@link #canCombine(LoginCallback)} / {@link #combineCallbacks(LoginCallback)} to merge
 *        with a newly added callback of the same type for the same player (returning {@code false} keeps
 *        them as distinct entries).</li>
 *    <li>Implement {@link #makeNew()} so the codec can construct fresh empty instances during decoding.</li>
 * </ul>
 */
public abstract class LoginCallback {
   /**
    * Codec that serializes {@link #id}, the target player's UUID string, and the subclass-defined NBT
    * payload returned by {@link #getData()}. On decode the codec looks up a freshly-built instance via
    * {@link BorisLib#createCallback(Identifier)} and re-hydrates it via {@link #setData(CompoundTag)}.
    */
   public static final Codec<LoginCallback> LOGIN_CALLBACK_CODEC = new Codec<>() {
      @Override
      public <T> DataResult<T> encode(LoginCallback input, DynamicOps<T> ops, T prefix){
         Identifier id = input.getId();
         String uuid = input.getPlayer() == null ? "" : input.getPlayer();
         CompoundTag data = input.getData() == null ? new CompoundTag() : input.getData();
         var mb = ops.mapBuilder();
         mb.add("id", ops.createString(id.toString()));
         mb.add("uuid", ops.createString(uuid));
         mb.add("data", CompoundTag.CODEC.encodeStart(ops, data).result().orElseGet(ops::empty));
         return mb.build(prefix);
      }
      
      @Override
      public <T> DataResult<Pair<LoginCallback, T>> decode(DynamicOps<T> ops, T input){
         return ops.getMap(input).flatMap(map -> {
            T idEl = map.get("id");
            T uuidEl = map.get("uuid");
            T dataEl = map.get("data");
            if(idEl == null || dataEl == null) return DataResult.error(() -> "Missing id or data");
            return ops.getStringValue(idEl).flatMap(idStr -> {
               final Identifier id;
               try{
                  id = Identifier.parse(idStr);
               }catch(Exception e){
                  return DataResult.error(() -> "Bad id: " + idStr);
               }
               LoginCallback cb = BorisLib.createCallback(id);
               if(cb == null) return DataResult.error(() -> "Unregistered callback id: " + id);
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
   
   /** Stringified UUID of the target player. Set on construction or when re-hydrating from storage. */
   protected String playerUUID;
   /** Registered identifier of this callback's type, used by the codec to look up a factory. */
   protected final Identifier id;
   /** Subclass-defined NBT payload set by {@link #setData(CompoundTag)} during decode. */
   protected CompoundTag data;
   
   /**
    * @param id unique namespaced identifier of the callback type; must match an id previously registered
    *           via {@link BorisLib#registerCallback(Identifier, java.util.function.Supplier)}
    */
   protected LoginCallback(Identifier id){
      this.id = id;
   }
   
   /**
    * Invoked once on the next login of the player whose UUID matches {@link #getPlayer()}.
    *
    * @param netHandler the player's connection handler (use {@code netHandler.player} for the
    *                   {@link net.minecraft.server.level.ServerPlayer})
    * @param server     the server instance
    */
   public abstract void onLogin(ServerGamePacketListenerImpl netHandler, MinecraftServer server);
   
   /**
    * Re-hydrates the subclass payload from an NBT compound. Called by the codec during decode after
    * {@link #makeNew()}.
    *
    * @param data the encoded payload previously returned by {@link #getData()}
    */
   public abstract void setData(CompoundTag data);
   
   /**
    * Encodes the subclass payload to an NBT compound for persistence via the codec.
    *
    * @return a {@link CompoundTag} representing this callback's current state
    */
   public abstract CompoundTag getData();
   
   /**
    * @param callback another callback of the same id queued for the same player
    * @return {@code true} if {@link #combineCallbacks(LoginCallback)} could meaningfully merge the two
    */
   public abstract boolean canCombine(LoginCallback callback);
   
   /**
    * Attempts to merge the state of {@code callback} into this one, replacing the need to keep both in the
    * container. Only invoked when {@link #canCombine(LoginCallback)} returned {@code true}.
    *
    * @param callback the incoming callback to absorb
    * @return {@code true} if the merge succeeded (the incoming callback is then dropped)
    */
   public abstract boolean combineCallbacks(LoginCallback callback);
   
   /**
    * Factory hook used by the codec to construct a fresh, empty instance before {@link #setData(CompoundTag)}
    * is called. Must return a new object of the same concrete subclass.
    *
    * @return a new blank instance of this callback type
    */
   public abstract LoginCallback makeNew();
   
   /**
    * Sets the target player's stringified UUID. Normally invoked when the callback is constructed for a
    * specific player or when decoding from storage.
    *
    * @param playerUUID the player's UUID, as produced by {@link ServerPlayer#getStringUUID()}
    */
   public void setPlayer(String playerUUID){
      this.playerUUID = playerUUID;
   }
   
   /** @return the registered identifier for this callback type. */
   public Identifier getId(){
      return id;
   }
   
   /** @return the stringified UUID of the player this callback is bound to. */
   public String getPlayer(){
      return playerUUID;
   }
}
