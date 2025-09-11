package net.borisshoes.borislib.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

public class ItemModDataHandler {
   
   private final String modKey;
   
   public ItemModDataHandler(String modKey){
      this.modKey = modKey;
   }
   
   public NbtCompound getDataTag(ItemStack stack){
      if(stack == null) return new NbtCompound();
      NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
      if(nbtComponent == null) return new NbtCompound();
      NbtCompound data = nbtComponent.copyNbt();
      if(data.contains(modKey)){
         return data.getCompound(modKey).orElse(new NbtCompound());
      }
      return new NbtCompound();
   }
   
   public int getIntProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0 : modTag.getInt(key,0);
   }
   
   public String getStringProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? "" : modTag.getString(key,"");
   }
   
   public boolean getBooleanProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return modTag != null && modTag.contains(key) && modTag.getBoolean(key, false);
   }
   
   public double getDoubleProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0.0 : modTag.getDouble(key,0.0);
   }
   
   public float getFloatProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0.0f : modTag.getFloat(key, 0f);
   }
   
   public long getLongProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? 0 : modTag.getLong(key, 0L);
   }
   
   public NbtList getListProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? new NbtList() : modTag.getList(key).orElse(new NbtList());
   }
   
   public NbtCompound getCompoundProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return  modTag == null || !modTag.contains(key) ? new NbtCompound() : modTag.getCompound(key).orElse(new NbtCompound());
   }
   
   public void putProperty(ItemStack stack, String key, int property){
      putProperty(stack, key, NbtInt.of(property));
   }
   
   public void putProperty(ItemStack stack, String key, boolean property){
      putProperty(stack, key, NbtByte.of(property));
   }
   
   public void putProperty(ItemStack stack, String key, double property){
      putProperty(stack,key, NbtDouble.of(property));
   }
   
   public void putProperty(ItemStack stack, String key, float property){
      putProperty(stack,key,NbtFloat.of(property));
   }
   
   public void putProperty(ItemStack stack, String key, String property){
      putProperty(stack,key,NbtString.of(property));
   }
   
   public void putProperty(ItemStack stack, String key, NbtElement property){
      NbtCompound modTag = getDataTag(stack);
      modTag.put(key,property);
      NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
      NbtCompound data = nbtComponent.copyNbt();
      data.put(modKey,modTag);
      NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
   }
   
   public boolean hasProperty(ItemStack stack, String key){
      NbtCompound modTag = getDataTag(stack);
      return modTag.contains(key);
   }
   
   public boolean removeProperty(ItemStack stack, String key){
      if(hasProperty(stack,key)){
         NbtCompound modTag = getDataTag(stack);
         modTag.remove(key);
         if(modTag.isEmpty()){
            NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound data = nbtComponent.copyNbt();
            data.remove(modKey);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
         }else{
            NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound data = nbtComponent.copyNbt();
            data.put(modKey,modTag);
            NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data);
         }
         return true;
      }
      return false;
   }
}