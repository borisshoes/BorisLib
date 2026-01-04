package net.borisshoes.borislib.testmod;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiFilter;
import net.borisshoes.borislib.gui.GuiSort;
import net.borisshoes.borislib.gui.PagedGui;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("unchecked")
public class TestGui extends PagedGui<Item> {
   
   private static boolean hasRegistered = false;
   private final int pageColor = 0xd9c682;
   
   public TestGui(ServerPlayer player){
      super(MenuType.GENERIC_9x6, player, BuiltInRegistries.ITEM.stream().toList());
      action1TextColor(ChatFormatting.RED.getColor().intValue());
      action2TextColor(ChatFormatting.GOLD.getColor().intValue());
      action3TextColor(ChatFormatting.YELLOW.getColor().intValue());
      
      itemElemBuilder((item, index) -> {
         GuiElementBuilder builder = GuiElementBuilder.from(item.getDefaultInstance());
         builder.setName(Component.translatable(item.getDescriptionId()).withColor(0x00ff00));
         builder.addLoreLine(Component.literal("Hi there!"));
         return builder;
      });
      
      blankItem(GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.PAGE_BG,pageColor)));
      
      elemClickFunction((item, index, clickType) -> {
         if(clickType.isRight){
            MinecraftUtils.removeItems(player,item,1);
         }else{
            player.handleExtraItemsCreatedOnUse(new ItemStack(item));
         }
      });
      
      curSort(ItemSort.RECOMMENDED);
      curFilter(ItemFilter.NONE);
      
      if(!hasRegistered){
         RandomSource random = new LegacyRandomSource(0L);
         for(CreativeModeTab itemGroup : BuiltInRegistries.CREATIVE_MODE_TAB){
            itemGroup.buildContents(new CreativeModeTab.ItemDisplayParameters(player.level().enabledFeatures(), true, player.registryAccess()));
            if(itemGroup.hasAnyItems()){
               random.setSeed(itemGroup.hashCode());
               new ItemFilter(itemGroup.getDisplayName().getString(),random.nextInt(0xffffff),item -> itemGroup.contains(item.getDefaultInstance()));
            }
         }
         hasRegistered = true;
      }
   }
   
   private static class ItemFilter extends GuiFilter<Item> {
      public static final List<ItemFilter> FILTERS = new ArrayList<>();
      
      public static final ItemFilter NONE = new ItemFilter("gui.borislib.none", ChatFormatting.WHITE.getColor().intValue(), entry -> true);
      
      private ItemFilter(String key, int color, Predicate<Item> predicate){
         super(key, color, predicate);
         FILTERS.add(this);
      }
      
      @Override
      protected List<ItemFilter> getList(){
         return FILTERS;
      }
      
      public ItemFilter getStaticDefault(){
         return NONE;
      }
   }
   
   private static class ItemSort extends GuiSort<Item> {
      public static final List<ItemSort> SORTS = new ArrayList<>();
      
      public static final ItemSort RECOMMENDED = new ItemSort("gui.borislib.recommended", ChatFormatting.LIGHT_PURPLE.getColor().intValue(),
            Comparator.comparingInt(entry -> BuiltInRegistries.ITEM.asHolderIdMap().getId(BuiltInRegistries.ITEM.wrapAsHolder(entry))));
      public static final ItemSort ALPHABETICAL = new ItemSort("gui.borislib.alphabetical", ChatFormatting.AQUA.getColor().intValue(),
            Comparator.comparing(Item::getDescriptionId));
      public static final ItemSort RARITY = new ItemSort("gui.borislib.rarity", ChatFormatting.GREEN.getColor().intValue(),
            Comparator.comparingInt((Item entry) -> entry.getDefaultInstance().getRarity().ordinal()).thenComparing(Item::getDescriptionId));
      
      private ItemSort(String key, int color, Comparator<Item> comparator){
         super(key, color, comparator);
         SORTS.add(this);
      }
      
      @Override
      protected List<ItemSort> getList(){
         return SORTS;
      }
      
      public ItemSort getStaticDefault(){
         return RECOMMENDED;
      }
   }
}
