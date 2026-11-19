package yt.ren.astatine.mixin.shaderpack;

import net.irisshaders.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public abstract class ItemMixin implements IrisItemLightProvider {
}
