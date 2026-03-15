package net.borisshoes.borislib.conditions;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public abstract class Condition {
   
   private final Identifier id;
   private final MobEffectCategory category;
   private final float base;
   private final float min;
   private final float max;
   private final boolean reversedImportance;
   
   protected Condition(Identifier id, MobEffectCategory category, float base, float min, float max){
      this(id, category, base, min, max, false);
   }
   
   protected Condition(Identifier id, MobEffectCategory category, float base, float min, float max, boolean reversed){
      this.id = id;
      this.category = category;
      this.base = base;
      this.min = min;
      this.max = max;
      this.reversedImportance = reversed;
   }
   
   public abstract void onTick(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   public abstract void onApply(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   public abstract void onRemove(MinecraftServer server, LivingEntity entity, float value, boolean particles);
   
   public Identifier getId(){
      return id;
   }
   
   public MobEffectCategory getCategory(){
      return category;
   }
   
   public float getBase(){
      return base;
   }
   
   public float getMin(){
      return min;
   }
   
   public float getMax(){
      return max;
   }
   
   public boolean isReversedImportance(){
      return reversedImportance;
   }
   
   public boolean isBeneficial(){
      return this.category == MobEffectCategory.BENEFICIAL;
   }
   
   public boolean isHarmful(){
      return this.category == MobEffectCategory.HARMFUL;
   }
   
   public boolean isNeutral(){
      return this.category == MobEffectCategory.NEUTRAL;
   }
   
   public String getTranslationKey(){
      return "condition." + id.getNamespace() + "." + id.getPath() + ".name";
   }
   
   public MutableComponent getName(){
      return Component.translatable(getTranslationKey());
   }
   
   public MutableComponent getNameWithValue(float value){
      return Component.translatable("condition." + id.getNamespace() + "." + id.getPath() + ".name_value", value);
   }
   
   @Override
   public boolean equals(Object o){
      if(this == o) return true;
      if(!(o instanceof Condition condition)) return false;
      return id.equals(condition.id);
   }
   
   @Override
   public int hashCode(){
      return id.hashCode();
   }
   
   public float sanitizeValue(float d){
      return Float.isNaN(d) ? this.base : Mth.clamp(d, this.min, this.max);
   }
}
