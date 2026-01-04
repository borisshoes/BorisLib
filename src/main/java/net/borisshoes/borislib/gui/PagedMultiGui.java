package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class PagedMultiGui extends PagedGuiBase {
   
   private final List<GuiMode<?>> modes = new ArrayList<>();
   private int currentModeInd = -1;
   
   public PagedMultiGui(MenuType<?> type, ServerPlayer player){
      super(type, player, false);
   }
   
   public <T> PagedMultiGui addMode(List<T> items, BiFunction<T, Integer, GuiElementBuilder> elemBuilder, TriConsumer<T, Integer, ClickType> elemClickFunction, GuiSort<T> defaultSort, GuiFilter<T> defaultFilter){
      GuiMode<T> config = new GuiMode<>(items, elemBuilder, elemClickFunction, defaultSort, defaultFilter);
      modes.add(config);
      if(currentModeInd == -1){
         currentModeInd = modes.size()-1;
         regenPageFunctions();
      }
      return this;
   }
   
   public void buildPage(){
      getCurrentConfig().buildPage(this);
   }
   
   protected <T> void regenPageFunctions(){
      GuiMode<T> curMode = getCurrentConfig();
      this.pageNum = curMode.getPageNum();
      this.pageUpFunction = (clickType -> {
         if(pageNum < numPages()){
            curMode.setPageNum(++pageNum);
            buildPage();
         }
      });
      this.pageDownFunction = (clickType -> {
         if(pageNum > 1){
            curMode.setPageNum(--pageNum);
            buildPage();
         }
      });
      this.cycleSortFunction = (clickType -> {
         if(curMode.getCurSort() != null){
            if(clickType.isLeft && clickType.shift){
               curMode.setCurSort(curMode.getCurSort().getStaticDefault());
            }else{
               curMode.setCurSort(curMode.getCurSort().cycle(curMode.getCurSort(),clickType.isRight));
            }
            buildPage();
         }
      });
      this.cycleFilterFunction = (clickType -> {
         if(curMode.getCurFilter() != null){
            if(clickType.isLeft && clickType.shift){
               curMode.setCurFilter(curMode.getCurFilter().getStaticDefault());
            }else{
               curMode.setCurFilter(curMode.getCurFilter().cycle(curMode.getCurFilter(),clickType.isRight));
            }
            buildPage();
         }
      });
   }
   
   public <T> void switchMode(int ind){
      if(ind < 0 || ind >= modes.size()) return;
      this.currentModeInd = ind;
      GuiMode<T> curMode = getCurrentConfig();
      this.pageNum = curMode.getPageNum();
      regenPageFunctions();
      buildPage();
   }
   
   @Override
   public int numPages(){
      GuiMode<?> curMode = getCurrentConfig();
      return Math.max(1,(int) (Math.ceil((float)curMode.getFilteredItems().size()/(this.paneWidth*this.paneHeight))));
   }
   
   protected GuiElementBuilder createSortItem(){
      GuiMode<?> curMode = getCurrentConfig();
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
            curMode.getCurSort().getColoredLabel()));
      sortBuilt.setCallback(this.cycleSortFunction);
      return sortBuilt;
   }
   protected GuiElementBuilder createFilterItem(){
      GuiMode<?> curMode = getCurrentConfig();
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
            curMode.getCurFilter().getColoredLabel()));
      filterBuilt.setCallback(this.cycleFilterFunction);
      return filterBuilt;
   }
   
   @SuppressWarnings("unchecked")
   public <T> GuiMode<T> getCurrentConfig(){
      return (GuiMode<T>) modes.get(currentModeInd);
   }
   
   @SuppressWarnings("unchecked")
   public <T> GuiMode<T> getConfig(int ind){
      return (GuiMode<T>) modes.get(ind);
   }
   
   public int getCurrentModeInd(){
      return currentModeInd;
   }
   
   public static class GuiMode<T> {
      
      private final TriConsumer<T, Integer, ClickType> elemClickFunction;
      private final BiFunction<T, Integer, GuiElementBuilder> elemBuilder;
      private List<T> items;
      private List<T> filteredSortedList;
      private GuiSort<T> curSort;
      private GuiFilter<T> curFilter;
      private int pageNum = 1;
      
      public GuiMode(List<T> items, BiFunction<T, Integer, GuiElementBuilder> elemBuilder, TriConsumer<T, Integer, ClickType> elemClickFunction, GuiSort<T> defaultSort, GuiFilter<T> defaultFilter){
         this.items = items;
         this.elemBuilder = elemBuilder;
         this.elemClickFunction = elemClickFunction;
         this.curSort = defaultSort;
         this.curFilter = defaultFilter;
      }
      
      public void buildPage(PagedMultiGui gui){
         int numPages = gui.numPages();
         this.pageNum = Math.clamp(this.pageNum,1,Math.max(1,numPages));
         gui.pageNum = this.pageNum;
         
         if(gui.sortInd >= 0 && curSort != null) gui.setSlot(gui.sortInd,gui.createSortItem());
         if(gui.filterInd >= 0 && curFilter != null) gui.setSlot(gui.filterInd,gui.createFilterItem());
         if(gui.nextInd >= 0 && numPages > 1) gui.setSlot(gui.nextInd,gui.createNextPageItem());
         if(gui.prevInd >= 0 && numPages > 1) gui.setSlot(gui.prevInd,gui.createPrevPageItem());
         
         List<T> pageItems = AlgoUtils.listToPage(getFilteredItems(),pageNum,gui.pageSize());
         int pageIndex = 0;
         for(int paneY = 0; paneY < gui.paneHeight; paneY++){
            for(int paneX = 0; paneX < gui.paneWidth; paneX++){
               int guiIndex = gui.paneStartInd + (pageIndex / gui.paneWidth) * gui.width + (pageIndex % gui.paneWidth);
               if(pageIndex < pageItems.size()){
                  T item = pageItems.get(pageIndex);
                  GuiElementBuilder builder = getElemBuilder().apply(item,pageIndex);
                  final int finalPageIndex = pageIndex;
                  builder.setCallback(clickType -> getElemClickFunction().accept(item, finalPageIndex, clickType));
                  gui.setSlot(guiIndex, builder);
               }else{
                  gui.setSlot(guiIndex,gui.blankItem);
               }
               pageIndex++;
            }
         }
      }
      
      private void updateFilteredSorted(){
         Stream<T> itemStream = items.stream();
         if(curFilter != null) itemStream = itemStream.filter(curFilter.getPredicate());
         if(curSort != null) itemStream = itemStream.sorted(curSort.getComparator());
         this.filteredSortedList = itemStream.toList();
      }
      
      public void setItems(List<T> items){
         this.items = items;
      }
      
      public List<T> getItems(){
         return items;
      }
      
      public List<T> getFilteredItems(){
         updateFilteredSorted();
         return this.filteredSortedList;
      }
      
      public BiFunction<T, Integer, GuiElementBuilder> getElemBuilder(){
         return elemBuilder;
      }
      
      public TriConsumer<T, Integer, ClickType> getElemClickFunction(){
         return elemClickFunction;
      }
      
      public GuiSort<T> getCurSort(){
         return curSort;
      }
      
      public void setCurSort(GuiSort<T> sort){
         this.curSort = sort;
      }
      
      public GuiFilter<T> getCurFilter(){
         return curFilter;
      }
      
      public void setCurFilter(GuiFilter<T> filter){
         this.curFilter = filter;
      }
      
      public int getPageNum(){
         return pageNum;
      }
      
      public void setPageNum(int page){
         this.pageNum = page;
      }
   }
}
