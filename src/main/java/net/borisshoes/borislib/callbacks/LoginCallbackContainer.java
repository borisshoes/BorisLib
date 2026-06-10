package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player container of pending {@link LoginCallback}s.
 *
 * <p>Persisted as {@link StorableData} under the {@link BorisLib#LOGIN_CALLBACKS_KEY} player-data key.
 * When a player joins, {@link PlayerConnectionCallback#onPlayerJoin} pulls this container, fires each
 * callback bound to that player's UUID, and removes them. Adding a callback that {@linkplain
 * LoginCallback#canCombine can combine} with an existing one for the same player and id is merged in
 * place via {@link LoginCallback#combineCallbacks(LoginCallback)} rather than appended.</p>
 */
public class LoginCallbackContainer implements StorableData {
   
   /** Backing list of queued callbacks for {@link #playerID}. */
   public final List<LoginCallback> callbacks = new ArrayList<>();
   /** The owner player's UUID. */
   public final UUID playerID;
   
   /**
    * @param uuid the player this container belongs to
    */
   public LoginCallbackContainer(UUID uuid){
      this.playerID = uuid;
   }
   
   /**
    * Re-hydrates the callback list from a saved {@code callbacks} ListTag.
    *
    * @param view the value view to read from
    */
   @Override
   public void read(ValueInput view){
      this.callbacks.clear();
      for(LoginCallback callback : view.listOrEmpty("callbacks", LoginCallback.LOGIN_CALLBACK_CODEC)){
         this.callbacks.add(callback);
      }
   }
   
   /**
    * Serializes the container to the supplied tag: writes {@code uuid} as a string and {@code callbacks}
    * as a list-tag of encoded callback entries.
    *
    * @param tag the destination compound tag
    */
   @Override
   public void writeNbt(CompoundTag tag){
      tag.putString("uuid", playerID.toString());
      
      ListTag callbackList = new ListTag();
      for(LoginCallback callback : callbacks){
         LoginCallback.LOGIN_CALLBACK_CODEC.encodeStart(NbtOps.INSTANCE, callback).result().ifPresent(callbackList::add);
      }
      tag.put("callbacks", callbackList);
   }
   
   /** @return a live reference to the underlying list of callbacks (mutations affect the container). */
   public List<LoginCallback> getCallbacks(){
      return callbacks;
   }
   
   /**
    * Queues a callback for the player.
    *
    * <p>If an existing callback of the same {@link LoginCallback#getId() id} for the same target player
    * is present and {@linkplain LoginCallback#canCombine accepts} this one, it absorbs the new callback
    * via {@link LoginCallback#combineCallbacks(LoginCallback)} instead of appending it. Otherwise the
    * callback is added to the list.</p>
    *
    * @param callback the callback to add
    * @return {@code true} if the callback was newly added or combined into an existing entry
    */
   public boolean addCallback(LoginCallback callback){
      if(callbacks.contains(callback)) return false;
      for(LoginCallback loginCallback : callbacks){
         if(callback.getId().equals(loginCallback.getId()) && callback.getPlayer().equals(loginCallback.getPlayer())){
            if(loginCallback.canCombine(callback) && loginCallback.combineCallbacks(callback)){
               return true;
            }
         }
      }
      return callbacks.add(callback);
   }
   
   /**
    * Removes a previously queued callback.
    *
    * @param callback the callback instance to remove
    * @return {@code true} if the callback was present and removed
    */
   public boolean removeCallback(LoginCallback callback){
      if(!callbacks.contains(callback)) return false;
      return callbacks.remove(callback);
   }
}
