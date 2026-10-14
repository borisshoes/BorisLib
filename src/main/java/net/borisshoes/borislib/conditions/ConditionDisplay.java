package net.borisshoes.borislib.conditions;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.minecraft.core.Holder;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builds the player-facing text for an entity's conditions. Shared by the {@code /conditions} chat list
 * and {@link ConditionGui}.
 */
public final class ConditionDisplay {
   private static final Comparator<Holder<Condition>> TYPE_ORDER = Comparator.comparing(holder -> holder.value().getId());

   private ConditionDisplay(){
   }

   /**
    * @param entity the affected entity
    * @return every instance on the entity, ordered by condition id and then by instance id
    */
   public static List<ConditionInstance> sortedInstances(LivingEntity entity){
      List<ConditionInstance> sorted = new ArrayList<>();
      EntityConditions record = ((ConditionHolder) entity).borislib$getConditions();
      if(record == null) return sorted;
      for(Holder<Condition> holder : sortedTypes(record)){
         EntityConditions.TypeState state = record.getState(holder);
         if(state == null) continue;
         List<ConditionInstance> instances = new ArrayList<>(state.getInstances());
         instances.sort(Comparator.comparing(ConditionInstance::getId));
         sorted.addAll(instances);
      }
      return sorted;
   }

   /**
    * Icons are drawn from the client's item atlas, so they only render for players who have the server
    * resource pack; everyone else would see the missing-texture sprite.
    *
    * @param player the player who will see the text
    * @return {@code true} if condition icons should be included in text sent to that player
    */
   public static boolean showsIcons(ServerPlayer player){
      return PolymerResourcePackUtils.hasMainPack(player.connection);
   }

   /**
    * @param condition the condition type
    * @return the condition's {@link Condition#getIcon() icon} as an inline sprite, or an empty component
    *         if it has none
    */
   public static MutableComponent icon(Condition condition){
      GraphicalItem.GraphicElement icon = condition.getIcon();
      if(icon == null) return Component.empty();
      Identifier sprite = Identifier.fromNamespaceAndPath(icon.id().getNamespace(), "item/gui/" + icon.id().getPath());
      return Component.object(new AtlasSprite(AtlasIds.ITEMS, sprite));
   }

   /**
    * @param condition the condition type
    * @param text      the text to label
    * @param icons     whether to prepend the condition's icon
    * @return the text, preceded by the condition's icon and a space if {@code icons} is set and it has one
    */
   public static MutableComponent withIcon(Condition condition, Component text, boolean icons){
      if(!icons || condition.getIcon() == null) return text.copy();
      return Component.empty().append(icon(condition)).append(" ").append(text);
   }

   /**
    * @param entity the affected entity
    * @param icons  whether to prepend each condition's icon
    * @return one line per active condition giving its prevailing magnitude, ordered by condition id
    */
   public static List<Component> totals(LivingEntity entity, boolean icons){
      List<Component> lines = new ArrayList<>();
      EntityConditions record = ((ConditionHolder) entity).borislib$getConditions();
      if(record == null) return lines;
      for(Holder<Condition> holder : sortedTypes(record)){
         EntityConditions.TypeState state = record.getState(holder);
         if(state != null && state.isActive()) lines.add(total(holder.value(), state.getValue(), icons));
      }
      return lines;
   }

   /**
    * @param condition the condition type
    * @param value     its prevailing magnitude
    * @param icons     whether to prepend the condition's icon
    * @return the condition's name and magnitude, colored by its category
    */
   public static MutableComponent total(Condition condition, float value, boolean icons){
      float shown = Float.isInfinite(value) ? value : Math.round(value * 100.0f) / 100.0f;
      return withIcon(condition, condition.getNameWithValue(shown).withColor(categoryColor(condition.getCategory())), icons);
   }

   /**
    * @param entity the affected entity
    * @param icons  whether to prepend each condition's icon
    * @return the chat lines listing every condition total with its instances beneath it; empty if the
    *         entity has no conditions
    */
   public static List<Component> listLines(LivingEntity entity, boolean icons){
      List<Component> lines = new ArrayList<>();
      EntityConditions record = ((ConditionHolder) entity).borislib$getConditions();
      if(record == null) return lines;
      for(Holder<Condition> holder : sortedTypes(record)){
         EntityConditions.TypeState state = record.getState(holder);
         if(state == null) continue;
         lines.add(total(holder.value(), state.getValue(), icons));
         List<ConditionInstance> instances = new ArrayList<>(state.getInstances());
         instances.sort(Comparator.comparing(ConditionInstance::getId));
         for(ConditionInstance instance : instances){
            lines.add(Component.translatable("command.borislib.conditions.list.entry",
                  label(instance, TextColor.AQUA), value(instance).withColor(TextColor.WHITE), timeLeft(instance).withColor(TextColor.YELLOW)).withColor(TextColor.GRAY));
         }
      }
      return lines;
   }

   /**
    * @param instance the instance to label
    * @param color    the color used where the name does not set its own
    * @return the instance's display name, or its identifier if it has no name
    */
   public static MutableComponent label(ConditionInstance instance, TextColor color){
      Component name = instance.getName() != null ? instance.getName() : Component.literal(instance.getId().toString());
      return Component.empty().withColor(color).append(name);
   }

   /**
    * @param instance the instance to describe
    * @return the instance's signed value: a magnitude for {@code ADD_VALUE}, otherwise a percentage
    */
   public static MutableComponent value(ConditionInstance instance){
      float value = instance.getValue();
      return switch(instance.getOperation()){
         case ADD_VALUE -> Component.literal(signed(value));
         case ADD_MULTIPLIED_BASE -> Component.translatable("text.borislib.condition_value.multiplied_base", signed(value * 100.0f));
         case ADD_MULTIPLIED_TOTAL -> Component.translatable("text.borislib.condition_value.multiplied_total", signed(value * 100.0f));
      };
   }

   /**
    * @param instance the instance to describe
    * @return the time before the instance expires as {@code m:ss} or {@code h:mm:ss}, or {@code ∞}
    */
   public static MutableComponent timeLeft(ConditionInstance instance){
      return timeLeft(instance.getRemainingTicks());
   }

   /**
    * @param remaining ticks left, or {@link ConditionInstance#INFINITE_DURATION}
    * @return that time as {@code m:ss} or {@code h:mm:ss}, or {@code ∞}
    */
   public static MutableComponent timeLeft(int remaining){
      if(remaining == ConditionInstance.INFINITE_DURATION) return Component.literal("∞");
      int seconds = secondsLeft(remaining);
      if(seconds >= 3600) return Component.literal(String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60));
      return Component.literal(String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
   }

   /**
    * @param ticks a number of ticks
    * @return that many ticks in seconds, rounded up
    */
   public static int secondsLeft(int ticks){
      return (ticks + 19) / 20;
   }

   /**
    * @param category a condition category
    * @return green for beneficial, red for harmful and yellow for neutral
    */
   public static TextColor categoryColor(MobEffectCategory category){
      return switch(category){
         case BENEFICIAL -> TextColor.GREEN;
         case HARMFUL -> TextColor.RED;
         case NEUTRAL -> TextColor.YELLOW;
      };
   }

   private static List<Holder<Condition>> sortedTypes(EntityConditions record){
      List<Holder<Condition>> types = record.getTypes();
      types.sort(TYPE_ORDER);
      return types;
   }

   private static String signed(float value){
      if(value == Float.POSITIVE_INFINITY) return "+∞";
      String number = String.format(Locale.ROOT, "%.2f", value);
      number = number.replaceAll("\\.?0+$", "");
      if(number.equals("-0")) number = "0";
      return number.startsWith("-") ? number : "+" + number;
   }
}
