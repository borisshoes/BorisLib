package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class PagedGui<T> extends PagedGuiBase {
   
   private GuiSort<T> curSort;
   private GuiFilter<T> curFilter;
   private List<T> itemList;
   private List<T> filteredSortedList;
   
   private TriConsumer<T, Integer, ClickType> elemClickFunction = (item, index, clickType) -> {};
   private Function<T, GuiElementBuilder> itemElemBuilder = (item -> new GuiElementBuilder(ItemStack.EMPTY));
   
   public PagedGui(MenuType<?> type, ServerPlayer player, List<T> items){
      super(type, player, false);
      this.itemList = items;
      
      this.pageUpFunction = (clickType -> {
         if(pageNum < numPages()){
            pageNum++;
            buildPage();
         }
      });
      this.pageDownFunction = (clickType -> {
         if(pageNum > 1){
            pageNum--;
            buildPage();
         }
      });
      this.cycleSortFunction = (clickType -> {
         if(curSort != null){
            if(clickType.isLeft && clickType.shift){
               this.curSort = this.curSort.getStaticDefault();
            }else{
               this.curSort = curSort.cycle(curSort,clickType.isRight);
            }
            buildPage();
         }
      });
      this.cycleFilterFunction = (clickType -> {
         if(curFilter != null){
            if(clickType.isLeft && clickType.shift){
               this.curFilter = this.curFilter.getStaticDefault();
            }else{
               this.curFilter = curFilter.cycle(curFilter,clickType.isRight);
            }
            buildPage();
         }
      });
   }
   
   public int numPages(){
      return Math.max(1,(int) (Math.ceil((float)filteredSortedList.size()/(this.paneWidth*this.paneHeight))));
   }
   
   public void buildPage(){
      updateFilteredSorted();
      int numPages = numPages();
      pageNum = Math.clamp(pageNum,1,Math.max(1,numPages));
      List<T> pageItems = AlgoUtils.listToPage(filteredSortedList,pageNum,pageSize());
      
      if(sortInd >= 0 && curSort != null) setSlot(sortInd,createSortItem());
      if(filterInd >= 0 && curFilter != null) setSlot(filterInd,createFilterItem());
      if(nextInd >= 0 && numPages > 1) setSlot(nextInd,createNextPageItem());
      if(prevInd >= 0 && numPages > 1) setSlot(prevInd,createPrevPageItem());
      
      int pageIndex = 0;
      for(int paneY = 0; paneY < paneHeight; paneY++){
         for(int paneX = 0; paneX < paneWidth; paneX++){
            int guiIndex = paneStartInd + (pageIndex / paneWidth) * width + (pageIndex % paneWidth);
            if(pageIndex < pageItems.size()){
               T item = pageItems.get(pageIndex);
               GuiElementBuilder builder = itemElemBuilder.apply(item);
               final int finalPageIndex = pageIndex;
               builder.setCallback(clickType -> elemClickFunction.accept(item, finalPageIndex, clickType));
               setSlot(guiIndex, builder);
            }else{
               setSlot(guiIndex,blankItem);
            }
            pageIndex++;
         }
      }
   }
   
   protected void updateFilteredSorted(){
      Stream<T> itemStream = itemList.stream();
      if(curFilter != null) itemStream = itemStream.filter(curFilter.getPredicate());
      if(curSort != null) itemStream = itemStream.sorted(curSort.getComparator());
      this.filteredSortedList = itemStream.toList();
   }
   
   protected GuiElementBuilder createSortItem(){
      GuiElementBuilder sortBuilt = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.SORT)).hideDefaultTooltip();
      sortBuilt.setName(Component.translatable("gui.borislib.sort").withColor(primaryTextColor));
      sortBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.change_sort").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.right_click").withColor(action2TextColor),
            Component.translatable("gui.borislib.change_sort_back").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.shift_click").withColor(action3TextColor),
            Component.translatable("gui.borislib.reset_sort").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Component.literal(""));
      sortBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.sorting_by").withColor(secondaryTextColor),
            curSort.getColoredLabel()));
      sortBuilt.setCallback(this.cycleSortFunction);
      return sortBuilt;
   }
   
   protected GuiElementBuilder createFilterItem(){
      GuiElementBuilder filterBuilt = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.FILTER)).hideDefaultTooltip();
      filterBuilt.setName(Component.translatable("gui.borislib.filter").withColor(primaryTextColor));
      filterBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.change_filter").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.right_click").withColor(action2TextColor),
            Component.translatable("gui.borislib.change_filter_back").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.shift_click").withColor(action3TextColor),
            Component.translatable("gui.borislib.reset_filter").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Component.literal(""));
      filterBuilt.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.filtering_by").withColor(secondaryTextColor),
            curFilter.getColoredLabel()));
      filterBuilt.setCallback(this.cycleFilterFunction);
      return filterBuilt;
   }
   
   public PagedGui<T> items(List<T> items){
      this.itemList = items;
      return this;
   }
   
   public PagedGui<T> elemClickFunction(TriConsumer<T, Integer, ClickType> function){
      this.elemClickFunction = function;
      return this;
   }
   
   public PagedGui<T> itemElemBuilder(Function<T, GuiElementBuilder> builder){
      this.itemElemBuilder = builder;
      return this;
   }
   
   public PagedGui<T> curFilter(GuiFilter<T> filter){
      this.curFilter = filter;
      return this;
   }
   
   public PagedGui<T> curSort(GuiSort<T> sort){
      this.curSort = sort;
      return this;
   }
   
   public GuiSort<T> getCurSort(){
      return curSort;
   }
   
   public GuiFilter<T> getCurFilter(){
      return curFilter;
   }
   
   public List<T> getItemList(){
      return itemList;
   }
   
   public List<T> getFilteredSortedList(){
      return filteredSortedList;
   }
   
   public TriConsumer<T, Integer, ClickType> getElemClickFunction(){
      return elemClickFunction;
   }
   
   public Function<T, GuiElementBuilder> getItemElemBuilder(){
      return itemElemBuilder;
   }
}
