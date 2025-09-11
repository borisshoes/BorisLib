package net.borisshoes.borislib.callbacks;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public abstract class LoginCallback {
   protected String playerUUID;
   protected final Identifier id;
   protected NbtCompound data;
   protected ServerWorld world;
   
   protected LoginCallback(Identifier id){
      this.id = id;
   }
   
   public abstract void onLogin(ServerPlayNetworkHandler netHandler, MinecraftServer server);
   
   public abstract void setData(NbtCompound data);
   
   public abstract NbtCompound getData();
   
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
   
   public ServerWorld getWorld(){
      return world;
   }
}
