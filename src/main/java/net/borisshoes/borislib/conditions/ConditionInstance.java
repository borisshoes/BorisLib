package net.borisshoes.borislib.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.borisshoes.borislib.BorisLib;
import net.borisshoes.borislib.utils.CodecUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

public class ConditionInstance {
   
   public static final Codec<ConditionInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
         BorisLib.CONDITIONS.holderByNameCodec().fieldOf("condition").forGetter(ConditionInstance::getCondition),
         Identifier.CODEC.fieldOf("id").forGetter(ConditionInstance::getId),
         Codec.INT.fieldOf("duration").forGetter(ConditionInstance::getDuration),
         Codec.FLOAT.fieldOf("value").forGetter(ConditionInstance::getValue),
         Codec.BOOL.fieldOf("stacking").forGetter(ConditionInstance::isStacking),
         Codec.BOOL.fieldOf("particles").forGetter(ConditionInstance::hasParticles),
         AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(ConditionInstance::getOperation),
         Codec.INT.fieldOf("timer").forGetter(ConditionInstance::getTimer),
         Codec.BOOL.fieldOf("persistent").forGetter(ConditionInstance::isPersistent),
         CodecUtils.UUID_CODEC.optionalFieldOf("inflictedBy").forGetter(inst -> java.util.Optional.ofNullable(inst.getInflictedBy()))
   ).apply(i, (condition, id, duration, value, stacking, particles, operation, timer, persistent, inflictedBy) -> {
      ConditionInstance inst = new ConditionInstance(condition, id, duration, value, stacking, particles, persistent, operation, inflictedBy.orElse(null));
      inst.timer = timer;
      return inst;
   }));
   
   private final Holder<Condition> condition;
   private final Identifier id;
   private final float value;
   private final boolean stacking;
   private final boolean particles;
   private final AttributeModifier.Operation operation;
   private final int duration;
   private final boolean persistent;
   private final UUID inflictedBy;
   private int timer;
   
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value){
      this(condition, id, duration, value, true, true, false, AttributeModifier.Operation.ADD_VALUE, null);
   }
   
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, AttributeModifier.Operation operation){
      this(condition, id, duration, value, true, true, false, operation, null);
   }
   
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, AttributeModifier.Operation operation){
      this(condition, id, duration, value, stacking, particles, false, operation, null);
   }
   
   public ConditionInstance(Holder<Condition> condition, Identifier id, int duration, float value, boolean stacking, boolean particles, boolean persistent, AttributeModifier.Operation operation, UUID inflictedBy){
      this.condition = condition;
      this.id = id;
      this.duration = duration;
      this.value = value;
      this.stacking = stacking;
      this.particles = particles;
      this.operation = operation;
      this.timer = 0;
      this.persistent = persistent;
      this.inflictedBy = inflictedBy;
   }
   
   public boolean tick(){
      return ++this.timer >= this.duration;
   }
   
   public Holder<Condition> getCondition(){
      return condition;
   }
   
   public Identifier getId(){
      return id;
   }
   
   public float getValue(){
      return value;
   }
   
   public boolean isStacking(){
      return stacking;
   }
   
   public boolean hasParticles(){
      return particles;
   }
   
   public AttributeModifier.Operation getOperation(){
      return operation;
   }
   
   public int getDuration(){
      return duration;
   }
   
   public int getTimer(){
      return timer;
   }
   
   public void setTimer(int timer){
      this.timer = timer;
   }
   
   public boolean isPersistent(){
      return persistent;
   }
   
   public UUID getInflictedBy(){
      return inflictedBy;
   }
}
