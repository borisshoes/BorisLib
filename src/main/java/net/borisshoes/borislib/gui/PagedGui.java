package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.List;
import java.util.function.*;
import java.util.stream.Stream;

public class PagedGui<T> extends SimpleGui {
   
   private int paneWidth;
   private int paneHeight;
   private int paneStartInd;
   private int sortInd;
   private int filterInd;
   private int nextInd;
   private int prevInd;
   
   private GuiSort<T> curSort;
   private GuiFilter<T> curFilter;
   private int pageNum = 1;
   private List<T> itemList;
   private List<T> filteredSortedList;
   
   private int primaryTextColor = Formatting.DARK_PURPLE.getColorValue().intValue();
   private int secondaryTextColor = Formatting.LIGHT_PURPLE.getColorValue().intValue();
   private int action1TextColor = Formatting.AQUA.getColorValue().intValue();
   private int action2TextColor = Formatting.GREEN.getColorValue().intValue();
   private int action3TextColor = Formatting.YELLOW.getColorValue().intValue();
   
   private Consumer<ClickType> pageUpFunction = (clickType -> {
      if(pageNum < numPages()){
         pageNum++;
         buildPage();
      }
   });
   private Consumer<ClickType> pageDownFunction = (clickType -> {
      if(pageNum > 1){
         pageNum--;
         buildPage();
      }
   });
   private Consumer<ClickType> cycleSortFunction = (clickType -> {
      if(curSort != null){
         if(clickType.isLeft && clickType.shift){
            this.curSort = this.curSort.getStaticDefault();
         }else{
            this.curSort = curSort.cycle(curSort,clickType.isRight);
         }
         buildPage();
      }
   });
   private Consumer<ClickType> cycleFilterFunction = (clickType -> {
      if(curFilter != null){
         if(clickType.isLeft && clickType.shift){
            this.curFilter = this.curFilter.getStaticDefault();
         }else{
            this.curFilter = curFilter.cycle(curFilter,clickType.isRight);
         }
         buildPage();
      }
   });
   private TriConsumer<T, Integer, ClickType> elemClickFunction = (item, index, clickType) -> {};
   private Function<T, GuiElementBuilder> itemElemBuilder = (item -> new GuiElementBuilder(ItemStack.EMPTY));
   private GuiElementBuilder blankItem = new GuiElementBuilder(ItemStack.EMPTY);
   
   public PagedGui(ScreenHandlerType<?> type, ServerPlayerEntity player, List<T> items){
      super(type, player, false);
      this.itemList = items;
      if(width >= 3 && height >= 3){
         this.paneWidth = width - 2;
         this.paneHeight = height - 2;
         this.paneStartInd = width + 1;
         this.sortInd = 0;
         this.filterInd = width - 1;
         this.nextInd = width*height - 1;
         this.prevInd = width*(height-1);
      }
   }
   
   public int numPages(){
      return Math.max(1,(int) (Math.ceil((float)filteredSortedList.size()/(this.paneWidth*this.paneHeight))));
   }
   
   public int pageSize(){
      return this.paneWidth*this.paneHeight;
   }
   
   public void buildPage(){
      updateFilteredSorted();
      int numPages = numPages();
      pageNum = Math.clamp(pageNum,1,Math.max(1,numPages));
      List<T> pageItems = AlgoUtils.listToPage(filteredSortedList,pageNum,pageSize());
      
      if(sortInd >= 0) setSlot(sortInd,createSortItem());
      if(filterInd >= 0) setSlot(filterInd,createFilterItem());
      if(nextInd >= 0) setSlot(nextInd,createNextPageItem());
      if(prevInd >= 0) setSlot(prevInd,createPrevPageItem());
      
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
   
   private void updateFilteredSorted(){
      Stream<T> itemStream = itemList.stream();
      if(curFilter != null) itemStream = itemStream.filter(curFilter.getPredicate());
      if(curSort != null) itemStream = itemStream.sorted(curSort.getComparator());
      this.filteredSortedList = itemStream.toList();
   }
   
   private GuiElementBuilder createNextPageItem(){
      GuiElementBuilder nextPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.RIGHT_ARROW));
      nextPage.setName(Text.translatable("gui.borislib.next_page_title",pageNum,numPages()).withColor(primaryTextColor));
      nextPage.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.click").withColor(action1TextColor),
            Text.translatable("gui.borislib.next_page_sub").withColor(secondaryTextColor)));
      nextPage.setCallback(this.pageUpFunction);
      return nextPage;
   }
   
   private GuiElementBuilder createPrevPageItem(){
      GuiElementBuilder prevPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.LEFT_ARROW));
      prevPage.setName(Text.translatable("gui.borislib.prev_page_title",pageNum,numPages()).withColor(primaryTextColor));
      prevPage.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.click").withColor(action1TextColor),
            Text.translatable("gui.borislib.prev_page_sub").withColor(secondaryTextColor)));
      prevPage.setCallback(this.pageDownFunction);
      return prevPage;
   }
   
   private GuiElementBuilder createSortItem(){
      GuiElementBuilder sortBuilt = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.SORT)).hideDefaultTooltip();
      sortBuilt.setName(Text.translatable("gui.borislib.sort").withColor(primaryTextColor));
      sortBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.click").withColor(action1TextColor),
            Text.translatable("gui.borislib.change_sort").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.right_click").withColor(action2TextColor),
            Text.translatable("gui.borislib.change_sort_back").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.shift_click").withColor(action3TextColor),
            Text.translatable("gui.borislib.reset_sort").withColor(secondaryTextColor)));
      sortBuilt.addLoreLine(Text.literal(""));
      sortBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.sorting_by").withColor(secondaryTextColor),
            curSort.getColoredLabel()));
      sortBuilt.setCallback(this.cycleSortFunction);
      return sortBuilt;
   }
   
   private GuiElementBuilder createFilterItem(){
      GuiElementBuilder filterBuilt = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.FILTER)).hideDefaultTooltip();
      filterBuilt.setName(Text.translatable("gui.borislib.filter").withColor(primaryTextColor));
      filterBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.click").withColor(action1TextColor),
            Text.translatable("gui.borislib.change_filter").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.right_click").withColor(action2TextColor),
            Text.translatable("gui.borislib.change_filter_back").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.shift_click").withColor(action3TextColor),
            Text.translatable("gui.borislib.reset_filter").withColor(secondaryTextColor)));
      filterBuilt.addLoreLine(Text.literal(""));
      filterBuilt.addLoreLine(Text.translatable("text.borislib.two_elements",
            Text.translatable("gui.borislib.filtering_by").withColor(secondaryTextColor),
            curFilter.getColoredLabel()));
      filterBuilt.setCallback(this.cycleFilterFunction);
      return filterBuilt;
   }
   
   
   public PagedGui<T> primaryTextColor(int color){
      this.primaryTextColor = color;
      return this;
   }
   
   public PagedGui<T> secondaryTextColor(int color){
      this.secondaryTextColor = color;
      return this;
   }
   
   public PagedGui<T> action1TextColor(int color){
      this.action1TextColor = color;
      return this;
   }
   
   public PagedGui<T> action2TextColor(int color){
      this.action2TextColor = color;
      return this;
   }
   
   public PagedGui<T> action3TextColor(int color){
      this.action3TextColor = color;
      return this;
   }
   
   public PagedGui<T> items(List<T> items){
      this.itemList = items;
      return this;
   }
   
   public PagedGui<T> pageUpFunction(Consumer<ClickType> function){
      this.pageUpFunction = function;
      return this;
   }
   
   public PagedGui<T> pageDownFunction(Consumer<ClickType> function){
      this.pageDownFunction = function;
      return this;
   }
   
   public PagedGui<T> cycleSortFunction(Consumer<ClickType> function){
      this.cycleSortFunction = function;
      return this;
   }
   
   public PagedGui<T> cycleFilterFunction(Consumer<ClickType> function){
      this.cycleFilterFunction = function;
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
   
   public PagedGui<T> blankItem(GuiElementBuilder builder){
      this.blankItem = builder;
      return this;
   }
   
   public PagedGui<T> setPaneWidth(int paneWidth){
      this.paneWidth = paneWidth;
      return this;
   }
   
   public PagedGui<T> setPaneHeight(int paneHeight){
      this.paneHeight = paneHeight;
      return this;
   }
   
   public PagedGui<T> setPaneStartInd(int paneStartInd){
      this.paneStartInd = paneStartInd;
      return this;
   }
   
   public PagedGui<T> setSortInd(int sortInd){
      this.sortInd = sortInd;
      return this;
   }
   
   public PagedGui<T> setFilterInd(int filterInd){
      this.filterInd = filterInd;
      return this;
   }
   
   public PagedGui<T> setNextInd(int nextInd){
      this.nextInd = nextInd;
      return this;
   }
   
   public PagedGui<T> setPrevInd(int prevInd){
      this.prevInd = prevInd;
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
   
   public int getPaneWidth(){
      return paneWidth;
   }
   
   public int getPaneHeight(){
      return paneHeight;
   }
   
   public int getPaneStartInd(){
      return paneStartInd;
   }
   
   public int getSortInd(){
      return sortInd;
   }
   
   public int getFilterInd(){
      return filterInd;
   }
   
   public int getNextInd(){
      return nextInd;
   }
   
   public int getPrevInd(){
      return prevInd;
   }
   
   public GuiSort<T> getCurSort(){
      return curSort;
   }
   
   public GuiFilter<T> getCurFilter(){
      return curFilter;
   }
   
   public int getPageNum(){
      return pageNum;
   }
   
   public List<T> getItemList(){
      return itemList;
   }
   
   public List<T> getFilteredSortedList(){
      return filteredSortedList;
   }
   
   public int getPrimaryTextColor(){
      return primaryTextColor;
   }
   
   public int getSecondaryTextColor(){
      return secondaryTextColor;
   }
   
   public int getAction1TextColor(){
      return action1TextColor;
   }
   
   public int getAction2TextColor(){
      return action2TextColor;
   }
   
   public int getAction3TextColor(){
      return action3TextColor;
   }
   
   public Consumer<ClickType> getPageUpFunction(){
      return pageUpFunction;
   }
   
   public Consumer<ClickType> getPageDownFunction(){
      return pageDownFunction;
   }
   
   public Consumer<ClickType> getCycleSortFunction(){
      return cycleSortFunction;
   }
   
   public Consumer<ClickType> getCycleFilterFunction(){
      return cycleFilterFunction;
   }
   
   public TriConsumer<T, Integer, ClickType> getElemClickFunction(){
      return elemClickFunction;
   }
   
   public Function<T, GuiElementBuilder> getItemElemBuilder(){
      return itemElemBuilder;
   }
   
   public GuiElementBuilder getBlankItem(){
      return blankItem;
   }
   
   
}
