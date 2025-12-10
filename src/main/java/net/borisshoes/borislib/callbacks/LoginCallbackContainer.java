package net.borisshoes.borislib.callbacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.LOGGER;

public class LoginCallbackContainer {
   public static final Codec<LoginCallbackContainer> CODEC = RecordCodecBuilder.create(i -> i.group(
         LoginCallback.LOGIN_CALLBACK_CODEC.listOf().optionalFieldOf("callbacks", List.of()).forGetter(LoginCallbackContainer::getCallbacks),
         CodecUtils.UUID_CODEC.fieldOf("uuid").forGetter(c -> c.playerID)
   ).apply(i, (list, uuid) -> {
      LoginCallbackContainer c = new LoginCallbackContainer(uuid);
      c.callbacks.addAll(list);
      return c;
   }));
   
   public final List<LoginCallback> callbacks = new ArrayList<>();
   public final UUID playerID;
   
   public LoginCallbackContainer(UUID uuid){
      this.playerID = uuid;
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
