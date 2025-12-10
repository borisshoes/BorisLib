package net.borisshoes.borislib.utils;

import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.mixins.EntityAccessor;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.PrepareSpawnTask;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static org.apache.logging.log4j.Level.WARN;

public class MinecraftUtils {
   
   private Vec3d findSafeTeleportSpot(ServerPlayerEntity user, double maxRange, double minRange, double leniencyRange, double distStep, double radialStep, double dropStep, boolean checkFluid){
      ServerWorld world = user.getEntityWorld();
      Vec3d direction = user.getRotationVector().normalize();
      Vec3d origin = user.getEntityPos();
      double maxDistSq = (maxRange + leniencyRange) * (maxRange + leniencyRange);
      Vec3d upRef = Math.abs(direction.y) < 0.999 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
      Vec3d right = direction.crossProduct(upRef).normalize();
      Vec3d up = direction.crossProduct(right).normalize();
      for(double d = maxRange; d >= minRange; d -= distStep){
         Vec3d center = origin.add(direction.multiply(d));
         for(double r = 0.0; r <= leniencyRange + 1e-9; r += radialStep){
            int slices = r == 0.0 ? 1 : 12;
            for(int k = 0; k < slices; k++){
               double a = slices == 1 ? 0.0 : (2.0 * Math.PI * k) / slices;
               Vec3d lateral = right.multiply(r * Math.cos(a)).add(up.multiply(r * Math.sin(a)));
               Vec3d base = center.add(lateral);
               double[] yNudges = new double[]{0.0, 0.5, -0.5, 1.0, -1.0};
               for(double yOff : yNudges){
                  Vec3d candidate = new Vec3d(base.x, base.y + yOff, base.z);
                  if(!isSpaceClearFor(user, world, candidate, checkFluid)) continue;
                  if(dropStep < 0 || hasGroundSupport(world, user, candidate)){
                     return candidate;
                  }
                  Vec3d down = candidate;
                  while(origin.squaredDistanceTo(down) <= maxDistSq && down.y > world.getBottomY()){
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
   
   public static boolean hasGroundSupport(World world, Entity entity, Vec3d targetPos){
      Vec3d delta = targetPos.subtract(entity.getEntityPos());
      Box targetBox = entity.getBoundingBox().offset(delta);
      double eps = 1.0 / 16.0;
      Box floorProbe = new Box(targetBox.minX, targetBox.minY - eps, targetBox.minZ, targetBox.maxX, targetBox.minY, targetBox.maxZ);
      return world.getBlockCollisions(entity, floorProbe).iterator().hasNext();
   }
   
   public static boolean isSpaceClearFor(Entity entity, World world, Vec3d targetPos, boolean checkFluid) {
      Vec3d delta = targetPos.subtract(entity.getEntityPos());
      Box targetBox = entity.getBoundingBox().offset(delta);
      return world.isSpaceEmpty(entity, targetBox, checkFluid);
   }
   
   public static <T extends Entity> T getClosestEntity(List<T> list, Vec3d pos){
      T closest = null;
      double smallestDist = Double.MAX_VALUE;
      for(T t : list){
         if(t.getEntityPos().distanceTo(pos) < smallestDist){
            closest = t;
            smallestDist = t.getEntityPos().distanceTo(pos);
         }
      }
      return closest;
   }
   
   public static ItemEntity getLargestItemEntity(List<ItemEntity> list){
      ItemEntity largest = null;
      double largestNumber = 0;
      for(ItemEntity itemEntity : list){
         ItemStack itemStack = itemEntity.getStack();
         if(itemStack.getCount() > largestNumber){
            largestNumber = itemStack.getCount();
            largest = itemEntity;
         }
      }
      return largest;
   }
   
   public static void removeMaxAbsorption(LivingEntity entity, Identifier id, float amount){
      AttributeContainer attributeContainer = entity.getAttributes();
      EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(EntityAttributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      EntityAttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.value();
         double newAmount = current-amount;
         entityAttributeInstance.removeModifier(id);
         if(newAmount > 0.01){
            EntityAttributeModifier modifier = new EntityAttributeModifier(id, newAmount, EntityAttributeModifier.Operation.ADD_VALUE);
            entityAttributeInstance.addPersistentModifier(modifier);
         }
      }
   }
   
   public static void addMaxAbsorption(LivingEntity entity, Identifier id, double amount){
      AttributeContainer attributeContainer = entity.getAttributes();
      EntityAttributeModifier modifier = new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_VALUE);
      EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(EntityAttributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      EntityAttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.value();
         entityAttributeInstance.removeModifier(id);
         modifier = new EntityAttributeModifier(id, amount+current, EntityAttributeModifier.Operation.ADD_VALUE);
      }
      entityAttributeInstance.addPersistentModifier(modifier);
   }
   
   public static ItemStack removeLore(ItemStack stack){
      ItemStack copy = stack.copy();
      copy.remove(DataComponentTypes.LORE);
      return copy;
   }
   
   public static RegistryEntry<Enchantment> getEnchantment(RegistryKey<Enchantment> key){
      if(BorisLib.SERVER == null){
         LOGGER.log(WARN,"Attempted to access Enchantment "+key.toString()+" before DRM is available");
         return null;
      }
      Optional<RegistryEntry.Reference<Enchantment>> opt = BorisLib.SERVER.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key);
      return opt.orElse(null);
   }
   
   public static RegistryEntry<Enchantment> getEnchantment(DynamicRegistryManager drm, RegistryKey<Enchantment> key){
      Optional<RegistryEntry.Reference<Enchantment>> opt = drm.getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key);
      return opt.orElse(null);
   }
   
   public static ItemEnchantmentsComponent makeEnchantComponent(EnchantmentLevelEntry... entries){
      ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
      
      for(EnchantmentLevelEntry entry : entries){
         builder.add(entry.enchantment(),entry.level());
      }
      
      return builder.build();
   }
   
   public static void giveStacks(PlayerEntity player, ItemStack... stacks){
      returnItems(new SimpleInventory(stacks),player);
   }
   
   public static void returnItems(Inventory inv, PlayerEntity player){
      if(inv == null) return;
      for(int i=0; i<inv.size();i++){
         ItemStack stack = inv.getStack(i).copy();
         if(!stack.isEmpty()){
            inv.setStack(0,ItemStack.EMPTY);
            
            ItemEntity itemEntity;
            boolean bl = player.getInventory().insertStack(stack);
            if(!bl || !stack.isEmpty()){
               itemEntity = player.dropItem(stack, false);
               if(itemEntity == null) continue;
               itemEntity.resetPickupDelay();
               itemEntity.setOwner(player.getUuid());
               continue;
            }
            stack.setCount(1);
            itemEntity = player.dropItem(stack, false);
            if(itemEntity != null){
               itemEntity.setDespawnImmediately();
            }
            player.currentScreenHandler.sendContentUpdates();
         }
      }
   }
   
   public static boolean removeItems(PlayerEntity player, Item item, int count){
      if(player.isCreative()) return true;
      int remaining = count;
      PlayerInventory inv = player.getInventory();
      int[] slots = new int[inv.size()];
      for(int i = 0; i < inv.size() && remaining > 0; i++){
         ItemStack stack = inv.getStack(i);
         int stackCount = stack.getCount();
         if(stack.isOf(item)){
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
         inv.removeStack(i,slots[i]);
      }
      return true;
   }
   
   public static List<ItemStack> getMatchingItemsFromContainerComp(ItemStack container, Item item){
      ContainerComponent containerItems = container.getOrDefault(DataComponentTypes.CONTAINER,ContainerComponent.DEFAULT);
      ArrayList<ItemStack> items = new ArrayList<>();
      for(ItemStack stack : containerItems.iterateNonEmpty()){
         if(stack.isOf(item)){
            items.add(stack);
         }
      }
      return items;
   }
   
   public static void attributeEffect(LivingEntity livingEntity, RegistryEntry<EntityAttribute> attribute, double value, EntityAttributeModifier.Operation operation, Identifier identifier, boolean remove){
      boolean hasMod = livingEntity.getAttributes().hasModifierForAttribute(attribute,identifier);
      if(hasMod && remove){ // Remove the modifier
         HashMultimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> map = HashMultimap.create();
         map.put(attribute, new EntityAttributeModifier(identifier, value, operation));
         livingEntity.getAttributes().removeModifiers(map);
      }else if(!hasMod && !remove){ // Add the modifier
         HashMultimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> map = HashMultimap.create();
         map.put(attribute, new EntityAttributeModifier(identifier, value, operation));
         livingEntity.getAttributes().addTemporaryModifiers(map);
      }
   }
   
   public static Pair<ContainerComponent,ItemStack> tryAddStackToContainerComp(ContainerComponent container, int size, ItemStack stack){
      List<ItemStack> beltList = new ArrayList<>(container.stream().toList());
      
      // Fill up existing slots first
      for(ItemStack existingStack : beltList){
         int curCount = stack.getCount();
         if(stack.isEmpty()) break;
         boolean canCombine = !existingStack.isEmpty()
               && ItemStack.areItemsAndComponentsEqual(existingStack, stack)
               && existingStack.isStackable()
               && existingStack.getCount() < existingStack.getMaxCount();
         if(!canCombine) continue;
         int toAdd = Math.min(existingStack.getMaxCount() - existingStack.getCount(),curCount);
         existingStack.increment(toAdd);
         stack.setCount(curCount - toAdd);
      }
      
      int nonEmpty = (int) beltList.stream().filter(s -> !s.isEmpty()).count();
      
      if(!stack.isEmpty() && nonEmpty < size){
         if(nonEmpty == beltList.size()){ // No middle empty slots, append new slot to end
            beltList.add(stack.copyAndEmpty());
         }else{
            for(int i = 0; i < nonEmpty; i++){ // Find middle empty slot to fill
               if(beltList.get(i).isEmpty()){
                  beltList.set(i, stack.copyAndEmpty());
                  break;
               }
            }
         }
      }
      return new Pair<>(ContainerComponent.fromStacks(beltList),stack);
   }
   
   public static LasercastResult lasercast(World world, Vec3d startPos, Vec3d direction, double distance, boolean blockedByShields, Entity entity){
      Vec3d rayEnd = startPos.add(direction.multiply(distance));
      BlockHitResult raycast = world.raycast(new RaycastContext(startPos,rayEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
      EntityHitResult entityHit;
      List<Entity> hits = new ArrayList<>();
      Box box = new Box(startPos,raycast.getPos());
      box = box.expand(2);
      // Primary hitscan check
      do{
         entityHit = ProjectileUtil.raycast(entity,startPos,raycast.getPos(),box, e -> e instanceof LivingEntity && !e.isSpectator() && !hits.contains(e),distance*2);
         if(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY){
            hits.add(entityHit.getEntity());
         }
      }while(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY);
      
      // Secondary hitscan check to add lenience
      List<Entity> hits2 = world.getOtherEntities(entity, box, (e)-> e instanceof LivingEntity && !e.isSpectator() && !hits.contains(e) && MathUtils.hitboxRaycast(e,startPos,raycast.getPos()));
      hits.addAll(hits2);
      hits.sort(Comparator.comparingDouble(e->e.distanceTo(entity)));
      
      if(!blockedByShields){
         return new LasercastResult(startPos, raycast.getPos(), direction, hits);
      }
      
      List<Entity> hits3 = new ArrayList<>();
      Vec3d endPoint = raycast.getPos();
      for(Entity hit : hits){
         boolean blocked = false;
         if(hit instanceof ServerPlayerEntity hitPlayer && hitPlayer.isBlocking()){
            double dp = hitPlayer.getRotationVecClient().normalize().dotProduct(direction.normalize());
            blocked = dp < -0.6;
            if(blocked){
               SoundUtils.playSound(world,hitPlayer.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS,1f,1f);
               endPoint = startPos.add(direction.normalize().multiply(direction.normalize().dotProduct(hitPlayer.getEntityPos().subtract(startPos)))).subtract(direction.normalize());
            }
         }
         hits3.add(hit);
         if(blocked){
            break;
         }
      }
      
      return new LasercastResult(startPos,endPoint,direction,hits3);
   }
   
   public record LasercastResult(Vec3d startPos, Vec3d endPos, Vec3d direction, List<Entity> sortedHits){}
   
   public static ServerPlayerEntity getRequestedPlayer(MinecraftServer server, PlayerConfigEntry playerEntry){
      ServerPlayerEntity requestedPlayer = server.getPlayerManager().getPlayer(playerEntry.name());
      
      if (requestedPlayer == null) {
         requestedPlayer = new ServerPlayerEntity(server, server.getOverworld(), new GameProfile(playerEntry.id(), playerEntry.name()), SyncedClientOptions.createDefault());
         Optional<ReadView> readViewOpt = server
               .getPlayerManager()
               .loadPlayerData(playerEntry)
               .map(playerData -> NbtReadView.create(new ErrorReporter.Logging(LogUtils.getLogger()), server.getRegistryManager(), playerData));
         readViewOpt.ifPresent(requestedPlayer::readData);
         
         if (readViewOpt.isPresent()) {
            ReadView readView = readViewOpt.get();
            Optional<String> dimension = readView.getOptionalString("Dimension");
            
            if (dimension.isPresent()) {
               ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(dimension.get())));
               if(world != null) ((EntityAccessor) requestedPlayer).callSetWorld(world);
            }
         }
      }
      return requestedPlayer;
   }
   
   public static boolean removeItemEntities(ServerWorld serverWorld, Box area, Predicate<ItemStack> predicate, int count){
      List<ItemEntity> entities = serverWorld.getEntitiesByClass(ItemEntity.class, area, entity -> predicate.test(entity.getStack()));
      int foundCount = 0;
      for(ItemEntity entity : entities){
         foundCount += entity.getStack().getCount();
         if(foundCount >= count) break;
      }
      if(foundCount < count) return false;
      for(ItemEntity entity : entities){
         ItemStack stack = entity.getStack();
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
