package net.borisshoes.borislib.gui;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static net.borisshoes.borislib.BorisLib.*;

public class GraphicalItem extends Item implements PolymerItem {
   
   public static final String GRAPHICS_TAG = "graphic_id";
   
   public static final GraphicElement CONFIRM = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "confirm"), Items.STRUCTURE_VOID, false));
   public static final GraphicElement CANCEL = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "cancel"), Items.BARRIER, false));
   public static final GraphicElement LEFT_ARROW = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "left_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement RIGHT_ARROW = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "right_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement SORT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "sort"), Items.NETHER_STAR, false));
   public static final GraphicElement FILTER = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "filter"), Items.HOPPER, false));
   public static final GraphicElement BLACK = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "black"), Items.BLACK_DYE, false));
   public static final GraphicElement MENU_HORIZONTAL = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_horizontal"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_VERTICAL = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_vertical"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_MIDDLE = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_middle"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_top"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_bottom"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_top_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_top_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_bottom_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_bottom_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_right_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_left_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_top_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "menu_bottom_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement PAGE_BG = registerGraphicItem(new GraphicElement(Identifier.of(MOD_ID, "page_bg"), Items.GLASS_PANE, true));
   
   private static final ArrayList<Pair<Item,Integer>> DYED_REPLACEMENTS = new ArrayList<>(Arrays.asList(
         new Pair<>(Items.BLACK_STAINED_GLASS_PANE,0x000000),
         new Pair<>(Items.BLUE_STAINED_GLASS_PANE,0x0000ff),
         new Pair<>(Items.BROWN_STAINED_GLASS_PANE,0x6b5341),
         new Pair<>(Items.GRAY_STAINED_GLASS_PANE,0x5c5c5c),
         new Pair<>(Items.CYAN_STAINED_GLASS_PANE,0x168e94),
         new Pair<>(Items.GREEN_STAINED_GLASS_PANE,0x04753a),
         new Pair<>(Items.LIGHT_BLUE_STAINED_GLASS_PANE,0x5ad2fa),
         new Pair<>(Items.LIGHT_GRAY_STAINED_GLASS_PANE,0xc7c7c7),
         new Pair<>(Items.LIME_STAINED_GLASS_PANE,0x4ded0e),
         new Pair<>(Items.MAGENTA_STAINED_GLASS_PANE,0xb306c9),
         new Pair<>(Items.ORANGE_STAINED_GLASS_PANE,0xff8800),
         new Pair<>(Items.PINK_STAINED_GLASS_PANE,0xff7dde),
         new Pair<>(Items.PURPLE_STAINED_GLASS_PANE,0x8502cc),
         new Pair<>(Items.RED_STAINED_GLASS_PANE,0xff000),
         new Pair<>(Items.WHITE_STAINED_GLASS_PANE,0xffffff),
         new Pair<>(Items.YELLOW_STAINED_GLASS_PANE,0xffff00)
   ));
   
   public GraphicalItem(Item.Settings settings){
      super(settings.registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID,"graphical_item"))));
   }
   
   @Override
   public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(stack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.get(identifier);
      if(elem == null){
         return Identifier.of(MOD_ID, "graphical_item");
      }
      
      if(PolymerResourcePackUtils.hasMainPack(context)){
         return Identifier.of(elem.id().getNamespace(),"gui/"+elem.id().getPath());
      }else{
         return Registries.ITEM.getKey(getPolymerItem(stack,context)).get().getValue();
      }
   }
   
   @Override
   public Item getPolymerItem(ItemStack itemStack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(itemStack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.get(identifier);
      if(elem == null || itemStack == null) return Items.BARRIER;
      if(!elem.dyeable() || !itemStack.contains(DataComponentTypes.DYED_COLOR)) return elem.replacement();
      
      if(PolymerResourcePackUtils.hasMainPack(context.getPlayer())){
         return Items.LEATHER_CHESTPLATE;
      }else{
         return getItemFromColor(itemStack.get(DataComponentTypes.DYED_COLOR).rgb());
      }
   }
   
   private Item getItemFromColor(int colorRGB){
      Item closest = Items.GLASS_PANE;
      double cDist = Integer.MAX_VALUE;
      for(Pair<Item, Integer> pair : DYED_REPLACEMENTS){
         int repColor = pair.getRight();
         double rDist = (((repColor>>16)&0xFF)-((colorRGB>>16)&0xFF))*0.30;
         double gDist = (((repColor>>8)&0xFF)-((colorRGB>>8)&0xFF))*0.59;
         double bDist = ((repColor&0xFF)-(colorRGB&0xFF))*0.11;
         double dist = rDist*rDist + gDist*gDist + bDist*bDist;
         if(dist < cDist){
            cDist = dist;
            closest = pair.getLeft();
         }
      }
      return closest;
   }
   
   public static ItemStack with(GraphicElement id){
      ItemStack stack = new ItemStack(BorisLib.GRAPHICAL_ITEM);
      BORISLIB_ITEM_DATA.putProperty(stack,GRAPHICS_TAG,id.id().toString());
      return stack;
   }
   
   public static ItemStack withColor(GraphicElement id, int color){
      ItemStack stack = new ItemStack(BorisLib.GRAPHICAL_ITEM);
      BORISLIB_ITEM_DATA.putProperty(stack,GRAPHICS_TAG,id.id().toString());
      stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color));
      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR,true));
      return stack;
   }
   
   // These are not quite working yet with how client-sided bundles are ;-;
   
//   public static ItemStack getItemBlack(ItemStack shownItem, boolean selected, int selectionColor){
//      ItemStack stack = with(BLACK);
//      CustomModelDataComponent custom = new CustomModelDataComponent(List.of(),List.of(),List.of("selected"),List.of(selectionColor));
//      BundleContentsComponent.Builder bundle = new BundleContentsComponent.Builder(new BundleContentsComponent(List.of(shownItem.copy())));
//      bundle.setSelectedStackIndex(0);
//      if(selected) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, custom);
//      if(!shownItem.isEmpty()) stack.set(DataComponentTypes.BUNDLE_CONTENTS, bundle.build());
//      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.BUNDLE_CONTENTS,false));
//      return stack;
//   }
//
//   public static ItemStack getItemPageBg(int pageColor, ItemStack shownItem, boolean selected, int selectionColor){
//      ItemStack stack = withColor(PAGE_BG,pageColor);
//      CustomModelDataComponent custom = new CustomModelDataComponent(List.of(),List.of(true),List.of("selected"),List.of(selectionColor));
//      BundleContentsComponent.Builder bundle = new BundleContentsComponent.Builder(new BundleContentsComponent(List.of(shownItem.copy())));
//      bundle.setSelectedStackIndex(0);
//      if(selected) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, custom);
//      if(!shownItem.isEmpty()) stack.set(DataComponentTypes.BUNDLE_CONTENTS, bundle.build());
//      stack.set(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplayComponent.DEFAULT.with(DataComponentTypes.DYED_COLOR,true).with(DataComponentTypes.BUNDLE_CONTENTS,false));
//      return stack;
//   }
   
   
   
   public record GraphicElement(Identifier id, Item replacement, boolean dyeable){}
}