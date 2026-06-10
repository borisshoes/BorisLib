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

/**
 * {@link LoginCallback} that hands an {@link ItemStack} back to a player the next time they log in.
 *
 * <p>When the player joins, the stack is added to their inventory in the preferred slot if possible. If
 * the inventory is full or the player is in a state that prevents pickup, an
 * {@link ItemReturnTimerCallback} is scheduled to retry shortly. If the player never logs in, the
 * callback simply sits in their {@link LoginCallbackContainer} indefinitely.</p>
 *
 * <p>Typically queued via {@link BorisLib#addLoginCallback(LoginCallback)}.</p>
 */
public class ItemReturnLoginCallback extends LoginCallback {
   
   private ItemStack item;
   private int prefSlot;
   
   /** Default constructor used by the codec's {@link #makeNew()} factory; populates only the type id. */
   public ItemReturnLoginCallback(){
      super(Identifier.fromNamespaceAndPath(MOD_ID, "item_return"));
   }
   
   /**
    * @param player   the player who should receive the item on next login
    * @param item     the stack to return
    * @param prefSlot inventory slot index to try first; {@code -1} disables the preference
    */
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
            BorisLib.addTickTimerCallback(new ItemReturnTimerCallback(item, player, 1, prefSlot));
         }
      }
   }
   
   @Override
   public void setData(CompoundTag data){
      this.data = data;
      this.item = ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, BorisLib.SERVER.registryAccess()), data.getCompoundOrEmpty("item")).result().orElse(ItemStack.EMPTY);
   }
   
   @Override
   public CompoundTag getData(){
      CompoundTag data = new CompoundTag();
      if(!this.item.isEmpty())
         data.put("item", ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, BorisLib.SERVER.registryAccess()), this.item).getOrThrow());
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