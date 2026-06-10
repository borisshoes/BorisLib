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

import java.util.*;

/**
 * Per-player {@link StorableData} holding the default profile information that BorisLib tracks for every
 * known player.
 *
 * <p>Maintained by BorisLib via the {@link net.borisshoes.borislib.BorisLib#PLAYER_DATA_KEY PLAYER_DATA_KEY}
 * (a PLAYER-scoped {@link DataKey}). On login, {@link #onLogin(ServerPlayer)} records the player's
 * current username (appending it to {@link #getKnownUsernames()} as an alias history) and caches a
 * {@link ResolvableProfile} that can later be turned into a player head, mannequin, or text component
 * even when the player is offline.</p>
 *
 * <p>Use {@link #tryResolve(MinecraftServer)} to populate the cached profile from Mojang services on
 * demand (e.g. when looking up a player that has never logged into this server).</p>
 */
public class DefaultPlayerData implements StorableData {
   
   private final UUID playerID;
   private final ArrayList<String> knownUsernames = new ArrayList<>();
   private String username = "";
   private ResolvableProfile resProf;
   
   /**
    * @param playerID the UUID of the player this data describes
    */
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
   
   /**
    * Updates this entry from an authenticated {@link ServerPlayer}. Caches the player's profile and
    * appends the current username to {@link #getKnownUsernames()} if it is new.
    *
    * @param player the player who just logged in
    */
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
   
   /**
    * Asynchronously resolves the player's Mojang profile via {@link MinecraftServer#services()} and
    * updates the cached {@link ResolvableProfile} / username on success.
    *
    * @param server the server providing the {@link ProfileResolver}
    */
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
   
   /**
    * Spawns a {@link Mannequin} entity textured with this player's cached profile (if any).
    *
    * @param level the level to spawn the mannequin into
    * @return the new mannequin (or {@code null} if creation failed)
    */
   public Mannequin createMannequin(ServerLevel level){
      Mannequin mannequin = Mannequin.create(EntityType.MANNEQUIN, level);
      if(mannequin != null && resProf != null){
         mannequin.setComponent(DataComponents.PROFILE, this.resProf);
      }
      return mannequin;
   }
   
   /** @return a {@link ItemStack} of {@code minecraft:player_head} textured with this player's profile. */
   public ItemStack getPlayerHeadItem(){
      ItemStack head = new ItemStack(Items.PLAYER_HEAD);
      if(resProf != null){
         head.set(DataComponents.PROFILE, resProf);
      }
      return head;
   }
   
   /**
    * @return a text {@link Component} containing this player's face sprite (using cached profile);
    *         empty if no profile has been resolved yet
    */
   public Component getFaceTextComponent(){
      if(resProf == null) return Component.empty();
      return Component.object(new PlayerSprite(resProf, true));
   }
   
   /** @return this entry's player UUID. */
   public UUID getPlayerID(){
      return playerID;
   }
   
   /** @return the most recently known username for this player, or an empty string. */
   public String getUsername(){
      return username;
   }
   
   /** @return historical list of usernames this player has used while connecting to this server. */
   public ArrayList<String> getKnownUsernames(){
      return knownUsernames;
   }
   
   /** @return the cached {@link ResolvableProfile} (skin / signature / name), or {@code null} if never resolved. */
   public ResolvableProfile getResolvableProfile(){
      return resProf;
   }
}
