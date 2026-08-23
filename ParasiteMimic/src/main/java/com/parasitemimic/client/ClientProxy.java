package com.parasitemimic.client;

import com.parasitemimic.CommonProxy;
import com.parasitemimic.entity.EntityAdaptedParasiteMimic;
import com.parasitemimic.entity.EntityParasiteMimic;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityParasiteMimic.class, RenderParasiteMimic::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityAdaptedParasiteMimic.class, RenderParasiteMimic::new);
    }

    public static class RenderParasiteMimic extends RenderLiving<EntityParasiteMimic> {
        private static final ResourceLocation PRIMITIVE =
                new ResourceLocation("parasitemimic", "textures/entity/parasite_mimic.png");
        private static final ResourceLocation ADAPTED =
                new ResourceLocation("parasitemimic", "textures/entity/adapted_parasite_mimic.png");

        public RenderParasiteMimic(RenderManager manager) {
            super(manager, new ModelPlayer(0.0F, false), 0.5F);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityParasiteMimic entity) {
            return entity instanceof EntityAdaptedParasiteMimic ? ADAPTED : PRIMITIVE;
        }
    }
}
