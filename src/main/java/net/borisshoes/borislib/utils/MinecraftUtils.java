package net.borisshoes.borislib.utils;

import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.EntityAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static org.apache.logging.log4j.Level.WARN;

public class MinecraftUtils {
   
   public static CompletableFuture<Suggestions> getPlayerSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> items = new HashSet<>();
      context.getSource().getOnlinePlayerNames().forEach(name -> items.add(name.toLowerCase(Locale.ROOT)));
      items.stream().filter(s -> s.startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   public static Either<Item, TagKey<Item>> parseItemOrTag(String str){
      if(str.startsWith("#")){ // It's a tag
         Identifier tagLoc = Identifier.parse(str.substring(1));
         TagKey<Item> tag = TagKey.create(Registries.ITEM, tagLoc);
         return Either.right(tag);
      }else{ // It's an item
         Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(str)).orElse(null);
         if(item == null) return null;
         return Either.left(item);
      }
   }
   
   public static MutableComponent getAtlasedTexture(Item item){
      Identifier id = BuiltInRegistries.ITEM.getResourceKey(item).get().identifier();
      Identifier newId = Identifier.fromNamespaceAndPath(id.getNamespace(),"item/"+id.getPath());
      return Component.object(new AtlasSprite(AtlasIds.ITEMS, newId));
   }
   
   public static MutableComponent getAtlasedTexture(Block block){
      Identifier id = BuiltInRegistries.BLOCK.getResourceKey(block).get().identifier();
      Identifier newId = Identifier.fromNamespaceAndPath(id.getNamespace(),"block/"+id.getPath());
      return Component.object(new AtlasSprite(AtlasIds.BLOCKS, newId));
   }
   
   public static MutableComponent getAtlasedTexture(Identifier atlas, Identifier rawId){
      return Component.object(new AtlasSprite(atlas, rawId));
   }
   
   public static MutableComponent getFormattedDimName(ResourceKey<Level> worldKey){
      if(worldKey.identifier().toString().equals(ServerLevel.OVERWORLD.identifier().toString())){
         return Component.literal("Overworld").withStyle(ChatFormatting.GREEN);
      }else if(worldKey.identifier().toString().equals(ServerLevel.NETHER.identifier().toString())){
         return Component.literal("The Nether").withStyle(ChatFormatting.RED);
      }else if(worldKey.identifier().toString().equals(ServerLevel.END.identifier().toString())){
         return Component.literal("The End").withStyle(ChatFormatting.DARK_PURPLE);
      }else{
         return Component.literal(worldKey.identifier().toString()).withStyle(ChatFormatting.YELLOW);
      }
   }
   
   private Vec3 findSafeTeleportSpot(ServerPlayer user, double maxRange, double minRange, double leniencyRange, double distStep, double radialStep, double dropStep, boolean checkFluid){
      ServerLevel world = user.level();
      Vec3 direction = user.getLookAngle().normalize();
      Vec3 origin = user.position();
      double maxDistSq = (maxRange + leniencyRange) * (maxRange + leniencyRange);
      Vec3 upRef = Math.abs(direction.y) < 0.999 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
      Vec3 right = direction.cross(upRef).normalize();
      Vec3 up = direction.cross(right).normalize();
      for(double d = maxRange; d >= minRange; d -= distStep){
         Vec3 center = origin.add(direction.scale(d));
         for(double r = 0.0; r <= leniencyRange + 1e-9; r += radialStep){
            int slices = r == 0.0 ? 1 : 12;
            for(int k = 0; k < slices; k++){
               double a = slices == 1 ? 0.0 : (2.0 * Math.PI * k) / slices;
               Vec3 lateral = right.scale(r * Math.cos(a)).add(up.scale(r * Math.sin(a)));
               Vec3 base = center.add(lateral);
               double[] yNudges = new double[]{0.0, 0.5, -0.5, 1.0, -1.0};
               for(double yOff : yNudges){
                  Vec3 candidate = new Vec3(base.x, base.y + yOff, base.z);
                  if(!isSpaceClearFor(user, world, candidate, checkFluid)) continue;
                  if(dropStep < 0 || hasGroundSupport(world, user, candidate)){
                     return candidate;
                  }
                  Vec3 down = candidate;
                  while(origin.distanceToSqr(down) <= maxDistSq && down.y > world.getMinY()){
                     down = down.add(0.0, -dropStep, 0.0);
                     if(!isSpaceClearFor(user, world, down, checkFluid)) break;
                     if(hasGroundSupport(world, user, down)){
                        return down;
                     }
                  }
               }
            }
         }
      }
      return null;
   }
   
   public static boolean hasGroundSupport(Level world, Entity entity, Vec3 targetPos){
      Vec3 delta = targetPos.subtract(entity.position());
      AABB targetBox = entity.getBoundingBox().move(delta);
      double eps = 1.0 / 16.0;
      AABB floorProbe = new AABB(targetBox.minX, targetBox.minY - eps, targetBox.minZ, targetBox.maxX, targetBox.minY, targetBox.maxZ);
      return world.getBlockCollisions(entity, floorProbe).iterator().hasNext();
   }
   
   public static boolean isSpaceClearFor(Entity entity, Level world, Vec3 targetPos, boolean checkFluid) {
      Vec3 delta = targetPos.subtract(entity.position());
      AABB targetBox = entity.getBoundingBox().move(delta);
      return world.noCollision(entity, targetBox, checkFluid);
   }
   
   public static <T extends Entity> T getClosestEntity(List<T> list, Vec3 pos){
      T closest = null;
      double smallestDist = Double.MAX_VALUE;
      for(T t : list){
         if(t.position().distanceTo(pos) < smallestDist){
            closest = t;
            smallestDist = t.position().distanceTo(pos);
         }
      }
      return closest;
   }
   
   public static ItemEntity getLargestItemEntity(List<ItemEntity> list){
      ItemEntity largest = null;
      double largestNumber = 0;
      for(ItemEntity itemEntity : list){
         ItemStack itemStack = itemEntity.getItem();
         if(itemStack.getCount() > largestNumber){
            largestNumber = itemStack.getCount();
            largest = itemEntity;
         }
      }
      return largest;
   }
   
   public static void removeMaxAbsorption(LivingEntity entity, Identifier id, float amount){
      AttributeMap attributeContainer = entity.getAttributes();
      AttributeInstance entityAttributeInstance = attributeContainer.getInstance(Attributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      AttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.amount();
         double newAmount = current-amount;
         entityAttributeInstance.removeModifier(id);
         if(newAmount > 0.01){
            AttributeModifier modifier = new AttributeModifier(id, newAmount, AttributeModifier.Operation.ADD_VALUE);
            entityAttributeInstance.addPermanentModifier(modifier);
         }
      }
   }
   
   public static void addMaxAbsorption(LivingEntity entity, Identifier id, double amount){
      AttributeMap attributeContainer = entity.getAttributes();
      AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
      AttributeInstance entityAttributeInstance = attributeContainer.getInstance(Attributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      AttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.amount();
         entityAttributeInstance.removeModifier(id);
         modifier = new AttributeModifier(id, amount+current, AttributeModifier.Operation.ADD_VALUE);
      }
      entityAttributeInstance.addPermanentModifier(modifier);
   }
   
   public static ItemStack removeLore(ItemStack stack){
      ItemStack copy = stack.copy();
      copy.remove(DataComponents.LORE);
      return copy;
   }
   
   public static Holder<Enchantment> getEnchantment(ResourceKey<Enchantment> key){
      if(BorisLib.SERVER == null){
         LOGGER.log(WARN,"Attempted to access Enchantment "+key.toString()+" before DRM is available");
         return null;
      }
      Optional<Holder.Reference<Enchantment>> opt = BorisLib.SERVER.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
      return opt.orElse(null);
   }
   
   public static Holder<Enchantment> getEnchantment(RegistryAccess drm, ResourceKey<Enchantment> key){
      Optional<Holder.Reference<Enchantment>> opt = drm.lookupOrThrow(Registries.ENCHANTMENT).get(key);
      return opt.orElse(null);
   }
   
   public static ItemEnchantments makeEnchantComponent(EnchantmentInstance... entries){
      ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
      
      for(EnchantmentInstance entry : entries){
         builder.upgrade(entry.enchantment(),entry.level());
      }
      
      return builder.toImmutable();
   }
   
   public static void giveStacks(Player player, ItemStack... stacks){
      returnItems(new SimpleContainer(stacks),player);
   }
   
   public static void returnItems(Container inv, Player player){
      if(inv == null) return;
      for(int i = 0; i<inv.getContainerSize(); i++){
         ItemStack stack = inv.getItem(i).copy();
         if(!stack.isEmpty()){
            inv.setItem(0, ItemStack.EMPTY);
            
            ItemEntity itemEntity;
            boolean bl = player.getInventory().add(stack);
            if(!bl || !stack.isEmpty()){
               itemEntity = player.drop(stack, false);
               if(itemEntity == null) continue;
               itemEntity.setNoPickUpDelay();
               itemEntity.setTarget(player.getUUID());
               continue;
            }
            stack.setCount(1);
            itemEntity = player.drop(stack, false);
            if(itemEntity != null){
               itemEntity.makeFakeItem();
            }
            player.containerMenu.broadcastChanges();
         }
      }
   }
   
   public static boolean removeItems(Player player, Item item, int count){
      if(player.isCreative()) return true;
      int remaining = count;
      Inventory inv = player.getInventory();
      int[] slots = new int[inv.getContainerSize()];
      for(int i = 0; i < inv.getContainerSize() && remaining > 0; i++){
         ItemStack stack = inv.getItem(i);
         int stackCount = stack.getCount();
         if(stack.is(item)){
            if(remaining < stackCount){
               slots[i] = remaining;
               remaining = 0;
            }else{
               slots[i] = stackCount;
               remaining -= stackCount;
            }
         }
      }
      if(remaining > 0)return false;
      
      for(int i = 0; i < slots.length; i++){
         if(slots[i] <= 0) continue;
         inv.removeItem(i,slots[i]);
      }
      return true;
   }
   
   public static List<ItemStack> getMatchingItemsFromContainerComp(ItemStack container, Item item){
      ItemContainerContents containerItems = container.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
      ArrayList<ItemStack> items = new ArrayList<>();
      for(ItemStack stack : containerItems.nonEmptyItems()){
         if(stack.is(item)){
            items.add(stack);
         }
      }
      return items;
   }
   
   public static void attributeEffect(LivingEntity livingEntity, Holder<Attribute> attribute, double value, AttributeModifier.Operation operation, Identifier identifier, boolean remove){
      boolean hasMod = livingEntity.getAttributes().hasModifier(attribute,identifier);
      if(hasMod && remove){ // Remove the modifier
         HashMultimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
         map.put(attribute, new AttributeModifier(identifier, value, operation));
         livingEntity.getAttributes().removeAttributeModifiers(map);
      }else if(!hasMod && !remove){ // Add the modifier
         HashMultimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
         map.put(attribute, new AttributeModifier(identifier, value, operation));
         livingEntity.getAttributes().addTransientAttributeModifiers(map);
      }
   }
   
   public static Tuple<ItemContainerContents, ItemStack> tryAddStackToContainerComp(ItemContainerContents container, int size, ItemStack stack){
      List<ItemStack> beltList = new ArrayList<>(container.stream().toList());
      
      // Fill up existing slots first
      for(ItemStack existingStack : beltList){
         int curCount = stack.getCount();
         if(stack.isEmpty()) break;
         boolean canCombine = !existingStack.isEmpty()
               && ItemStack.isSameItemSameComponents(existingStack, stack)
               && existingStack.isStackable()
               && existingStack.getCount() < existingStack.getMaxStackSize();
         if(!canCombine) continue;
         int toAdd = Math.min(existingStack.getMaxStackSize() - existingStack.getCount(),curCount);
         existingStack.grow(toAdd);
         stack.setCount(curCount - toAdd);
      }
      
      int nonEmpty = (int) beltList.stream().filter(s -> !s.isEmpty()).count();
      
      if(!stack.isEmpty() && nonEmpty < size){
         if(nonEmpty == beltList.size()){ // No middle empty slots, append new slot to end
            beltList.add(stack.copyAndClear());
         }else{
            for(int i = 0; i < nonEmpty; i++){ // Find middle empty slot to fill
               if(beltList.get(i).isEmpty()){
                  beltList.set(i, stack.copyAndClear());
                  break;
               }
            }
         }
      }
      return new Tuple<>(ItemContainerContents.fromItems(beltList),stack);
   }
   
   public static LasercastResult lasercast(Level world, Vec3 startPos, Vec3 direction, double distance, boolean blockedByShields, Entity entity){
      Vec3 rayEnd = startPos.add(direction.scale(distance));
      BlockHitResult raycast = world.clip(new ClipContext(startPos,rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
      EntityHitResult entityHit;
      List<Entity> hits = new ArrayList<>();
      AABB box = new AABB(startPos,raycast.getLocation());
      box = box.inflate(2);
      // Primary hitscan check
      do{
         entityHit = ProjectileUtil.getEntityHitResult(entity,startPos,raycast.getLocation(),box, e -> e instanceof LivingEntity && !e.isSpectator() && !hits.contains(e),distance*2);
         if(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY){
            hits.add(entityHit.getEntity());
         }
      }while(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY);
      
      // Secondary hitscan check to add lenience
      List<Entity> hits2 = world.getEntities(entity, box, (e)-> e instanceof LivingEntity && !e.isSpectator() && !hits.contains(e) && MathUtils.hitboxRaycast(e,startPos,raycast.getLocation()));
      hits.addAll(hits2);
      hits.sort(Comparator.comparingDouble(e->e.distanceTo(entity)));
      
      if(!blockedByShields){
         return new LasercastResult(startPos, raycast.getLocation(), direction, hits);
      }
      
      List<Entity> hits3 = new ArrayList<>();
      Vec3 endPoint = raycast.getLocation();
      for(Entity hit : hits){
         boolean blocked = false;
         if(hit instanceof ServerPlayer hitPlayer && hitPlayer.isBlocking()){
            double dp = hitPlayer.getForward().normalize().dot(direction.normalize());
            blocked = dp < -0.6;
            if(blocked){
               SoundUtils.playSound(world,hitPlayer.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,1f,1f);
               endPoint = startPos.add(direction.normalize().scale(direction.normalize().dot(hitPlayer.position().subtract(startPos)))).subtract(direction.normalize());
            }
         }
         hits3.add(hit);
         if(blocked){
            break;
         }
      }
      
      return new LasercastResult(startPos,endPoint,direction,hits3);
   }
   
   public record LasercastResult(Vec3 startPos, Vec3 endPos, Vec3 direction, List<Entity> sortedHits){}
   
   public static ServerPlayer getRequestedPlayer(MinecraftServer server, NameAndId playerEntry){
      ServerPlayer requestedPlayer = server.getPlayerList().getPlayerByName(playerEntry.name());
      
      if (requestedPlayer == null) {
         requestedPlayer = new ServerPlayer(server, server.overworld(), new GameProfile(playerEntry.id(), playerEntry.name()), ClientInformation.createDefault());
         Optional<ValueInput> readViewOpt = server
               .getPlayerList()
               .loadPlayerData(playerEntry)
               .map(playerData -> TagValueInput.create(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), server.registryAccess(), playerData));
         readViewOpt.ifPresent(requestedPlayer::load);
         
         if (readViewOpt.isPresent()) {
            ValueInput readView = readViewOpt.get();
            Optional<String> dimension = readView.getString("Dimension");
            
            if (dimension.isPresent()) {
               ServerLevel world = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.tryParse(dimension.get())));
               if(world != null) ((EntityAccessor) requestedPlayer).callSetLevel(world);
            }
         }
      }
      return requestedPlayer;
   }
   
   public static boolean removeItemEntities(ServerLevel serverWorld, AABB area, Predicate<ItemStack> predicate, int count){
      List<ItemEntity> entities = serverWorld.getEntitiesOfClass(ItemEntity.class, area, entity -> predicate.test(entity.getItem()));
      int foundCount = 0;
      for(ItemEntity entity : entities){
         foundCount += entity.getItem().getCount();
         if(foundCount >= count) break;
      }
      if(foundCount < count) return false;
      for(ItemEntity entity : entities){
         ItemStack stack = entity.getItem();
         int stackCount = stack.getCount();
         int toRemove = Math.min(count, stackCount);
         if(toRemove >= stackCount){
            entity.discard();
         }else{
            stack.setCount(stackCount - toRemove);
         }
         count -= toRemove;
         if(count <= 0) break;
      }
      return true;
   }
}
