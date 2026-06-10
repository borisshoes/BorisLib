package net.borisshoes.borislib.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * A helper class for managing mod-specific custom data on {@link ItemStack} instances using
 * Minecraft 1.21's data component system.
 *
 * <p>This class provides type-safe accessors for storing and retrieving various data types
 * in a mod-namespaced tag within an item's {@link DataComponents#CUSTOM_DATA} component.
 * All data is stored under a single mod-specific key to avoid conflicts with other mods.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li><b>Namespacing</b> — Data is stored under a mod-specific key to prevent conflicts</li>
 *   <li><b>Type-safe getters/setters</b> — Convenience methods for primitives and NBT types</li>
 *   <li><b>Automatic cleanup</b> — Empty tags are removed automatically</li>
 *   <li><b>Null-safe</b> — Returns defaults instead of throwing exceptions</li>
 * </ul>
 *
 * <h3>Data Structure:</h3>
 * <pre>
 * ItemStack
 * └── CustomData component
 *     └── "mymod" (your modKey)
 *         ├── "property1": value
 *         ├── "property2": value
 *         └── ...
 * </pre>
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * // Create a handler for your mod
 * ItemModDataHandler data = new ItemModDataHandler("mymod");
 *
 * // Store data
 * ItemStack wand = new ItemStack(Items.STICK);
 * data.putProperty(wand, "charges", 100);
 * data.putProperty(wand, "owner", player.getName().getString());
 * data.putProperty(wand, "upgraded", false);
 *
 * // Read data
 * int charges = data.getIntProperty(wand, "charges"); // Returns 100
 * String owner = data.getStringProperty(wand, "owner");
 * boolean upgraded = data.getBooleanProperty(wand, "upgraded");
 *
 * // Check and remove
 * if (data.hasProperty(wand, "charges")) {
 *     data.removeProperty(wand, "charges");
 * }
 * }</pre>
 *
 * <h3>Migration from 1.20.x:</h3>
 * <p>In Minecraft 1.20.x and earlier, custom data was stored directly in ItemStack NBT.
 * In 1.21+, it uses the {@link DataComponents#CUSTOM_DATA} component. This class handles
 * the migration automatically.
 *
 * @see BorisLib#BORISLIB_ITEM_DATA The built-in handler for BorisLib's own data
 * @see CustomData Minecraft's custom data component
 * @see DataComponents Minecraft's data component registry
 */
public class ItemModDataHandler {
   
   private final String modKey;
   
   /**
    * Creates a new data handler that stores data under the specified mod key.
    *
    * <p>The key should typically be your mod ID to ensure uniqueness.
    *
    * @param modKey the namespace key for this mod's data (e.g., "mymod")
    */
   public ItemModDataHandler(String modKey){
      this.modKey = modKey;
   }
   
   /**
    * Gets the mod-namespaced compound tag containing all custom data.
    *
    * @param stack the item stack
    * @return the compound tag, or empty tag if no data exists
    */
   public CompoundTag getDataTag(ItemStack stack){
      if(stack == null) return new CompoundTag();
      CustomData nbtComponent = stack.get(DataComponents.CUSTOM_DATA);
      if(nbtComponent == null) return new CompoundTag();
      CompoundTag data = nbtComponent.copyTag();
      if(data.contains(modKey)){
         return data.getCompoundOrEmpty(modKey);
      }
      return new CompoundTag();
   }
   
   /**
    * Gets an integer property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the integer value, or 0 if not present
    */
   public int getIntProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? 0 : modTag.getIntOr(key, 0);
   }
   
   /**
    * Gets a string property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the string value, or empty string if not present
    */
   public String getStringProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? "" : modTag.getStringOr(key, "");
   }
   
   /**
    * Gets a boolean property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the boolean value, or {@code false} if not present
    */
   public boolean getBooleanProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag != null && modTag.contains(key) && modTag.getBooleanOr(key, false);
   }
   
   /**
    * Gets a double property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the double value, or 0.0 if not present
    */
   public double getDoubleProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? 0.0 : modTag.getDoubleOr(key, 0.0);
   }
   
   /**
    * Gets a float property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the float value, or 0.0f if not present
    */
   public float getFloatProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? 0.0f : modTag.getFloatOr(key, 0f);
   }
   
   /**
    * Gets a long property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the long value, or 0L if not present
    */
   public long getLongProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? 0 : modTag.getLongOr(key, 0L);
   }
   
   /**
    * Gets an NBT list property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the list tag, or empty list if not present
    */
   public ListTag getListProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? new ListTag() : modTag.getListOrEmpty(key);
   }
   
   /**
    * Gets an NBT compound property from the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return the compound tag, or empty compound if not present
    */
   public CompoundTag getCompoundProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag == null || !modTag.contains(key) ? new CompoundTag() : modTag.getCompoundOrEmpty(key);
   }
   
   /**
    * Stores an integer property in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the integer value to store
    */
   public void putProperty(ItemStack stack, String key, int property){
      putProperty(stack, key, IntTag.valueOf(property));
   }
   
   /**
    * Stores a boolean property in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the boolean value to store
    */
   public void putProperty(ItemStack stack, String key, boolean property){
      putProperty(stack, key, ByteTag.valueOf(property));
   }
   
   /**
    * Stores a double property in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the double value to store
    */
   public void putProperty(ItemStack stack, String key, double property){
      putProperty(stack, key, DoubleTag.valueOf(property));
   }
   
   /**
    * Stores a float property in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the float value to store
    */
   public void putProperty(ItemStack stack, String key, float property){
      putProperty(stack, key, FloatTag.valueOf(property));
   }
   
   /**
    * Stores a string property in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the string value to store
    */
   public void putProperty(ItemStack stack, String key, String property){
      putProperty(stack, key, StringTag.valueOf(property));
   }
   
   /**
    * Stores an arbitrary NBT tag in the item's data.
    *
    * <p>This is the base method that all other putProperty variants call.
    * It handles updating the custom data component and ensuring the mod tag exists.
    *
    * @param stack the item stack
    * @param key the property key
    * @param property the NBT tag to store (can be any tag type)
    */
   public void putProperty(ItemStack stack, String key, Tag property){
      CompoundTag modTag = getDataTag(stack);
      modTag.put(key, property);
      CustomData nbtComponent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      CompoundTag data = nbtComponent.copyTag();
      data.put(modKey, modTag);
      CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
   }
   
   /**
    * Checks if a property exists in the item's data.
    *
    * @param stack the item stack
    * @param key the property key
    * @return {@code true} if the property exists
    */
   public boolean hasProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag.contains(key);
   }
   
   /**
    * Removes a property from the item's data.
    *
    * <p>If removing this property causes the mod tag to become empty, the entire
    * mod key is removed from the custom data component for cleanliness.
    *
    * @param stack the item stack
    * @param key the property key
    * @return {@code true} if the property was present and removed, {@code false} if it didn't exist
    */
   public boolean removeProperty(ItemStack stack, String key){
      if(hasProperty(stack, key)){
         CompoundTag modTag = getDataTag(stack);
         modTag.remove(key);
         if(modTag.isEmpty()){
            CustomData nbtComponent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag data = nbtComponent.copyTag();
            data.remove(modKey);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
         }else{
            CustomData nbtComponent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag data = nbtComponent.copyTag();
            data.put(modKey, modTag);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
         }
         return true;
      }
      return false;
   }
}