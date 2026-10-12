package net.borisshoes.borislib.conditions;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Base class for {@link Condition}s whose whole effect is a transient modifier on one or more entity
 * attributes.
 *
 * <p>The modifier is written when the condition activates, changes strength or is reattached to a loaded
 * entity, and removed when it deactivates. Nothing happens per tick. The modifier id on every attribute is
 * the condition's own {@link #getId() id}.</p>
 *
 * <p>{@link #canApply(LivingEntity)} rejects entities that lack any of the listed attributes, so adding
 * the condition to such an entity is cleanly {@link Conditions.AddResult#REJECTED}.</p>
 */
public abstract class AttributeCondition extends Condition {

   /** How a magnitude {@code m} becomes an attribute modifier. */
   public enum Curve {
      /** {@code ADD_MULTIPLIED_BASE} with value {@code m}: adds {@code m ×} the base value. */
      AMPLIFY_BASE(AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
      /** {@code ADD_MULTIPLIED_TOTAL} with value {@code attenuate(m) - 1}: scales the total by {@code 1 / (1 + m)}, down to 0 at +∞. */
      ATTENUATE_TOTAL(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
      /** {@code ADD_VALUE} with value {@code m / (1 + m)}, reaching 1 at +∞; for resistance attributes in the 0..1 range. */
      RESISTANCE(AttributeModifier.Operation.ADD_VALUE),
      /** {@code ADD_MULTIPLIED_TOTAL} with value {@code -m}: scales the total by {@code 1 - m}, which can cross zero. */
      LINEAR_TOTAL(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

      private final AttributeModifier.Operation operation;

      Curve(AttributeModifier.Operation operation){
         this.operation = operation;
      }

      /** @return the attribute modifier operation this curve uses. */
      public AttributeModifier.Operation operation(){
         return operation;
      }

      /**
       * @param m the magnitude
       * @return the attribute modifier value this curve gives that magnitude
       */
      public double value(float m){
         return switch(this){
            case AMPLIFY_BASE -> m;
            case ATTENUATE_TOTAL -> ConditionMath.attenuate(m) - 1.0;
            case RESISTANCE -> 1.0 - ConditionMath.attenuate(m);
            case LINEAR_TOTAL -> -m;
         };
      }
   }

   private final List<Holder<Attribute>> attributes;
   private final Curve curve;

   /**
    * @param id         unique namespaced identifier for this condition type, also used as the modifier id
    * @param category   beneficial / harmful / neutral classification
    * @param cap        the largest permitted magnitude; {@link Float#POSITIVE_INFINITY} opts in to infinity
    * @param attributes the attributes this condition modifies
    * @param curve      how a magnitude maps onto the modifier
    */
   protected AttributeCondition(Identifier id, MobEffectCategory category, float cap, List<Holder<Attribute>> attributes, Curve curve){
      super(id, category, cap);
      this.attributes = List.copyOf(attributes);
      this.curve = curve;
   }

   /** @return the attributes this condition modifies. */
   public List<Holder<Attribute>> getAttributes(){
      return attributes;
   }

   /** @return the curve that maps this condition's magnitude onto its modifier. */
   public Curve getCurve(){
      return curve;
   }

   /** @return the scale matching this condition's {@link Curve}, so percentages mean percent of the attribute. */
   @Override
   public ConditionMath.Scale getScale(){
      return switch(curve){
         case AMPLIFY_BASE -> ConditionMath.Scale.AMPLIFY;
         case ATTENUATE_TOTAL, RESISTANCE -> ConditionMath.Scale.ATTENUATE;
         case LINEAR_TOTAL -> ConditionMath.Scale.LINEAR;
      };
   }

   /**
    * The modifier value for a magnitude. Defaults to the {@link Curve}; override for a custom mapping.
    *
    * @param m the prevailing magnitude
    * @return the attribute modifier value
    */
   protected double modifierValue(float m){
      return curve.value(m);
   }

   /** @return the modifier operation. Defaults to the {@link Curve}'s operation. */
   protected AttributeModifier.Operation modifierOperation(){
      return curve.operation();
   }

   /**
    * Writes this condition's modifier onto every listed attribute. Called on activation, on change and on
    * entity load; override together with {@link #removeModifiers(LivingEntity)} to manage extra modifiers.
    *
    * @param entity the affected entity
    * @param value  the prevailing magnitude
    */
   protected void applyModifiers(LivingEntity entity, float value){
      AttributeModifier modifier = new AttributeModifier(getId(), modifierValue(value), modifierOperation());
      for(int i = 0; i < attributes.size(); i++){
         AttributeInstance instance = entity.getAttribute(attributes.get(i));
         if(instance != null) instance.addOrUpdateTransientModifier(modifier);
      }
   }

   /**
    * Removes this condition's modifier from every listed attribute.
    *
    * @param entity the affected entity
    */
   protected void removeModifiers(LivingEntity entity){
      for(int i = 0; i < attributes.size(); i++){
         AttributeInstance instance = entity.getAttribute(attributes.get(i));
         if(instance != null) instance.removeModifier(getId());
      }
   }

   /** @return {@code true} only if the entity has every attribute this condition modifies. */
   @Override
   public boolean canApply(LivingEntity entity){
      for(int i = 0; i < attributes.size(); i++){
         if(!entity.getAttributes().hasAttribute(attributes.get(i))) return false;
      }
      return true;
   }

   @Override
   public void onActivate(MinecraftServer server, LivingEntity entity, float value){
      applyModifiers(entity, value);
   }

   @Override
   public void onChange(MinecraftServer server, LivingEntity entity, float oldValue, float newValue){
      applyModifiers(entity, newValue);
   }

   @Override
   public void onEntityLoad(MinecraftServer server, LivingEntity entity, float value){
      applyModifiers(entity, value);
   }

   @Override
   public void onDeactivate(MinecraftServer server, LivingEntity entity, float lastValue){
      removeModifiers(entity);
   }

   /** Attribute conditions do nothing per tick. */
   @Override
   public void onTick(MinecraftServer server, LivingEntity entity, float value){
   }
}
