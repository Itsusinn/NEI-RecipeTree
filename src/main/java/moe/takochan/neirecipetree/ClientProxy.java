package moe.takochan.neirecipetree;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import moe.takochan.neirecipetree.nei.InventoryTreeButtonHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new InventoryTreeButtonHandler());
    }
}
