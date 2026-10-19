package moe.takochan.neirecipetree;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import moe.takochan.neirecipetree.bom.AlternativePreferences;
import moe.takochan.neirecipetree.bom.TreeStorage;
import moe.takochan.neirecipetree.nei.InventoryTreeButtonHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        AlternativePreferences.init(event.getModConfigurationDirectory());
        TreeStorage.init(event.getModConfigurationDirectory());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Alternative choices are loaded lazily together with the tree store, when the player first
        // opens the inventory
        MinecraftForge.EVENT_BUS.register(new InventoryTreeButtonHandler());
    }
}
