package net.borisshoes.borislib.cca;

import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.callbacks.LoginCallback;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

import static net.borisshoes.borislib.BorisLib.LOGGER;

public class LoginCallbackComponent implements ILoginCallbackComponent{
   public final List<LoginCallback> callbacks = new ArrayList<>();
   
   @Override
   public List<LoginCallback> getCallbacks(){
      return callbacks;
   }
   
   @Override
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
   
   @Override
   public boolean removeCallback(LoginCallback callback){
      if(!callbacks.contains(callback)) return false;
      return callbacks.remove(callback);
   }
   
   @Override
   public void readData(ReadView readView){
      try{
         callbacks.clear();
         List<NbtCompound> callbackTags = readView.read("Callbacks", CodecUtils.COMPOUND_LIST).orElse(new ArrayList<>());
         for (NbtCompound callbackTag : callbackTags){
            String playerUUID = callbackTag.getString("uuid", "");
            Identifier callbackId = Identifier.tryParse(callbackTag.getString("id", ""));
            if(callbackId == null) continue;
            LoginCallback callback = BorisLib.createCallback(callbackId);
            if(callback == null) continue;
            callback.setData(callbackTag.getCompoundOrEmpty("data"));
            callback.setPlayer(playerUUID);
            callbacks.add(callback);
         }
      }catch(Exception e){
         LOGGER.error(e);
      }
   }
   
   @Override
   public void writeData(WriteView writeView){
      try{
         ArrayList<NbtCompound> callbackTags = new ArrayList<>();
         for(LoginCallback callback : callbacks){
            NbtCompound callbackTag = new NbtCompound();
            NbtCompound dataTag = callback.getData();
            callbackTag.putString("uuid",callback.getPlayer());
            callbackTag.putString("id",callback.getId().toString());
            callbackTag.put("data",dataTag);
            callbackTags.add(callbackTag);
         }
         writeView.put("Callbacks", CodecUtils.COMPOUND_LIST,callbackTags);
      }catch(Exception e){
         LOGGER.error(e);
      }
   }
}
