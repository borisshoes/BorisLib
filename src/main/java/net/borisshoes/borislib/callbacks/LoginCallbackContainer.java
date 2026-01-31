package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.datastorage.StorableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoginCallbackContainer implements StorableData {
   
   public final List<LoginCallback> callbacks = new ArrayList<>();
   public final UUID playerID;
   
   public LoginCallbackContainer(UUID uuid){
      this.playerID = uuid;
   }
   
   @Override
   public void read(ValueInput view){
      this.callbacks.clear();
      for(LoginCallback callback : view.listOrEmpty("callbacks", LoginCallback.LOGIN_CALLBACK_CODEC)){
         this.callbacks.add(callback);
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      tag.putString("uuid", playerID.toString());
      
      ListTag callbackList = new ListTag();
      for(LoginCallback callback : callbacks){
         LoginCallback.LOGIN_CALLBACK_CODEC.encodeStart(NbtOps.INSTANCE, callback).result().ifPresent(callbackList::add);
      }
      tag.put("callbacks", callbackList);
   }
   
   public List<LoginCallback> getCallbacks(){
      return callbacks;
   }
   
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
   
   public boolean removeCallback(LoginCallback callback){
      if(!callbacks.contains(callback)) return false;
      return callbacks.remove(callback);
   }
}
