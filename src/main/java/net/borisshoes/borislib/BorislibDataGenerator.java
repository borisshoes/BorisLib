package net.borisshoes.borislib;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class BorislibDataGenerator implements DataGeneratorEntrypoint {
   
   @Override
   public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator){
      FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
   }
}
