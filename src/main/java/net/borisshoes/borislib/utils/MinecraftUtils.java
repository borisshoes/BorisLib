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
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static net.borisshoes.borislib.BorisLib.LOGGER;
import static org.apache.logging.log4j.Level.WARN;

/**
 * A comprehensive collection of utility methods for common Minecraft operations including block/item queries,
 * entity management, attribute manipulation, teleportation, inventory operations, and combat utilities.
 *
 * <p>This class serves as a central repository for gameplay-related helper methods that don't fit
 * into more specialized utility classes. Methods are organized into logical categories:
 *
 * <h3>Categories:</h3>
 * <ul>
 *   <li><b>Block Utilities</b> — Finding similar blocks, block queries</li>
 *   <li><b>Item Utilities</b> — Dye items, tag queries, container operations</li>
 *   <li><b>Entity Utilities</b> — Finding entities, closest entity calculations</li>
 *   <li><b>Attribute Management</b> — Modifying entity attributes (health, absorption, etc.)</li>
 *   <li><b>Inventory Operations</b> — Giving items, removing items, container manipulation</li>
 *   <li><b>Teleportation</b> — Safe teleport spot finding, position validation</li>
 *   <li><b>Enchantment Utilities</b> — Enchantment registry access and component creation</li>
 *   <li><b>Visual & Display</b> — Dimension names, atlased textures</li>
 *   <li><b>Combat Utilities</b> — Arrow damage, laser raycasting</li>
 * </ul>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Find similar blocks (all concrete colors)
 * Set<Block> allConcrete = MinecraftUtils.getSimilarBlocks(Blocks.WHITE_CONCRETE);
 *
 * // Give items to player
 * MinecraftUtils.giveStacks(player,
 *     new ItemStack(Items.DIAMOND, 10),
 *     new ItemStack(Items.EMERALD, 5));
 *
 * // Perform a lasercast
 * LasercastResult result = MinecraftUtils.lasercast(
 *     world, startPos, direction, 50.0, true, player);
 * for (Entity hit : result.sortedHits()) {
 *     // Process hits
 * }
 * }</pre>
 *
 * @see MathUtils For mathematical and geometric operations
 * @see ItemModDataHandler For item data storage
 * @see ItemContainerContentsMutable For mutable container operations
 */
public class MinecraftUtils {
   
   /**
    * Finds all blocks in the registry that have the same path as the base block.
    *
    * <p>This is useful for finding color variants of the same block type. For example,
    * passing {@code Blocks.WHITE_CONCRETE} will return all 16 concrete color variants.
    *
    * @param baseBlock the base block to match against
    * @return set of all blocks with matching paths (includes the base block itself)
    */
   public static Set<Block> getSimilarBlocks(Block baseBlock){
      Set<Block> allowedBlocks = new HashSet<>();
      allowedBlocks.add(baseBlock);
      Identifier baseId = BuiltInRegistries.BLOCK.getKey(baseBlock);
      for(Identifier similarId : BuiltInRegistries.BLOCK.keySet()){
         if(similarId.getPath().equals(baseId.getPath()))
            allowedBlocks.add(BuiltInRegistries.BLOCK.getValue(similarId));
      }
      return allowedBlocks;
   }
   
   /**
    * Calculates a damage percentage based on arrow velocity.
    *
    * <p>Returns a value typically between 0 and 1, where 0.5 is minimum natural velocity
    * and 1.0+ is full power. This is clamped between 0.5 and 10 for sanity.
    *
    * @param arrow the arrow entity
    * @return the damage percentage (0.5-1.0 for natural shots)
    * @see #getArrowPercentage(AbstractArrow, float) For custom minimum percentage
    */
   public static float getArrowPercentage(AbstractArrow arrow){ // 0.5 is usually smallest natural value and 2.5-3 is usually largest natural value
      return getArrowPercentage(arrow, 0f);
   }
   
   /**
    * Calculates a damage percentage based on arrow velocity with a custom minimum.
    *
    * @param arrow the arrow entity
    * @param minPercent the minimum percentage to return
    * @return the damage percentage, at least minPercent
    */
   public static float getArrowPercentage(AbstractArrow arrow, float minPercent){ // 0.5 is usually smallest natural value and 2.5-3 is usually largest natural value
      return Math.max(minPercent, ((float) Mth.clamp(arrow.getDeltaMovement().length(), 0.5, 10) - 0.5f) / 2.5f);
   }
   
   public static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId){
      for(ServerLevel level : server.getAllLevels()){
         Entity entity = level.getEntity(entityId);
         if(entity instanceof LivingEntity living) return living;
      }
      return null;
   }
   
   /**
    * Returns the first item in the registry that matches the given tag.
    *
    * @param tag The TagKey to search for
    * @return The first matching Item, or null if no items match the tag
    */
   public static Item getFirstItemFromTag(TagKey<Item> tag){
      return BuiltInRegistries.ITEM.get(tag)
            .flatMap(holders -> holders.stream().findFirst())
            .map(Holder::value)
            .orElse(null);
   }
   
   /**
    * Finds all online players whose names start with the current command input.
    *
    * @param context the command context
    * @param builder the suggestions builder
    * @return a future containing player name suggestions
    */
   public static CompletableFuture<Suggestions> getPlayerSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder){
      String start = builder.getRemaining().toLowerCase(Locale.ROOT);
      Set<String> items = new HashSet<>();
      context.getSource().getOnlinePlayerNames().forEach(name -> items.add(name.toLowerCase(Locale.ROOT)));
      items.stream().filter(s -> s.startsWith(start)).forEach(builder::suggest);
      return builder.buildFuture();
   }
   
   /**
    * Parses a string as either an item (namespace:path) or a tag (#namespace:path).
    *
    * @param str the input string
    * @return an Either containing the Item (left) or TagKey (right), or null if invalid
    */
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
   
   /**
    * Creates a text component displaying the item's texture from the item atlas.
    *
    * @param item the item
    * @return a text component with the texture
    */
   public static MutableComponent getAtlasedTexture(Item item){
      Identifier id = BuiltInRegistries.ITEM.getResourceKey(item).get().identifier();
      Identifier newId = Identifier.fromNamespaceAndPath(id.getNamespace(), "item/" + id.getPath());
      return Component.object(new AtlasSprite(AtlasIds.ITEMS, newId));
   }
   
   /**
    * Creates a text component displaying the block's texture from the block atlas.
    *
    * @param block the block
    * @return a text component with the texture
    */
   public static MutableComponent getAtlasedTexture(Block block){
      Identifier id = BuiltInRegistries.BLOCK.getResourceKey(block).get().identifier();
      Identifier newId = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath());
      return Component.object(new AtlasSprite(AtlasIds.BLOCKS, newId));
   }
   
   /**
    * Creates a text component displaying a texture from a custom atlas.
    *
    * @param atlas identifier for the atlas
    * @param rawId identifier for the texture
    * @return a text component with the texture
    */
   public static MutableComponent getAtlasedTexture(Identifier atlas, Identifier rawId){
      return Component.object(new AtlasSprite(atlas, rawId));
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
   
   /**
    * Checks if an entity would have solid ground support at the target position.
    *
    * @param world the level
    * @param entity the entity type to check for
    * @param targetPos the position to test
    * @return {@code true} if supported by blocks
    */
   public static boolean hasGroundSupport(Level world, Entity entity, Vec3 targetPos){
      Vec3 delta = targetPos.subtract(entity.position());
      AABB targetBox = entity.getBoundingBox().move(delta);
      double eps = 1.0 / 16.0;
      AABB floorProbe = new AABB(targetBox.minX, targetBox.minY - eps, targetBox.minZ, targetBox.maxX, targetBox.minY, targetBox.maxZ);
      return world.getBlockCollisions(entity, floorProbe).iterator().hasNext();
   }
   
   /**
    * Checks if the specified space is clear of collisions for the entity.
    *
    * @param entity the entity to check
    * @param world the level
    * @param targetPos the center position
    * @param checkFluid whether fluids count as collisions
    * @return {@code true} if clear
    */
   public static boolean isSpaceClearFor(Entity entity, Level world, Vec3 targetPos, boolean checkFluid){
      Vec3 delta = targetPos.subtract(entity.position());
      AABB targetBox = entity.getBoundingBox().move(delta);
      return world.noCollision(entity, targetBox, checkFluid);
   }
   
   /**
    * Finds the entity in the list closest to the target position.
    *
    * @param list the entities to search
    * @param pos the target position
    * @param <T> entity type
    * @return the closest entity, or null if list is empty
    */
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
   
   /**
    * Finds the ItemEntity with the largest stack count in the list.
    *
    * @param list item entities to search
    * @return the largest ItemEntity, or null
    */
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
   
   /**
    * Reduces the amount of max absorption added by a specific modifier ID.
    *
    * @param entity the entity
    * @param id modifier identifier
    * @param amount amount to remove
    */
   public static void removeMaxAbsorption(LivingEntity entity, Identifier id, float amount){
      AttributeMap attributeContainer = entity.getAttributes();
      AttributeInstance entityAttributeInstance = attributeContainer.getInstance(Attributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      AttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.amount();
         double newAmount = current - amount;
         entityAttributeInstance.removeModifier(id);
         if(newAmount > 0.01){
            AttributeModifier modifier = new AttributeModifier(id, newAmount, AttributeModifier.Operation.ADD_VALUE);
            entityAttributeInstance.addPermanentModifier(modifier);
         }
      }
   }
   
   /**
    * Increases or adds a max absorption modifier to an entity.
    *
    * @param entity the entity
    * @param id modifier identifier
    * @param amount amount to add
    */
   public static void addMaxAbsorption(LivingEntity entity, Identifier id, double amount){
      AttributeMap attributeContainer = entity.getAttributes();
      AttributeModifier modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
      AttributeInstance entityAttributeInstance = attributeContainer.getInstance(Attributes.MAX_ABSORPTION);
      if(entityAttributeInstance == null) return;
      AttributeModifier existing = entityAttributeInstance.getModifier(id);
      if(existing != null){
         double current = existing.amount();
         entityAttributeInstance.removeModifier(id);
         modifier = new AttributeModifier(id, amount + current, AttributeModifier.Operation.ADD_VALUE);
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
         LOGGER.log(WARN, "Attempted to access Enchantment " + key.toString() + " before DRM is available");
         return null;
      }
      Optional<Holder.Reference<Enchantment>> opt = BorisLib.SERVER.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
      return opt.orElse(null);
   }
   
   public static Holder<Enchantment> getEnchantment(RegistryAccess drm, ResourceKey<Enchantment> key){
      Optional<Holder.Reference<Enchantment>> opt = drm.lookupOrThrow(Registries.ENCHANTMENT).get(key);
      return opt.orElse(null);
   }
   
   /**
    * Creates an immutable ItemEnchantments component from a list of instances.
    *
    * @param entries the enchantments to include
    * @return the enchantment component
    */
   public static ItemEnchantments makeEnchantComponent(EnchantmentInstance... entries){
      ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
      
      for(EnchantmentInstance entry : entries){
         builder.upgrade(entry.enchantment(), entry.level());
      }
      
      return builder.toImmutable();
   }
   
   /**
    * Disperses item stacks to a player, attempting to add to inventory first then dropping.
    *
    * @param player the recipient
    * @param stacks stacks to give
    */
   public static void giveStacks(Player player, ItemStack... stacks){
      returnItems(new SimpleContainer(stacks), player);
   }
   
   /**
    * Returns all items from a container to the player's inventory or drops them.
    *
    * @param inv source container
    * @param player recipient player
    */
   public static void returnItems(Container inv, Player player){
      if(inv == null) return;
      for(int i = 0; i < inv.getContainerSize(); i++){
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
   
   /**
    * Attempts to remove a specific amount of an item from a player's inventory.
    *
    * @param player the player
    * @param item the item type
    * @param count number of items to remove
    * @return {@code true} if sufficient items were found and removed
    */
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
      if(remaining > 0) return false;
      
      for(int i = 0; i < slots.length; i++){
         if(slots[i] <= 0) continue;
         inv.removeItem(i, slots[i]);
      }
      return true;
   }
   
   /**
    * Searches an item's container component (e.g. Bundle) for matches of an item type.
    *
    * @param container the item with a container component
    * @param item item type to match
    * @return list of matching templates
    */
   public static List<ItemStackTemplate> getMatchingItemsFromContainerComp(ItemStack container, Item item){
      ItemContainerContents containerItems = container.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
      ArrayList<ItemStackTemplate> items = new ArrayList<>();
      for(ItemStackTemplate stack : containerItems.nonEmptyItems()){
         if(stack.is(item)){
            items.add(stack);
         }
      }
      return items;
   }
   
   /**
    * Adds or removes a transient attribute modifier.
    *
    * @param livingEntity the entity
    * @param attribute target attribute
    * @param value modifier value
    * @param operation modifier operation
    * @param identifier modifier ID
    * @param remove whether to remove instead of add
    */
   public static void attributeEffect(LivingEntity livingEntity, Holder<Attribute> attribute, double value, AttributeModifier.Operation operation, Identifier identifier, boolean remove){
      boolean hasMod = livingEntity.getAttributes().hasModifier(attribute, identifier);
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
   
   /**
    * Updates an existing transient attribute modifier or adds it if missing.
    *
    * @param livingEntity the entity
    * @param attribute target attribute
    * @param value new value
    * @param operation modifier operation
    * @param identifier modifier ID
    * @param upsert whether to add if not present
    */
   public static void updateAttributeEffect(LivingEntity livingEntity, Holder<Attribute> attribute, double value, AttributeModifier.Operation operation, Identifier identifier, boolean upsert){
      boolean hasMod = livingEntity.getAttributes().hasModifier(attribute, identifier);
      if(!hasMod){
         if(upsert) attributeEffect(livingEntity,attribute,value,operation,identifier,false);
         return;
      }
      double curMod = livingEntity.getAttributes().getModifierValue(attribute,identifier);
      if(curMod == value) return;
      HashMultimap<Holder<Attribute>, AttributeModifier> map = HashMultimap.create();
      map.put(attribute, new AttributeModifier(identifier, value, operation));
      livingEntity.getAttributes().removeAttributeModifiers(map);
      map.clear();
      map.put(attribute, new AttributeModifier(identifier, value, operation));
      livingEntity.getAttributes().addTransientAttributeModifiers(map);
   }
   
   /**
    * Logic for attempting to add an item stack to a container component.
    *
    * @param container existing container contents
    * @param size max slots
    * @param stack stack to add
    * @return a tuple containing updated contents and any remaining stack
    */
   public static Pair<ItemContainerContents, ItemStack> tryAddStackToContainerComp(ItemContainerContents container, int size, ItemStack stack){
      List<ItemStack> beltList = new ArrayList<>(container.allItemsCopyStream().toList());
      
      // Fill up existing slots first
      for(ItemStack existingStack : beltList){
         int curCount = stack.getCount();
         if(stack.isEmpty()) break;
         boolean canCombine = !existingStack.isEmpty()
               && ItemStack.isSameItemSameComponents(existingStack, stack)
               && existingStack.isStackable()
               && existingStack.getCount() < existingStack.getMaxStackSize();
         if(!canCombine) continue;
         int toAdd = Math.min(existingStack.getMaxStackSize() - existingStack.getCount(), curCount);
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
      return Pair.of(ItemContainerContents.fromItems(beltList), stack);
   }
   
   /**
    * Performs a comprehensive raycast that finds all entities hit by a beam, sorted by distance.
    *
    * <p>This method combines block raycasting with entity hitscan to find all living entities
    * in the path of a beam. It uses an iterative approach to find multiple hits and includes
    * a secondary check for entities that might be missed by the primary hitscan.
    *
    * <p>When {@code blockedByShields} is true, the beam can be blocked by players holding shields
    * who are facing the beam direction (dot product < -0.6).
    *
    * <h3>Features:</h3>
    * <ul>
    *   <li>Finds all entities hit, not just the first</li>
    *   <li>Sorted by distance from the entity parameter</li>
    *   <li>Optional shield blocking</li>
    *   <li>Automatic end-point adjustment when blocked</li>
    *   <li>Iteration limit to prevent infinite loops</li>
    * </ul>
    *
    * <h3>Example:</h3>
    * <pre>{@code
    * Vec3 start = player.getEyePosition();
    * Vec3 direction = player.getLookAngle();
    *
    * LasercastResult result = MinecraftUtils.lasercast(
    *     world, start, direction, 50.0, true, player);
    *
    * // Draw particles along the beam
    * ParticleUtils.drawLine(world, result.startPos(), result.endPos(), ParticleTypes.FLAME);
    *
    * // Damage all hits
    * for (Entity hit : result.sortedHits()) {
    *     if (hit instanceof LivingEntity living) {
    *         living.hurt(damageSource, 10.0f);
    *     }
    * }
    * }</pre>
    *
    * @param world the level to raycast in
    * @param startPos the starting position of the beam
    * @param direction the direction vector (should be normalized)
    * @param distance the maximum beam distance
    * @param blockedByShields whether shields can block the beam
    * @param entity the source entity (used for collision filtering and distance sorting)
    * @return result containing start/end positions, direction, and sorted list of hit entities
    * @see LasercastResult The result record containing all hit information
    */
   public static LasercastResult lasercast(Level world, Vec3 startPos, Vec3 direction, double distance, boolean blockedByShields, Entity entity){
      Vec3 rayEnd = startPos.add(direction.scale(distance));
      BlockHitResult raycast = world.clip(new ClipContext(startPos, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
      EntityHitResult entityHit;
      Set<Entity> hitSet = new HashSet<>();
      List<Entity> hits = new ArrayList<>();
      AABB box = new AABB(startPos, raycast.getLocation());
      box = box.inflate(2);
      // Primary hitscan check with iteration limit to prevent infinite loops
      // The loop finds entities one at a time (closest first) by excluding already-found entities
      int maxIterations = 1000;
      int iterations = 0;
      do{
         entityHit = ProjectileUtil.getEntityHitResult(entity, startPos, raycast.getLocation(), box, e -> e instanceof LivingEntity && !e.isSpectator() && !hitSet.contains(e), distance * 2);
         if(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY){
            Entity hitEntity = entityHit.getEntity();
            if(!hitSet.add(hitEntity)){
               LOGGER.warn("Lasercast duplicate entity detected despite filter - breaking to prevent infinite loop");
               break;
            }
            hits.add(hitEntity);
         }
         iterations++;
      }while(entityHit != null && entityHit.getType() == HitResult.Type.ENTITY && iterations < maxIterations);

      if(iterations >= maxIterations){
         LOGGER.warn("Lasercast hit iteration limit ({}) at pos {} direction {} - possible infinite loop prevented", maxIterations, startPos, direction);
      }

      // Secondary hitscan check to add lenience
      List<Entity> hits2 = world.getEntities(entity, box, (e) -> e instanceof LivingEntity && !e.isSpectator() && !hitSet.contains(e) && MathUtils.hitboxRaycast(e, startPos, raycast.getLocation()));
      hits.addAll(hits2);
      hitSet.addAll(hits2);
      hits.sort(Comparator.comparingDouble(e -> e.distanceTo(entity)));

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
               SoundUtils.playSound(world, hitPlayer.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
               endPoint = startPos.add(direction.normalize().scale(direction.normalize().dot(hitPlayer.position().subtract(startPos)))).subtract(direction.normalize());
            }
         }
         hits3.add(hit);
         if(blocked){
            break;
         }
      }

      return new LasercastResult(startPos, endPoint, direction, hits3);
   }

   /**
    * Result of a {@link #lasercast} operation containing all hit information.
    *
    * @param startPos the starting position of the beam
    * @param endPos the ending position (may be shortened if blocked by shield or block)
    * @param direction the beam direction vector
    * @param sortedHits list of all entities hit, sorted by distance from source
    */
   public record LasercastResult(Vec3 startPos, Vec3 endPos, Vec3 direction, List<Entity> sortedHits) {
   }

   /**
    * Retrieves or reconstructs a {@link ServerPlayer} from a {@link NameAndId} entry.
    *
    * <p>If the player is online, returns them directly. If offline, loads their data from
    * disk and reconstructs a temporary ServerPlayer instance. This is useful for commands
    * that need to work with offline players.
    *
    * <p><b>Warning:</b> The returned player instance for offline players should not be
    * modified or added to the world. It's primarily for reading data.
    *
    * @param server the server instance
    * @param playerEntry the player's name and ID
    * @return the online or reconstructed player instance
    */
   public static ServerPlayer getRequestedPlayer(MinecraftServer server, NameAndId playerEntry){
      ServerPlayer requestedPlayer = server.getPlayerList().getPlayerByName(playerEntry.name());

      if(requestedPlayer == null){
         requestedPlayer = new ServerPlayer(server, server.overworld(), new GameProfile(playerEntry.id(), playerEntry.name()), ClientInformation.createDefault());
         Optional<ValueInput> readViewOpt = server
               .getPlayerList()
               .loadPlayerData(playerEntry)
               .map(playerData -> TagValueInput.create(new ProblemReporter.ScopedCollector(LogUtils.getLogger()), server.registryAccess(), playerData));
         readViewOpt.ifPresent(requestedPlayer::load);

         if(readViewOpt.isPresent()){
            ValueInput readView = readViewOpt.get();
            Optional<String> dimension = readView.getString("Dimension");

            if(dimension.isPresent()){
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
