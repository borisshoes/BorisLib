package net.borisshoes.borislib.callbacks;

import net.borisshoes.borislib.BorisLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

public class ItemReturnLoginCallback extends LoginCallback{
   
   private ItemStack item;
   private int prefSlot;
   
   public ItemReturnLoginCallback(){
      super(Identifier.fromNamespaceAndPath(MOD_ID,"item_return"));
   }
   
   public ItemReturnLoginCallback(ServerPlayer player, ItemStack item, int prefSlot){
      this();
      this.playerUUID = player.getStringUUID();
      this.item = item;
      this.prefSlot = prefSlot;
   }
   
   @Override
   public void onLogin(ServerGamePacketListenerImpl netHandler, MinecraftServer server){
      // Double check that this is the correct player before running timer
      ServerPlayer player = netHandler.player;
      if(player.getStringUUID().equals(playerUUID)){
         if(!player.isAlive() || !player.getInventory().add(item)){
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item,player,1,prefSlot));
         }
      }
   }
   
   @Override
   public void setData(CompoundTag data){
      this.data = data;
      this.item = ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE,BorisLib.SERVER.registryAccess()),data.getCompoundOrEmpty("item")).result().orElse(ItemStack.EMPTY);
   }
   
   @Override
   public CompoundTag getData(){
      CompoundTag data = new CompoundTag();
      if(!this.item.isEmpty()) data.put("item", ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE,BorisLib.SERVER.registryAccess()),this.item).getOrThrow());
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