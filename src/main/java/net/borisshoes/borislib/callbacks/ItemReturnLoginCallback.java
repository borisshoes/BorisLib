package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class ItemReturnLoginCallback extends LoginCallback{
   
   private ItemStack item;
   private int prefSlot;
   
   public ItemReturnLoginCallback(){
      super(Identifier.of(MOD_ID,"item_return"));
   }
   
   public ItemReturnLoginCallback(ServerPlayerEntity player, ItemStack item, int prefSlot){
      this();
      this.playerUUID = player.getUuidAsString();
      this.item = item;
      this.prefSlot = prefSlot;
   }
   
   @Override
   public void onLogin(ServerPlayNetworkHandler netHandler, MinecraftServer server){
      // Double check that this is the correct player before running timer
      ServerPlayerEntity player = netHandler.player;
      if(player.getUuidAsString().equals(playerUUID)){
         if(!player.isAlive() || !player.getInventory().insertStack(item)){
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player,1,prefSlot));
         }
      }
   }
   
   @Override
   public void setData(NbtCompound data){
      this.data = data;
      this.item = ItemStack.CODEC.parse(RegistryOps.of(NbtOps.INSTANCE,BorisLib.SERVER.getRegistryManager()),data.getCompoundOrEmpty("item")).result().orElse(ItemStack.EMPTY);
   }
   
   @Override
   public NbtCompound getData(){
      NbtCompound data = new NbtCompound();
      if(!this.item.isEmpty()) data.put("item",ItemStack.CODEC.encodeStart(RegistryOps.of(NbtOps.INSTANCE,BorisLib.SERVER.getRegistryManager()),this.item).getOrThrow());
      this.data = data;
      return this.data;
   }
   
   @Override
   public boolean combineCallbacks(LoginCallback callback){
      return false;
   }
   
   @Override
   public boolean canCombine(LoginCallback callback){
      return false;
   }
   
   @Override
   public LoginCallback makeNew(){
      return new ItemReturnLoginCallback();
   }
}