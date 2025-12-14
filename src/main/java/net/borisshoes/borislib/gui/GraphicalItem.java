package net.borisshoes.borislib.gui;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.borisshoes.borislib.BorisLib;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.Arrays;

import static net.borisshoes.borislib.BorisLib.*;

public class GraphicalItem extends Item implements PolymerItem {
   
   public static final String GRAPHICS_TAG = "graphic_id";
   
   public static final GraphicElement CONFIRM = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "confirm"), Items.STRUCTURE_VOID, false));
   public static final GraphicElement CANCEL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "cancel"), Items.BARRIER, false));
   public static final GraphicElement LEFT_ARROW = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "left_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement RIGHT_ARROW = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "right_arrow"), Items.SPECTRAL_ARROW, false));
   public static final GraphicElement SORT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "sort"), Items.NETHER_STAR, false));
   public static final GraphicElement FILTER = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "filter"), Items.HOPPER, false));
   public static final GraphicElement BLACK = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "black"), Items.BLACK_DYE, false));
   public static final GraphicElement MENU_HORIZONTAL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_horizontal"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_VERTICAL = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_vertical"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_MIDDLE = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_middle"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_LEFT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_left"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_RIGHT = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_right"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_RIGHT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_right_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_LEFT_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_left_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_TOP_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_top_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement MENU_BOTTOM_CONNECTOR = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "menu_bottom_connector"), Items.GLASS_PANE, true));
   public static final GraphicElement PAGE_BG = registerGraphicItem(new GraphicElement(Identifier.fromNamespaceAndPath(MOD_ID, "page_bg"), Items.GLASS_PANE, true));
   
   private static final ArrayList<Tuple<Item,Integer>> DYED_REPLACEMENTS = new ArrayList<>(Arrays.asList(
         new Tuple<>(Items.BLACK_STAINED_GLASS_PANE,0x000000),
         new Tuple<>(Items.BLUE_STAINED_GLASS_PANE,0x0000ff),
         new Tuple<>(Items.BROWN_STAINED_GLASS_PANE,0x6b5341),
         new Tuple<>(Items.GRAY_STAINED_GLASS_PANE,0x5c5c5c),
         new Tuple<>(Items.CYAN_STAINED_GLASS_PANE,0x168e94),
         new Tuple<>(Items.GREEN_STAINED_GLASS_PANE,0x04753a),
         new Tuple<>(Items.LIGHT_BLUE_STAINED_GLASS_PANE,0x5ad2fa),
         new Tuple<>(Items.LIGHT_GRAY_STAINED_GLASS_PANE,0xc7c7c7),
         new Tuple<>(Items.LIME_STAINED_GLASS_PANE,0x4ded0e),
         new Tuple<>(Items.MAGENTA_STAINED_GLASS_PANE,0xb306c9),
         new Tuple<>(Items.ORANGE_STAINED_GLASS_PANE,0xff8800),
         new Tuple<>(Items.PINK_STAINED_GLASS_PANE,0xff7dde),
         new Tuple<>(Items.PURPLE_STAINED_GLASS_PANE,0x8502cc),
         new Tuple<>(Items.RED_STAINED_GLASS_PANE,0xff0000),
         new Tuple<>(Items.WHITE_STAINED_GLASS_PANE,0xffffff),
         new Tuple<>(Items.YELLOW_STAINED_GLASS_PANE,0xffff00)
   ));
   
   public GraphicalItem(Item.Properties settings){
      super(settings.setId(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID,"graphical_item"))));
   }
   
   @Override
   public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(stack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.getValue(identifier);
      if(elem == null){
         return Identifier.fromNamespaceAndPath(MOD_ID, "graphical_item");
      }
      
      if(PolymerResourcePackUtils.hasMainPack(context)){
         return Identifier.fromNamespaceAndPath(elem.id().getNamespace(),"gui/"+elem.id().getPath());
      }else{
         return BuiltInRegistries.ITEM.getResourceKey(getPolymerItem(stack,context)).get().identifier();
      }
   }
   
   @Override
   public Item getPolymerItem(ItemStack itemStack, PacketContext context){
      String id = BORISLIB_ITEM_DATA.getStringProperty(itemStack,GRAPHICS_TAG);
      Identifier identifier = Identifier.tryParse(id);
      GraphicElement elem = BorisLib.GRAPHIC_ITEM_REGISTRY.getValue(identifier);
      if(elem == null || itemStack == null) return Items.BARRIER;
      if(!elem.dyeable() || !itemStack.has(DataComponents.DYED_COLOR)) return elem.replacement();
      
      if(PolymerResourcePackUtils.hasMainPack(context.getPlayer())){
         return Items.LEATHER_CHESTPLATE;
      }else{
         return getItemFromColor(itemStack.get(DataComponents.DYED_COLOR).rgb());
      }
   }
   
   private Item getItemFromColor(int colorRGB){
      Item closest = Items.GLASS_PANE;
      double cDist = Integer.MAX_VALUE;
      for(Tuple<Item, Integer> pair : DYED_REPLACEMENTS){
         int repColor = pair.getB();
         double rDist = (((repColor>>16)&0xFF)-((colorRGB>>16)&0xFF))*0.30;
         double gDist = (((repColor>>8)&0xFF)-((colorRGB>>8)&0xFF))*0.59;
         double bDist = ((repColor&0xFF)-(colorRGB&0xFF))*0.11;
         double dist = rDist*rDist + gDist*gDist + bDist*bDist;
         if(dist < cDist){
            cDist = dist;
            closest = pair.getA();
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
      stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
      stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.DYED_COLOR,true));
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