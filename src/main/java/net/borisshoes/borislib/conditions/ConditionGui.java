package net.borisshoes.borislib.conditions;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiHelper;
import net.borisshoes.borislib.gui.PagedGui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * A paged view of the player's own condition instances, with their player head at the top summarizing
 * the prevailing magnitude of each active condition. The view refreshes itself once a second while open.
 */
public class ConditionGui extends PagedGui<ConditionInstance> {
   private static final int REFRESH_INTERVAL = 20;
   private static final int BORDER_COLOR = 0x4a3b6b;
   private static final int PAGE_COLOR = 0xd9c682;

   private final int headInd;
   private int ticks;

   /**
    * @param player the player whose conditions are shown, and who will see the GUI
    */
   public ConditionGui(ServerPlayer player){
      super(MenuType.GENERIC_9x6, player, ConditionDisplay.sortedInstances(player));
      this.headInd = width / 2;
      setTitle(Component.translatable("gui.borislib.conditions.title"));
      blankItem(GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.PAGE_BG, PAGE_COLOR)).hideTooltip());
      itemElemBuilder((instance, index) -> buildInstanceItem(instance));
      GuiHelper.outlineGUI(this, BORDER_COLOR, Component.empty());
      buildPage();
   }

   @Override
   public void buildPage(){
      super.buildPage();
      setSlot(headInd, buildTotalsItem());
   }

   @Override
   public void onTick(){
      super.onTick();
      if(++ticks % REFRESH_INTERVAL != 0) return;
      int pagesBefore = numPages();
      items(ConditionDisplay.sortedInstances(getPlayer()));
      buildPage();
      // The page buttons are only drawn when there are several pages, so repaint the border over them
      if(pagesBefore > 1 && numPages() == 1){
         GuiHelper.outlineGUI(this, BORDER_COLOR, Component.empty());
         buildPage();
      }
   }

   private GuiElementBuilder buildTotalsItem(){
      GuiElementBuilder head = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(getPlayer().getGameProfile()).hideDefaultTooltip();
      head.setName(Component.translatable("gui.borislib.conditions.totals").withColor(TextColor.GOLD));
      List<Component> totals = ConditionDisplay.totals(getPlayer(), ConditionDisplay.showsIcons(getPlayer()));
      if(totals.isEmpty()){
         head.addLoreLine(Component.translatable("gui.borislib.conditions.none").withColor(TextColor.GRAY));
      }
      for(Component total : totals){
         head.addLoreLine(total);
      }
      return head;
   }

   private GuiElementBuilder buildInstanceItem(ConditionInstance instance){
      Condition condition = instance.getCondition().value();
      GuiElementBuilder item = icon(condition).hideDefaultTooltip();
      item.setName(ConditionDisplay.label(instance, ConditionDisplay.categoryColor(condition.getCategory())));
      item.addLoreLine(line("gui.borislib.conditions.condition", ConditionDisplay.withIcon(condition,
            condition.getName().withColor(ConditionDisplay.categoryColor(condition.getCategory())), ConditionDisplay.showsIcons(getPlayer()))));
      item.addLoreLine(line("gui.borislib.conditions.value", ConditionDisplay.value(instance).withColor(TextColor.WHITE)));
      item.addLoreLine(line("gui.borislib.conditions.time", ConditionDisplay.timeLeft(instance).withColor(TextColor.YELLOW)));
      if(!instance.isStacking()) item.addLoreLine(Component.translatable("gui.borislib.conditions.non_stacking").withColor(TextColor.LIGHT_PURPLE));
      if(instance.isPersistent()) item.addLoreLine(Component.translatable("gui.borislib.conditions.persistent").withColor(TextColor.LIGHT_PURPLE));
      item.addLoreLine(line("gui.borislib.conditions.source", Component.literal(instance.getId().toString())).withColor(TextColor.DARK_GRAY));

      // The stack size counts down the last 99 seconds
      int remaining = instance.getRemainingTicks();
      if(remaining != ConditionInstance.INFINITE_DURATION){
         item.setMaxCount(99).setCount(Math.clamp(ConditionDisplay.secondsLeft(remaining), 1, 99));
      }
      return item;
   }

   private static MutableComponent line(String key, Component value){
      return Component.translatable(key, value).withColor(TextColor.GRAY);
   }

   // Conditions without an icon fall back to a dye in their category's color
   private static GuiElementBuilder icon(Condition condition){
      if(condition.getIcon() != null) return GuiElementBuilder.from(GraphicalItem.with(condition.getIcon()));
      return new GuiElementBuilder(switch(condition.getCategory()){
         case BENEFICIAL -> Items.DYE.lime();
         case HARMFUL -> Items.DYE.red();
         case NEUTRAL -> Items.DYE.yellow();
      });
   }
}
