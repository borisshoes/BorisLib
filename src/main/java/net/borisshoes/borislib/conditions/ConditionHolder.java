package net.borisshoes.borislib.conditions;

/**
 * Duck interface mixed into {@link net.minecraft.world.entity.LivingEntity} that binds an entity to its
 * {@link EntityConditions} record, giving condition reads an O(1) path with no map lookup.
 *
 * <p>Managed by {@link Conditions}; other code should go through the {@link Conditions} API.</p>
 */
public interface ConditionHolder {
   /** @return the record bound to this entity, or {@code null} if it has no conditions. */
   EntityConditions borislib$getConditions();

   /**
    * Binds or unbinds this entity's record.
    *
    * @param conditions the record to bind, or {@code null} for none
    */
   void borislib$setConditions(EntityConditions conditions);
}
