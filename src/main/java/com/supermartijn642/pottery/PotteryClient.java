package com.supermartijn642.pottery;

import com.supermartijn642.core.registry.ClientRegistrationHandler;
import com.supermartijn642.core.render.TextureAtlases;
import com.supermartijn642.pottery.content.PotBakedModel;
import com.supermartijn642.pottery.content.PotBlockRenderer;
import com.supermartijn642.pottery.content.PotColor;
import com.supermartijn642.pottery.content.PotType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Created 27/11/2023 by SuperMartijn642
 */
public class PotteryClient {

    public static void register(){
        ClientRegistrationHandler handler = ClientRegistrationHandler.get(Pottery.MODID);
        // Register block entity renderers
        for(PotType type : PotType.values())
            handler.registerCustomBlockEntityRenderer(type::getBlockEntityType, PotBlockRenderer::new);
        // Wrap all the pot models
        for(PotType type : PotType.values()){
            for(PotColor color : PotColor.values()){
                if(type == PotType.DEFAULT && color == PotColor.BLANK)
                    continue;
                handler.registerBlockModelOverwrite(() -> type.getBlock(color), PotBakedModel::new);
            }
        }
        // Put all decorated pot patterns into the block atlas
        BuiltInRegistries.DECORATED_POT_PATTERN.holders()
            .filter(holder -> holder.key().location().getNamespace().equals("minecraft"))
            .map(holder -> holder.value().assetId().withPrefix("entity/decorated_pot/"))
            .forEach(texture -> handler.registerAtlasSprite(TextureAtlases.getBlocks(), texture));
    }
}
