package org.valkyrienskies.mod.mixin.feature.mass_tooltip;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.ClientBlockInfo;
import org.valkyrienskies.mod.client.ClientBlockStateInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.mixinducks.feature.mass_tooltip.MassTooltipVisibility;
import oshi.util.tuples.Pair;

@Mixin(Item.class)
public class MixinItem {
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void valkyrienskies$addMassToTooltip(final ItemStack itemStack, final Level level,
        final List<Component> list, final TooltipFlag tooltipFlag, final CallbackInfo ci) {
        final MassTooltipVisibility visibility = VSGameConfig.CLIENT.getTooltip().getMassTooltipVisibility();
        if (!(visibility.isVisible(tooltipFlag) && ClientBlockStateInfo.INSTANCE.getClientHasMassInfo())) return;
        Item item = itemStack.getItem();
        if (item instanceof BlockItem blockItem) {
            final ClientBlockInfo info = ClientBlockStateInfo.INSTANCE.getBlockInfo(BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
            final double mass = info != null ? info.getMass() : 1000;
            list.add(Component.translatable("tooltip.valkyrienskies.mass")
                .append(VSGameConfig.CLIENT.getTooltip().getUseImperialUnits() ?
                    getImperialText(mass) : ": " + mass + "kg").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (item instanceof BucketItemAccessor bucketItem) {
            Fluid fluid = bucketItem.getContent(); // todo impl lol
            list.add(Component.literal("contents: " + (fluid != null ? fluid.getClass().getSimpleName() : "empty")));
        }
    }

    @Unique
    private Pair<Integer, Integer> convertToImperial(final double mass) {
        final double ounces = mass * 35.274;
        final double pounds = Math.floor(ounces / 16);
        return new Pair<>(
            (int) pounds,
            (int) Math.floor((ounces / 16 - pounds) * 16)
        );
    }

    @Unique
    private String getImperialText(final double mass) {
        String impText = ": ";
        final Pair<Integer, Integer> imperial = convertToImperial(mass);
        if (imperial.getA() > 0) {
            impText = impText + imperial.getA();
            if (imperial.getA() == 1) {
                impText = impText + "lb. ";
            } else {
                impText = impText + "lbs. ";
            }
        }

        if (imperial.getB() > 0) {
            impText = impText + imperial.getB() + "oz.";
        }

        return impText;
    }
}
