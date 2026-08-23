package com.parasitemimic;

import com.parasitemimic.compat.CotesiaCompat;
import com.parasitemimic.compat.SRPCompat;
import com.parasitemimic.entity.ModEntities;
import com.parasitemimic.event.MimicEventHandler;
import com.parasitemimic.event.SRPMergeHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = ParasiteMimic.MODID,
        name = ParasiteMimic.NAME,
        version = ParasiteMimic.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:forge@[14.23.5.2768,);required-after:srparasites;after:srpcotesia"
)
public class ParasiteMimic {

    public static final String MODID = "parasitemimic";
    public static final String NAME = "Parasite Mimic";
    public static final String VERSION = "1.4.0";

    @Mod.Instance(MODID)
    public static ParasiteMimic instance;

    public static Logger logger;

    @SidedProxy(
            clientSide = "com.parasitemimic.client.ClientProxy",
            serverSide = "com.parasitemimic.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ModEntities.register();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new MimicEventHandler());
        MinecraftForge.EVENT_BUS.register(new SRPMergeHandler());
        proxy.init();
        logger.info("Исказитель (Parasite Mimic) loaded.");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        SRPCompat.init();
        CotesiaCompat.init();
        logger.info("Исказитель loaded. SRP required. Cotesia integration: {}",
                CotesiaCompat.isLoaded() ? "ON" : "off");
    }
}
