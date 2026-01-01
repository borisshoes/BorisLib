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

public abstract class PagedGuiBase extends SimpleGui {
   
   protected int paneWidth;
   protected int paneHeight;
   protected int paneStartInd;
   protected int sortInd;
   protected int filterInd;
   protected int nextInd;
   protected int prevInd;
   protected int pageNum = 1;
   
   protected int primaryTextColor = ChatFormatting.DARK_PURPLE.getColor().intValue();
   protected int secondaryTextColor = ChatFormatting.LIGHT_PURPLE.getColor().intValue();
   protected int action1TextColor = ChatFormatting.AQUA.getColor().intValue();
   protected int action2TextColor = ChatFormatting.GREEN.getColor().intValue();
   protected int action3TextColor = ChatFormatting.YELLOW.getColor().intValue();
   
   protected Consumer<ClickType> pageUpFunction;
   protected Consumer<ClickType> pageDownFunction;
   protected Consumer<ClickType> cycleSortFunction;
   protected Consumer<ClickType> cycleFilterFunction;
   
   protected GuiElementBuilder blankItem = new GuiElementBuilder(ItemStack.EMPTY);
   
   protected PagedGuiBase(MenuType<?> type, ServerPlayer player, boolean manipulatePlayerSlots){
      super(type, player, manipulatePlayerSlots);
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
   
   public abstract int numPages();
   
   public int pageSize(){
      return this.paneWidth*this.paneHeight;
   }
   
   public abstract void buildPage();
   
   protected GuiElementBuilder createNextPageItem(){
      GuiElementBuilder nextPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.RIGHT_ARROW));
      nextPage.setName(Component.translatable("gui.borislib.next_page_title",pageNum,numPages()).withColor(primaryTextColor));
      nextPage.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.next_page_sub").withColor(secondaryTextColor)));
      nextPage.setCallback(this.pageUpFunction);
      return nextPage;
   }
   
   protected GuiElementBuilder createPrevPageItem(){
      GuiElementBuilder prevPage = GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.LEFT_ARROW));
      prevPage.setName(Component.translatable("gui.borislib.prev_page_title",pageNum,numPages()).withColor(primaryTextColor));
      prevPage.addLoreLine(Component.translatable("text.borislib.two_elements",
            Component.translatable("gui.borislib.click").withColor(action1TextColor),
            Component.translatable("gui.borislib.prev_page_sub").withColor(secondaryTextColor)));
      prevPage.setCallback(this.pageDownFunction);
      return prevPage;
   }
   
   protected abstract GuiElementBuilder createSortItem();
   
   protected abstract GuiElementBuilder createFilterItem();
   
   public PagedGuiBase primaryTextColor(int color){
      this.primaryTextColor = color;
      return this;
   }
   
   public PagedGuiBase secondaryTextColor(int color){
      this.secondaryTextColor = color;
      return this;
   }
   
   public PagedGuiBase action1TextColor(int color){
      this.action1TextColor = color;
      return this;
   }
   
   public PagedGuiBase action2TextColor(int color){
      this.action2TextColor = color;
      return this;
   }
   
   public PagedGuiBase action3TextColor(int color){
      this.action3TextColor = color;
      return this;
   }
   
   public PagedGuiBase pageUpFunction(Consumer<ClickType> function){
      this.pageUpFunction = function;
      return this;
   }
   
   public PagedGuiBase pageDownFunction(Consumer<ClickType> function){
      this.pageDownFunction = function;
      return this;
   }
   
   public PagedGuiBase cycleSortFunction(Consumer<ClickType> function){
      this.cycleSortFunction = function;
      return this;
   }
   
   public PagedGuiBase cycleFilterFunction(Consumer<ClickType> function){
      this.cycleFilterFunction = function;
      return this;
   }
   
   public PagedGuiBase blankItem(GuiElementBuilder builder){
      this.blankItem = builder;
      return this;
   }
   
   public PagedGuiBase setPaneWidth(int paneWidth){
      this.paneWidth = paneWidth;
      return this;
   }
   
   public PagedGuiBase setPaneHeight(int paneHeight){
      this.paneHeight = paneHeight;
      return this;
   }
   
   public PagedGuiBase setPaneStartInd(int paneStartInd){
      this.paneStartInd = paneStartInd;
      return this;
   }
   
   public PagedGuiBase setSortInd(int sortInd){
      this.sortInd = sortInd;
      return this;
   }
   
   public PagedGuiBase setFilterInd(int filterInd){
      this.filterInd = filterInd;
      return this;
   }
   
   public PagedGuiBase setNextInd(int nextInd){
      this.nextInd = nextInd;
      return this;
   }
   
   public PagedGuiBase setPrevInd(int prevInd){
      this.prevInd = prevInd;
      return this;
   }
   
   public GuiElementBuilder getBlankItem(){
      return blankItem;
   }
   
   public int getPageNum(){
      return pageNum;
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
}
