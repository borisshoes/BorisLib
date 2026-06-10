package net.borisshoes.borislib.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Shared base class for BorisLib's paginated server-side GUIs.
 *
 * <p>The base lays out a chest-style {@link SimpleGui} into:</p>
 * <ul>
 *    <li>A central "pane" of slots that displays the current page of items
 *        ({@link #paneWidth} × {@link #paneHeight}, starting at {@link #paneStartInd}).</li>
 *    <li>Border slots for the {@link #createSortItem() sort} button (top-left, {@link #sortInd}),
 *        the {@link #createFilterItem() filter} button (top-right, {@link #filterInd}), the
 *        {@link #createPrevPageItem() previous-page} button (bottom-left, {@link #prevInd}), and the
 *        {@link #createNextPageItem() next-page} button (bottom-right, {@link #nextInd}).</li>
 * </ul>
 *
 * <p>Concrete subclasses {@link PagedGui} (single typed list) and {@link PagedMultiGui} (multiple modes
 * each with their own list/filter/sort) provide the item-rendering / click-handling logic. Builders for
 * common control items use the {@link GraphicalItem} system for their textures and translate-keys under
 * the {@code gui.borislib.*} translation namespace.</p>
 *
 * <p>All numeric slot indexes and pane dimensions can be customised through the
 * {@code setXxx(int)} chainable setters on this class, so subclasses can build non-default layouts.</p>
 */
public abstract class PagedGuiBase extends SimpleGui {
   
   /** Width of the pane in slots. */
   protected int paneWidth;
   /** Height of the pane in slots. */
   protected int paneHeight;
   /** GUI slot index where the top-left corner of the pane lives. */
   protected int paneStartInd;
   /** Slot index of the sort button (negative to hide). */
   protected int sortInd;
   /** Slot index of the filter button (negative to hide). */
   protected int filterInd;
   /** Slot index of the next-page button (negative to hide). */
   protected int nextInd;
   /** Slot index of the previous-page button (negative to hide). */
   protected int prevInd;
   /** 1-based index of the currently displayed page. */
   protected int pageNum = 1;
   
   /** Default colour applied to a control element's primary line (its title). */
   protected int primaryTextColor = ChatFormatting.DARK_PURPLE.getColor().intValue();
   /** Default colour applied to secondary / hint lines. */
   protected int secondaryTextColor = ChatFormatting.LIGHT_PURPLE.getColor().intValue();
   /** Default colour applied to the "left click" action hint. */
   protected int action1TextColor = ChatFormatting.AQUA.getColor().intValue();
   /** Default colour applied to the "right click" action hint. */
   protected int action2TextColor = ChatFormatting.GREEN.getColor().intValue();
   /** Default colour applied to the "shift click" action hint. */
   protected int action3TextColor = ChatFormatting.YELLOW.getColor().intValue();
   
   /** Callback fired when the user clicks the next-page button. */
   protected Consumer<ClickType> pageUpFunction;
   /** Callback fired when the user clicks the previous-page button. */
   protected Consumer<ClickType> pageDownFunction;
   /** Callback fired when the user clicks the sort button (handles cycle / reset internally). */
   protected Consumer<ClickType> cycleSortFunction;
   /** Callback fired when the user clicks the filter button (handles cycle / reset internally). */
   protected Consumer<ClickType> cycleFilterFunction;
   
   /** Item builder used to fill empty pane slots. */
   protected GuiElementBuilder blankItem = new GuiElementBuilder(ItemStack.EMPTY);
   
   /**
    * Initialises a default border layout (1-slot border on each side) for guis of size {@code 3x3} and
    * larger. Subclasses can override the layout via the {@code setXxx} setters.
    *
    * @param type                   the chest size type
    * @param player                 the player who will see the GUI
    * @param manipulatePlayerSlots  forwarded to {@link SimpleGui#SimpleGui SimpleGui}
    */
   protected PagedGuiBase(MenuType<?> type, ServerPlayer player, boolean manipulatePlayerSlots){
      super(type, player, manipulatePlayerSlots);
      if(width >= 3 && height >= 3){
         this.paneWidth = width - 2;
         this.paneHeight = height - 2;
         this.paneStartInd = width + 1;
         this.sortInd = 0;
         this.filterInd = width - 1;
         this.nextInd = width * height - 1;
         this.prevInd = width * (height - 1);
      }
   }
   
   /** @return how many pages the current item list spans. */
   public abstract int numPages();
   
   /** @return the number of items that fit on one page ({@code paneWidth * paneHeight}). */
   public int pageSize(){
      return this.paneWidth * this.paneHeight;
   }
   
   /**
    * Renders the current page and re-binds all control buttons to {@link #pageUpFunction},
    * {@link #pageDownFunction}, {@link #cycleSortFunction} and {@link #cycleFilterFunction}.
    * Call this after any change that should re-paint the GUI (e.g. after changing the item list).
    */
   public abstract void buildPage();
   
   /** @return a builder for the standard next-page button (right-arrow icon, page-X-of-Y title). */
   protected GuiElementBuilder createNextPageItem(){
      GuiElementBuilder nextPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.RIGHT_ARROW));
      nextPage.setName(Component.translatable("gui.borislib.next_page_title", pageNum, numPages()).withColor(primaryTextColor));
      nextPage.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.next_page_sub").withColor(secondaryTextColor)));
      nextPage.setCallback(this.pageUpFunction);
      return nextPage;
   }
   
   /** @return a builder for the standard previous-page button. */
   protected GuiElementBuilder createPrevPageItem(){
      GuiElementBuilder prevPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.LEFT_ARROW));
      prevPage.setName(Component.translatable("gui.borislib.prev_page_title", pageNum, numPages()).withColor(primaryTextColor));
      prevPage.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.prev_page_sub").withColor(secondaryTextColor)));
      prevPage.setCallback(this.pageDownFunction);
      return prevPage;
   }
   
   /** @return a builder for the sort button reflecting the current sort mode. */
   protected abstract GuiElementBuilder createSortItem();
   
   /** @return a builder for the filter button reflecting the current filter mode. */
   protected abstract GuiElementBuilder createFilterItem();
   
   /** Sets the primary text colour used by built-in controls. Returns {@code this} for chaining. */
   public PagedGuiBase primaryTextColor(int color){
      this.primaryTextColor = color;
      return this;
   }
   
   /** Sets the secondary text colour used by built-in controls. Returns {@code this} for chaining. */
   public PagedGuiBase secondaryTextColor(int color){
      this.secondaryTextColor = color;
      return this;
   }
   
   /** Sets the colour for the primary action hint ("left click"). Returns {@code this} for chaining. */
   public PagedGuiBase action1TextColor(int color){
      this.action1TextColor = color;
      return this;
   }
   
   /** Sets the colour for the secondary action hint ("right click"). Returns {@code this} for chaining. */
   public PagedGuiBase action2TextColor(int color){
      this.action2TextColor = color;
      return this;
   }
   
   /** Sets the colour for the tertiary action hint ("shift click"). Returns {@code this} for chaining. */
   public PagedGuiBase action3TextColor(int color){
      this.action3TextColor = color;
      return this;
   }
   
   /** Replaces the next-page click handler. */
   public PagedGuiBase pageUpFunction(Consumer<ClickType> function){
      this.pageUpFunction = function;
      return this;
   }
   
   /** Replaces the previous-page click handler. */
   public PagedGuiBase pageDownFunction(Consumer<ClickType> function){
      this.pageDownFunction = function;
      return this;
   }
   
   /** Replaces the sort-button click handler. */
   public PagedGuiBase cycleSortFunction(Consumer<ClickType> function){
      this.cycleSortFunction = function;
      return this;
   }
   
   /** Replaces the filter-button click handler. */
   public PagedGuiBase cycleFilterFunction(Consumer<ClickType> function){
      this.cycleFilterFunction = function;
      return this;
   }
   
   /** Sets the item used to fill empty pane slots. */
   public PagedGuiBase blankItem(GuiElementBuilder builder){
      this.blankItem = builder;
      return this;
   }
   
   /** Sets the pane width in slots (defaults to {@code width - 2}). */
   public PagedGuiBase setPaneWidth(int paneWidth){
      this.paneWidth = paneWidth;
      return this;
   }
   
   /** Sets the pane height in slots (defaults to {@code height - 2}). */
   public PagedGuiBase setPaneHeight(int paneHeight){
      this.paneHeight = paneHeight;
      return this;
   }
   
   /** Sets the top-left slot index of the pane. */
   public PagedGuiBase setPaneStartInd(int paneStartInd){
      this.paneStartInd = paneStartInd;
      return this;
   }
   
   /** Sets the slot index of the sort button; a negative value hides the button. */
   public PagedGuiBase setSortInd(int sortInd){
      this.sortInd = sortInd;
      return this;
   }
   
   /** Sets the slot index of the filter button; a negative value hides the button. */
   public PagedGuiBase setFilterInd(int filterInd){
      this.filterInd = filterInd;
      return this;
   }
   
   /** Sets the slot index of the next-page button; a negative value hides the button. */
   public PagedGuiBase setNextInd(int nextInd){
      this.nextInd = nextInd;
      return this;
   }
   
   /** Sets the slot index of the previous-page button; a negative value hides the button. */
   public PagedGuiBase setPrevInd(int prevInd){
      this.prevInd = prevInd;
      return this;
   }
   
   /** @return the configured blank-slot item. */
   public GuiElementBuilder getBlankItem(){ return blankItem; }
   
   /** @return the 1-based index of the currently displayed page. */
   public int getPageNum(){ return pageNum; }
   
   /** @return current primary text colour (ARGB). */
   public int getPrimaryTextColor(){ return primaryTextColor; }
   
   /** @return current secondary text colour (ARGB). */
   public int getSecondaryTextColor(){ return secondaryTextColor; }
   
   /** @return current "left click" hint colour (ARGB). */
   public int getAction1TextColor(){ return action1TextColor; }
   
   /** @return current "right click" hint colour (ARGB). */
   public int getAction2TextColor(){ return action2TextColor; }
   
   /** @return current "shift click" hint colour (ARGB). */
   public int getAction3TextColor(){ return action3TextColor; }
   
   /** @return the pane width in slots. */
   public int getPaneWidth(){ return paneWidth; }
   
   /** @return the pane height in slots. */
   public int getPaneHeight(){ return paneHeight; }
   
   /** @return the top-left pane slot index. */
   public int getPaneStartInd(){ return paneStartInd; }
   
   /** @return slot index of the sort button (negative if hidden). */
   public int getSortInd(){ return sortInd; }
   
   /** @return slot index of the filter button (negative if hidden). */
   public int getFilterInd(){ return filterInd; }
   
   /** @return slot index of the next-page button (negative if hidden). */
   public int getNextInd(){ return nextInd; }
   
   /** @return slot index of the previous-page button (negative if hidden). */
   public int getPrevInd(){ return prevInd; }
   
   /** @return the next-page click handler. */
   public Consumer<ClickType> getPageUpFunction(){ return pageUpFunction; }
   
   /** @return the previous-page click handler. */
   public Consumer<ClickType> getPageDownFunction(){ return pageDownFunction; }
   
   /** @return the sort-button click handler. */
   public Consumer<ClickType> getCycleSortFunction(){ return cycleSortFunction; }
   
   /** @return the filter-button click handler. */
   public Consumer<ClickType> getCycleFilterFunction(){ return cycleFilterFunction; }
}
