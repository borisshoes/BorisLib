package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.utils.AlgoUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import org.apache.commons.lang3.function.TriConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class PagedMultiGui extends PagedGuiBase {
   
   private final List<GuiMode<?>> modes = new ArrayList<>();
   private int currentModeInd = -1;
   
   /**
    * Constructs a new multi-mode paginated GUI.
    *
    * @param type   the chest size/type
    * @param player the player who will see the GUI
    */
   public PagedMultiGui(MenuType<?> type, ServerPlayer player){
      super(type, player, false);
   }
   
   /**
    * Registers a new GUI mode. If this is the first mode added, it becomes active immediately.
    *
    * @param items              the list of items to display in this mode
    * @param elemBuilder        function to convert an item + slot-in-page into a {@link GuiElementBuilder}
    * @param elemClickFunction  callback fired when the user clicks an item in this mode
    * @param defaultSort        initial sort strategy (can be {@code null})
    * @param defaultFilter      initial filter strategy (can be {@code null})
    * @param <T>                the item type for this mode
    * @return {@code this} for chaining
    */
   public <T> PagedMultiGui addMode(List<T> items, BiFunction<T, Integer, GuiElementBuilder> elemBuilder, TriConsumer<T, Integer, ClickType> elemClickFunction, GuiSort<T> defaultSort, GuiFilter<T> defaultFilter){
      GuiMode<T> mode = new GuiMode<>(items, elemBuilder, elemClickFunction, defaultSort, defaultFilter);
      modes.add(mode);
      if(currentModeInd == -1){
         currentModeInd = modes.size() - 1;
         regenPageFunctions();
      }
      return this;
   }
   
   /** Rebuilds the current page using the active mode's item list and rendering logic. */
   public void buildPage(){
      getCurrentMode().buildPage(this);
   }
   
   /**
    * Re-binds the internal click handlers (page-up, page-down, sort-cycle, filter-cycle) based
    * on the current mode's state and callbac handlers.
    *
    * @param <T> the active mode's item type
    */
   protected <T> void regenPageFunctions(){
      GuiMode<T> curMode = getCurrentMode();
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
               curMode.setCurSort(curMode.getCurSort().cycle(curMode.getCurSort(), clickType.isRight));
            }
            buildPage();
         }
      });
      this.cycleFilterFunction = (clickType -> {
         if(curMode.getCurFilter() != null){
            if(clickType.isLeft && clickType.shift){
               curMode.setCurFilter(curMode.getCurFilter().getStaticDefault());
            }else{
               curMode.setCurFilter(curMode.getCurFilter().cycle(curMode.getCurFilter(), clickType.isRight));
            }
            buildPage();
         }
      });
   }
   
   /**
    * Switches to the specified mode by index. Clamps the active mode's page number to the new
    * valid range, regenerates page-control handlers, and rebuilds the page.
    *
    * @param ind the 0-based mode index
    * @param <T> the new mode's item type
    */
   public <T> void switchMode(int ind){
      if(ind < 0 || ind >= modes.size()) return;
      this.currentModeInd = ind;
      GuiMode<T> curMode = getCurrentMode();
      this.pageNum = curMode.getPageNum();
      regenPageFunctions();
      buildPage();
   }
   
   /** @return the number of pages required to fit all filtered items in the active mode. */
   @Override
   public int numPages(){
      GuiMode<?> curMode = getCurrentMode();
      return Math.max(1, (int) (Math.ceil((float) curMode.getFilteredItems().size() / (this.paneWidth * this.paneHeight))));
   }
   
   /** @return a builder for the sort button reflecting the active mode's current sort. */
   protected GuiElementBuilder createSortItem(){
      GuiMode<?> curMode = getCurrentMode();
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
   
   /** @return a builder for the filter button reflecting the active mode's current filter. */
   protected GuiElementBuilder createFilterItem(){
      GuiMode<?> curMode = getCurrentMode();
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
   
   /**
    * Returns the currently active mode.
    *
    * @param <T> the active mode's item type
    * @return the active {@link GuiMode}
    */
   @SuppressWarnings("unchecked")
   public <T> GuiMode<T> getCurrentMode(){
      return (GuiMode<T>) modes.get(currentModeInd);
   }
   
   /**
    * Returns the mode at the given index.
    *
    * @param ind the 0-based mode index
    * @param <T> the mode's item type
    * @return the {@link GuiMode} at the given index
    */
   @SuppressWarnings("unchecked")
   public <T> GuiMode<T> getMode(int ind){
      return (GuiMode<T>) modes.get(ind);
   }
   
   /** @return the 0-based index of the currently active mode. */
   public int getCurrentModeInd(){
      return currentModeInd;
   }
   
   /**
    * Represents a single display mode within a {@link PagedMultiGui}.
    *
    * <p>Each mode holds its own item list, element builder, click handler, sort strategy,
    * filter strategy, and current page number. The {@code buildPage} method renders items
    * from the filtered/sorted list into the GUI's pane slots.</p>
    *
    * @param <T> the item type managed by this mode
    */
   public static class GuiMode<T> {
      
      private final TriConsumer<T, Integer, ClickType> elemClickFunction;
      private final BiFunction<T, Integer, GuiElementBuilder> elemBuilder;
      private List<T> items;
      private List<T> filteredSortedList;
      private GuiSort<T> curSort;
      private GuiFilter<T> curFilter;
      private int pageNum = 1;
      
      /**
       * Constructs a new GUI mode.
       *
       * @param items              the item list to display
       * @param elemBuilder        function to convert an item + page-slot-index into a {@link GuiElementBuilder}
       * @param elemClickFunction  callback fired when an item is clicked
       * @param defaultSort        initial sort (can be {@code null})
       * @param defaultFilter      initial filter (can be {@code null})
       */
      public GuiMode(List<T> items, BiFunction<T, Integer, GuiElementBuilder> elemBuilder, TriConsumer<T, Integer, ClickType> elemClickFunction, GuiSort<T> defaultSort, GuiFilter<T> defaultFilter){
         this.items = items;
         this.elemBuilder = elemBuilder;
         this.elemClickFunction = elemClickFunction;
         this.curSort = defaultSort;
         this.curFilter = defaultFilter;
      }
      
      /**
       * Renders the current page of this mode into the given GUI. Populates pane slots with
       * items from the filtered/sorted list and sets up control buttons (sort, filter, prev, next).
       *
       * @param gui the owning {@link PagedMultiGui}
       */
      public void buildPage(PagedMultiGui gui){
         int numPages = gui.numPages();
         this.pageNum = Math.clamp(this.pageNum, 1, Math.max(1, numPages));
         gui.pageNum = this.pageNum;
         
         if(gui.sortInd >= 0 && curSort != null) gui.setSlot(gui.sortInd, gui.createSortItem());
         if(gui.filterInd >= 0 && curFilter != null) gui.setSlot(gui.filterInd, gui.createFilterItem());
         if(gui.nextInd >= 0 && numPages > 1) gui.setSlot(gui.nextInd, gui.createNextPageItem());
         if(gui.prevInd >= 0 && numPages > 1) gui.setSlot(gui.prevInd, gui.createPrevPageItem());
         
         List<T> pageItems = AlgoUtils.listToPage(getFilteredItems(), pageNum, gui.pageSize());
         int pageIndex = 0;
         for(int paneY = 0; paneY < gui.paneHeight; paneY++){
            for(int paneX = 0; paneX < gui.paneWidth; paneX++){
               int guiIndex = gui.paneStartInd + (pageIndex / gui.paneWidth) * gui.width + (pageIndex % gui.paneWidth);
               if(pageIndex < pageItems.size()){
                  T item = pageItems.get(pageIndex);
                  GuiElementBuilder builder = getElemBuilder().apply(item, pageIndex);
                  final int finalPageIndex = pageIndex;
                  builder.setCallback(clickType -> getElemClickFunction().accept(item, finalPageIndex, clickType));
                  gui.setSlot(guiIndex, builder);
               }else{
                  gui.setSlot(guiIndex, gui.blankItem);
               }
               pageIndex++;
            }
         }
      }
      
      /**
       * Re-computes the filtered and sorted item list from the base item list. Applies the
       * current filter's predicate, then the current sort's comparator, storing the result
       * in {@code filteredSortedList}.
       */
      private void updateFilteredSorted(){
         Stream<T> itemStream = items.stream();
         if(curFilter != null) itemStream = itemStream.filter(curFilter.getPredicate());
         if(curSort != null) itemStream = itemStream.sorted(curSort.getComparator());
         this.filteredSortedList = itemStream.toList();
      }
      
      /** Sets the base item list (does not trigger a rebuild until the next page render). */
      public void setItems(List<T> items){
         this.items = items;
      }
      
      /** @return the base item list (unfiltered, unsorted). */
      public List<T> getItems(){
         return items;
      }
      
      /**
       * Returns the filtered and sorted item list. Re-computes the list on each call based on the
       * current filter and sort strategies.
       *
       * @return the filtered and sorted item list
       */
      public List<T> getFilteredItems(){
         updateFilteredSorted();
         return this.filteredSortedList;
      }
      
      /** @return the element builder function for this mode. */
      public BiFunction<T, Integer, GuiElementBuilder> getElemBuilder(){
         return elemBuilder;
      }
      
      /** @return the element click callback for this mode. */
      public TriConsumer<T, Integer, ClickType> getElemClickFunction(){
         return elemClickFunction;
      }
      
      /** @return the current sort strategy (may be {@code null}). */
      public GuiSort<T> getCurSort(){
         return curSort;
      }
      
      /** Sets a new sort strategy for this mode. */
      public void setCurSort(GuiSort<T> sort){
         this.curSort = sort;
      }
      
      /** @return the current filter strategy (may be {@code null}). */
      public GuiFilter<T> getCurFilter(){
         return curFilter;
      }
      
      /** Sets a new filter strategy for this mode. */
      public void setCurFilter(GuiFilter<T> filter){
         this.curFilter = filter;
      }
      
      /** @return the 1-based page number within this mode. */
      public int getPageNum(){
         return pageNum;
      }
      
      /** Sets the 1-based page number within this mode. */
      public void setPageNum(int page){
         this.pageNum = page;
      }
   }
}
