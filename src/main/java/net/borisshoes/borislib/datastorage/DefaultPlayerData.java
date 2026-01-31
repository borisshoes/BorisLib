package net.borisshoes.borislib.datastorage;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.objects.PlayerSprite;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public class DefaultPlayerData implements StorableData {
   
   private final UUID playerID;
   private final ArrayList<String> knownUsernames = new ArrayList<>();
   private String username = "";
   private ResolvableProfile resProf;
   
   public DefaultPlayerData(UUID playerID){
      this.playerID = playerID;
   }
   
   @Override
   public void read(ValueInput view){
      this.username = view.getString("username").orElse("");
      
      for(String s : view.listOrEmpty("knownUsernames", Codec.STRING)){
         if(!s.isEmpty() && !this.knownUsernames.contains(s)){
            this.knownUsernames.add(s);
         }
      }
      
      for(ResolvableProfile prof : view.listOrEmpty("resolvableProfile_list", ResolvableProfile.CODEC)){
         this.resProf = prof;
         break;
      }
   }
   
   @Override
   public void writeNbt(CompoundTag tag){
      tag.putString("playerID", playerID.toString());
      tag.putString("username", username != null ? username : "");
      ListTag usernameList = new ListTag();
      for(String name : knownUsernames){
         if(name != null && !name.isEmpty()){
            usernameList.add(StringTag.valueOf(name));
         }
      }
      tag.put("knownUsernames", usernameList);
      if(resProf != null){
         ListTag profList = new ListTag();
         ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, resProf).result().ifPresent(profList::add);
         if(!profList.isEmpty()){
            tag.put("resolvableProfile_list", profList);
         }
      }
   }
   
   public void onLogin(ServerPlayer player){
      try{
         GameProfile profile = player.getGameProfile();
         this.resProf = ResolvableProfile.createResolved(profile);
         this.username = profile.name();
         if(!this.knownUsernames.contains(this.username)){
            this.knownUsernames.add(this.username);
         }
      }catch(Exception e){
         BorisLib.LOGGER.error("Failed to process login data for player {}: {}", playerID, e.getMessage());
      }
   }
   
   public void tryResolve(MinecraftServer server){
      ProfileResolver profileResolver = server.services().profileResolver();
      Util.nonCriticalIoPool().execute(() -> {
         try{
            Optional<GameProfile> optional = profileResolver.fetchById(playerID);
            server.execute(() -> optional.ifPresentOrElse(
                  gameProfile -> {
                     this.resProf = ResolvableProfile.createResolved(gameProfile);
                     this.username = gameProfile.name();
                     if(!this.knownUsernames.contains(this.username)){
                        this.knownUsernames.add(this.username);
                     }
                  }, () -> BorisLib.LOGGER.warn("Failed to resolve profile data for player {}", playerID)
            ));
         }catch(Exception e){
            BorisLib.LOGGER.warn("Exception while resolving profile for player {}: {}", playerID, e.getMessage());
         }
      });
   }
   
   public Mannequin createMannequin(ServerLevel level){
      Mannequin mannequin = Mannequin.create(EntityType.MANNEQUIN, level);
      if(mannequin != null && resProf != null){
         mannequin.setComponent(DataComponents.PROFILE, this.resProf);
      }
      return mannequin;
   }
   
   public ItemStack getPlayerHeadItem(){
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      if(resProf != null){
         head.set(DataComponents.PROFILE, resProf);
      }
      return head;
   }
   
   public Component getFaceTextComponent(){
      if(resProf == null) return Component.empty();
      return Component.object(new PlayerSprite(resProf, true));
   }
   
   public UUID getPlayerID(){
      return playerID;
   }
   
   public String getUsername(){
      return username;
   }
   
   public ArrayList<String> getKnownUsernames(){
      return knownUsernames;
   }
   
   public ResolvableProfile getResolvableProfile(){
      return resProf;
   }
}
