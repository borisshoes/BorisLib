package net.borisshoes.borislib.callbacks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.datastorage.DataAccess;
import net.borisshoes.borislib.datastorage.DataKey;
import net.borisshoes.borislib.datastorage.DataRegistry;
import net.borisshoes.borislib.datastorage.StorableData;
import net.borisshoes.borislib.mixins.InventoryAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.borisshoes.borislib.BorisLib.MOD_ID;

/**
 * Per-player {@link StorableData} queue of items that are owed to a player but could not be handed over yet.
 *
 * <p>This is the persisted backbone of {@link ItemReturnLoginCallback} and {@link ItemReturnTimerCallback}: an
 * owed item lives here, in the player's data file, until it is actually inside their inventory, so a restart or
 * crash only ever delays delivery. Online, living players are retried once a second; if their inventory stays
 * full for {@link #DROP_AFTER_ATTEMPTS} attempts, the rest is dropped at their feet for them to pick up.</p>
 *
 * <p>Use {@link #queue(UUID, ItemStack, int)} directly when an item must survive a restart even before its first
 * delivery attempt. The player does not need to be online.</p>
 */
public class PendingItemReturns implements StorableData {

   /** Server ticks between delivery attempts. */
   public static final int RETRY_INTERVAL = 20;
   /** Failed attempts on a full inventory before the remaining items are dropped at the player's feet. */
   public static final int DROP_AFTER_ATTEMPTS = 30;

   /** Registered PLAYER key holding each player's queue. */
   public static final DataKey<PendingItemReturns> KEY = DataRegistry.register(DataKey.ofPlayer(Identifier.fromNamespaceAndPath(MOD_ID, "pending_items"), PendingItemReturns::new));

   private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
         ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
         Codec.INT.fieldOf("slot").forGetter(Entry::prefSlot)
   ).apply(i, Entry::new));

   // Players known to have something queued, so the retry loop never has to look at everyone online
   private static final Set<UUID> ACTIVE = new HashSet<>();
   private static final Map<UUID, Integer> FAILED_ATTEMPTS = new HashMap<>();

   private final UUID playerID;
   private final ArrayList<Entry> entries = new ArrayList<>();

   /**
    * @param playerID the UUID of the player the items are owed to
    */
   public PendingItemReturns(UUID playerID){
      this.playerID = playerID;
   }

   @Override
   public void read(ValueInput view){
      entries.clear();
      for(Entry entry : view.listOrEmpty("items", ENTRY_CODEC)){
         if(!entry.item().isEmpty()) entries.add(entry);
      }
   }

   @Override
   public void writeNbt(CompoundTag tag){
      DynamicOps<Tag> ops = BorisLib.SERVER != null ? BorisLib.SERVER.registryAccess().createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
      ListTag list = new ListTag();
      for(Entry entry : entries){
         if(entry.item().isEmpty()) continue;
         ENTRY_CODEC.encodeStart(ops, entry)
               .resultOrPartial(error -> BorisLib.LOGGER.error("Failed to encode pending item {} for player {}: {}", entry.item(), playerID, error))
               .ifPresent(list::add);
      }
      tag.putString("uuid", playerID.toString());
      tag.put("items", list);
   }

   /** @return {@code true} if nothing is owed to this player. */
   public boolean isEmpty(){
      return entries.isEmpty();
   }

   /** @return copies of every item currently owed to this player. */
   public List<ItemStack> getItems(){
      List<ItemStack> items = new ArrayList<>();
      for(Entry entry : entries){
         items.add(entry.item().copy());
      }
      return items;
   }

   /**
    * Owes an item to a player and tries to hand it over straight away if they are online. The stack is copied and
    * split into stacks no larger than its max stack size, so it always fits the item codec.
    *
    * @param playerId the receiving player (online or offline)
    * @param item     the stack to return; not modified
    * @param prefSlot inventory slot to try first, or {@code -1} for none
    */
   public static void queue(UUID playerId, ItemStack item, int prefSlot){
      if(item == null || item.isEmpty()) return;
      PendingItemReturns pending = DataAccess.getPlayer(playerId, KEY);
      ItemStack remaining = item.copy();
      int maxCount = Math.max(1, remaining.getMaxStackSize());
      while(!remaining.isEmpty()){
         pending.entries.add(new Entry(remaining.split(maxCount), prefSlot));
      }
      DataAccess.markPlayerDirty(playerId);
      ACTIVE.add(playerId);

      ServerPlayer player = BorisLib.SERVER == null ? null : BorisLib.SERVER.getPlayerList().getPlayer(playerId);
      if(player != null) deliver(player, pending);
   }

   /**
    * Join hook: picks up items queued while the player was offline or before a restart.
    *
    * @param player the player who just connected
    */
   public static void onPlayerJoin(ServerPlayer player){
      // Checked without creating the key, so players who were never owed anything get nothing added to their file
      if(DataAccess.hasPlayerData(player.getUUID(), KEY) && !DataAccess.getPlayer(player.getUUID(), KEY).isEmpty()){
         ACTIVE.add(player.getUUID());
      }
   }

   /**
    * Leave hook: stops retrying for the player. Their queue stays in their data file.
    *
    * @param player the disconnecting player
    */
   public static void onPlayerLeave(ServerPlayer player){
      ACTIVE.remove(player.getUUID());
      FAILED_ATTEMPTS.remove(player.getUUID());
   }

   /** Server-stop hook: forgets the in-memory retry state. */
   public static void onServerStopped(){
      ACTIVE.clear();
      FAILED_ATTEMPTS.clear();
   }

   /**
    * Server tick hook: retries delivery for online players with something queued, once every
    * {@link #RETRY_INTERVAL} ticks.
    *
    * @param server the server being ticked
    */
   public static void tick(MinecraftServer server){
      if(ACTIVE.isEmpty() || server.getTickCount() % RETRY_INTERVAL != 0) return;
      Iterator<UUID> iterator = ACTIVE.iterator();
      List<ServerPlayer> online = new ArrayList<>();
      while(iterator.hasNext()){
         UUID playerId = iterator.next();
         ServerPlayer player = server.getPlayerList().getPlayer(playerId);
         if(player == null){
            iterator.remove();
            FAILED_ATTEMPTS.remove(playerId);
         }else{
            online.add(player);
         }
      }
      // deliver() removes finished players from ACTIVE, so it runs after the iteration
      for(int i = 0; i < online.size(); i++){
         ServerPlayer player = online.get(i);
         deliver(player, DataAccess.getPlayer(player.getUUID(), KEY));
      }
   }

   private static void deliver(ServerPlayer player, PendingItemReturns pending){
      UUID playerId = player.getUUID();
      if(pending.entries.isEmpty()){
         ACTIVE.remove(playerId);
         FAILED_ATTEMPTS.remove(playerId);
         return;
      }
      if(!player.isAlive()) return;

      Inventory inv = player.getInventory();
      boolean changed = false;
      for(int i = 0; i < pending.entries.size(); i++){
         Entry entry = pending.entries.get(i);
         ItemStack stack = entry.item();
         int before = stack.getCount();
         int slot = entry.prefSlot();
         if(slot >= 0 && slot < inv.getContainerSize()){
            ItemStack inSlot = inv.getItem(slot);
            if(inSlot.isEmpty() || ((InventoryAccessor) inv).canAddMore(inSlot, stack)){
               inv.add(slot, stack);
            }
         }
         if(!stack.isEmpty()) inv.add(stack);
         if(stack.getCount() != before) changed = true;
      }

      boolean leftover = false;
      for(int i = 0; i < pending.entries.size(); i++){
         if(!pending.entries.get(i).item().isEmpty()) leftover = true;
      }
      int failed = FAILED_ATTEMPTS.getOrDefault(playerId, 0) + 1;
      if(leftover && !changed && failed >= DROP_AFTER_ATTEMPTS){
         for(Entry entry : pending.entries){
            if(entry.item().isEmpty()) continue;
            ItemEntity itemEntity = player.drop(entry.item().copy(), false, Prediction.SERVER_ONLY);
            // Only cleared once it is safely in the world; otherwise it stays owed and is tried again
            if(itemEntity != null){
               itemEntity.setNoPickUpDelay();
               itemEntity.setTarget(playerId);
               entry.item().setCount(0);
            }
         }
         BorisLib.LOGGER.info("Dropped pending returned items at the feet of {} after {} attempts on a full inventory", player.getPlainTextName(), failed);
         changed = true;
      }

      if(pending.entries.removeIf(entry -> entry.item().isEmpty())) changed = true;
      if(changed){
         DataAccess.markPlayerDirty(playerId);
         player.containerMenu.broadcastChanges();
      }
      if(pending.entries.isEmpty()){
         ACTIVE.remove(playerId);
         FAILED_ATTEMPTS.remove(playerId);
      }else{
         FAILED_ATTEMPTS.put(playerId, changed ? 0 : failed);
      }
   }

   private record Entry(ItemStack item, int prefSlot) {
   }
}
