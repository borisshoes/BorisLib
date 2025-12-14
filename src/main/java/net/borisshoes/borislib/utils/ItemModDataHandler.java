package net.borisshoes.borislib.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ItemModDataHandler {
   
   private final String modKey;
   
   public ItemModDataHandler(String modKey){
      this.modKey = modKey;
   }
   
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
   
   public int getIntProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0 : modTag.getIntOr(key,0);
   }
   
   public String getStringProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? "" : modTag.getStringOr(key,"");
   }
   
   public boolean getBooleanProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag != null && modTag.contains(key) && modTag.getBooleanOr(key, false);
   }
   
   public double getDoubleProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0.0 : modTag.getDoubleOr(key,0.0);
   }
   
   public float getFloatProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0.0f : modTag.getFloatOr(key, 0f);
   }
   
   public long getLongProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0 : modTag.getLongOr(key, 0L);
   }
   
   public ListTag getListProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? new ListTag() : modTag.getListOrEmpty(key);
   }
   
   public CompoundTag getCompoundProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? new CompoundTag() : modTag.getCompoundOrEmpty(key);
   }
   
   public void putProperty(ItemStack stack, String key, int property){
      putProperty(stack, key, IntTag.valueOf(property));
   }
   
   public void putProperty(ItemStack stack, String key, boolean property){
      putProperty(stack, key, ByteTag.valueOf(property));
   }
   
   public void putProperty(ItemStack stack, String key, double property){
      putProperty(stack,key, DoubleTag.valueOf(property));
   }
   
   public void putProperty(ItemStack stack, String key, float property){
      putProperty(stack,key, FloatTag.valueOf(property));
   }
   
   public void putProperty(ItemStack stack, String key, String property){
      putProperty(stack,key, StringTag.valueOf(property));
   }
   
   public void putProperty(ItemStack stack, String key, Tag property){
      CompoundTag modTag = getDataTag(stack);
      modTag.put(key,property);
      CustomData nbtComponent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
      CompoundTag data = nbtComponent.copyTag();
      data.put(modKey,modTag);
      CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
   }
   
   public boolean hasProperty(ItemStack stack, String key){
      CompoundTag modTag = getDataTag(stack);
      return modTag.contains(key);
   }
   
   public boolean removeProperty(ItemStack stack, String key){
      if(hasProperty(stack,key)){
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
            data.put(modKey,modTag);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, data);
         }
         return true;
      }
      return false;
   }
}