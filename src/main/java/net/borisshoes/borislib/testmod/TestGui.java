package net.borisshoes.borislib.testmod;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiFilter;
import net.borisshoes.borislib.gui.GuiSort;
import net.borisshoes.borislib.gui.PagedGui;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("unchecked")
public class TestGui extends PagedGui<Item> {
   
   private static boolean hasRegistered = false;
   private final int pageColor = 0xd9c682;
   
   public TestGui(ServerPlayerEntity player){
      super(ScreenHandlerType.GENERIC_9X6, player, Registries.ITEM.stream().toList());
      action1TextColor(Formatting.RED.getColorValue().intValue());
      action2TextColor(Formatting.GOLD.getColorValue().intValue());
      action3TextColor(Formatting.YELLOW.getColorValue().intValue());
      
      itemElemBuilder((item) -> {
         GuiElementBuilder builder = GuiElementBuilder.from(item.getDefaultStack());
         builder.setName(Text.translatable(item.getTranslationKey()).withColor(0x00ff00));
         builder.addLoreLine(Text.literal("Hi there!"));
         return builder;
      });
      
      blankItem(GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.PAGE_BG,pageColor)));
      
      elemClickFunction((item, index, clickType) -> {
         if(clickType.isRight){
            MinecraftUtils.removeItems(player,item,1);
         }else{
            player.giveOrDropStack(new ItemStack(item));
         }
      });
      
      curSort(ItemSort.RECOMMENDED);
      curFilter(ItemFilter.NONE);
      
      if(!hasRegistered){
         Random random = new CheckedRandom(0L);
         for(ItemGroup itemGroup : Registries.ITEM_GROUP){
            itemGroup.updateEntries(new ItemGroup.DisplayContext(player.getEntityWorld().getEnabledFeatures(), true, player.getRegistryManager()));
            if(itemGroup.hasStacks()){
               random.setSeed(itemGroup.hashCode());
               new ItemFilter(itemGroup.getDisplayName().getString(),random.nextInt(0xffffff),item -> itemGroup.contains(item.getDefaultStack()));
            }
         }
         hasRegistered = true;
      }
   }
   
   private static class ItemFilter extends GuiFilter<Item> {
      public static final List<ItemFilter> FILTERS = new ArrayList<>();
      
      public static final ItemFilter NONE = new ItemFilter("gui.borislib.none", Formatting.WHITE.getColorValue().intValue(), entry -> true);
      
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
      
      public static final ItemSort RECOMMENDED = new ItemSort("gui.borislib.recommended", Formatting.LIGHT_PURPLE.getColorValue().intValue(),
            Comparator.comparingInt(entry -> Registries.ITEM.getIndexedEntries().getRawId(Registries.ITEM.getEntry(entry))));
      public static final ItemSort ALPHABETICAL = new ItemSort("gui.borislib.alphabetical", Formatting.AQUA.getColorValue().intValue(),
            Comparator.comparing(Item::getTranslationKey));
      public static final ItemSort RARITY = new ItemSort("gui.borislib.rarity", Formatting.GREEN.getColorValue().intValue(),
            Comparator.comparingInt((Item entry) -> entry.getDefaultStack().getRarity().ordinal()).thenComparing(Item::getTranslationKey));
      
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
