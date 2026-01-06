package net.borisshoes.borislib.datastorage;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
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

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

public class DefaultPlayerData {
   
   public static final Codec<DefaultPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
         CodecUtils.UUID_CODEC.fieldOf("playerID").forGetter(DefaultPlayerData::getPlayerID),
         Codec.STRING.fieldOf("username").orElse("").forGetter(DefaultPlayerData::getUsername),
         CodecUtils.STRING_LIST.fieldOf("knownUsernames").orElse(new ArrayList<>()).forGetter(DefaultPlayerData::getKnownUsernames),
         ResolvableProfile.CODEC.optionalFieldOf("resolvableProfile").forGetter(data -> Optional.ofNullable(data.resProf))
   ).apply(instance, (playerID, username, knownUsernames, resProf) -> {
      DefaultPlayerData data = new DefaultPlayerData(playerID);
      data.username = username;
      data.knownUsernames.clear();
      data.knownUsernames.addAll(knownUsernames);
      data.resProf = resProf.orElse(null);
      return data;
   }));
   
   private final UUID playerID;
   private final ArrayList<String> knownUsernames = new ArrayList<>();
   private String username = "";
   private ResolvableProfile resProf;
   
   public DefaultPlayerData(UUID playerID){
      this.playerID = playerID;
   }
   
   public void onLogin(ServerPlayer player){
      GameProfile profile = player.getGameProfile();
      this.resProf = ResolvableProfile.createResolved(profile);
      this.username = profile.name();
      if(!this.knownUsernames.contains(this.username)){
         this.knownUsernames.add(this.username);
      }
   }
   
   public void tryResolve(MinecraftServer server){
      ProfileResolver profileResolver = server.services().profileResolver();
      Util.nonCriticalIoPool().execute(() -> {
         Optional<GameProfile> optional = profileResolver.fetchById(playerID);
         server.execute(() -> optional.ifPresentOrElse(
               gameProfile -> {
                  this.resProf = ResolvableProfile.createResolved(gameProfile);
                  this.username = gameProfile.name();
                  if(!this.knownUsernames.contains(this.username)){
                     this.knownUsernames.add(this.username);
                  }
                  }, () -> BorisLib.LOGGER.warn("Failed to resolve profile data for player {}", playerID)
               )
         );
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
