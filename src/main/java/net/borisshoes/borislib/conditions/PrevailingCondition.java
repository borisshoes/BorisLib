package net.borisshoes.borislib.conditions;

/**
 * The combined state of every {@link ConditionInstance} of one {@link Condition} type on one entity.
 *
 * @param value        the prevailing magnitude; {@code 0} means the condition is not in effect
 * @param fromStacking {@code true} if the value comes from the stacked instances, {@code false} if a
 *                     non-stacking instance won
 */
public record PrevailingCondition(float value, boolean fromStacking) {
   /** The state of a condition that is not in effect. */
   public static final PrevailingCondition NONE = new PrevailingCondition(0.0f, true);

   /** @return {@code true} if the prevailing magnitude is greater than 0. */
   public boolean isActive(){
      return value > 0.0f;
   }
}
