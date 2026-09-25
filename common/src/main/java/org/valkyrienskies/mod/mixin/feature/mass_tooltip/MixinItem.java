package org.valkyrienskies.mod.mixin.feature.mass_tooltip;

import static org.valkyrienskies.mod.common.config.BlockStateInfoResolver.getColorForMass;

import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.ClientBlockStateInfo;
import org.valkyrienskies.mod.common.config.LiquidStateProperties;
import org.valkyrienskies.mod.common.config.SolidStateProperties;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.config.VSGameConfig.Client.TOOLTIP;
import org.valkyrienskies.mod.mixin.accessors.item.BucketItemAccessor;
import org.valkyrienskies.mod.mixinducks.feature.mass_tooltip.MassTooltipVisibility;

@Mixin(Item.class)
public class MixinItem {
    // todo also this currently does not work for things like banners, i believe because BannerItem overrides the appendHoverText method
    // i have a fix though that i will implement later
    @Inject(method = "appendHoverText", at = @At("HEAD"))
    private void valkyrienskies$addMassToTooltip(final ItemStack itemStack, final Level level,
        final List<Component> list, final TooltipFlag tooltipFlag, final CallbackInfo ci) {
        final MassTooltipVisibility visibility = VSGameConfig.CLIENT.getTooltip().getMassTooltipVisibility();
        if (!ClientBlockStateInfo.INSTANCE.getClientHasMassInfo()) return;
        if (visibility.isVisible(tooltipFlag)) {
            Item item = itemStack.getItem();
            if (item instanceof BlockItem blockItem) {
                final SolidStateProperties info = ClientBlockStateInfo.INSTANCE.getSolidProperties(blockItem.getBlock().defaultBlockState());
                final double mass = info != null ? info.getMass() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultMass(), 404.0);
                list.add(valkyrienskies$makeMassComponent(mass, true));
//                if (VSGameConfig.CLIENT.getTooltip().getShowAllProperties() && Minecraft.getInstance().options.keyShift.isDown()) {
//                    // todo this currently does not work, i think we have to listen to an add to tooltip event or smthn to append this only if shift is down
//                    final double friction = info != null ? info.getFriction() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultFriction(), 404.0);
//                    final double elasticity = info != null ? info.getElasticity() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultElasticity(), 404.0);
//                    list.add(valkyrienskies$makeFrictionComponent(friction));
//                    list.add(valkyrienskies$makeElasticityComponent(elasticity));
//                }
            }

            if (item instanceof BucketItemAccessor bucketItem) {
                Fluid fluid = bucketItem.getContent(); // todo impl lol
                if (fluid == null || fluid instanceof EmptyFluid) return;
                final LiquidStateProperties info = ClientBlockStateInfo.INSTANCE.getLiquidProperties(fluid.defaultFluidState());
                final double density = info != null ? info.getDensity() : Objects.requireNonNullElse(ClientBlockStateInfo.INSTANCE.getDefaultDensity(), 404.0);
                list.add(valkyrienskies$makeMassComponent(density, false));
            }
        }
    }

    @Unique
    private Component valkyrienskies$makeMassComponent(final double mass, boolean isMass) {
        TOOLTIP tooltip = VSGameConfig.CLIENT.getTooltip();
        final Component first = Component.translatable("tooltip.valkyrienskies." + (isMass ? "mass" : "density")).append(": ").withStyle(ChatFormatting.DARK_GRAY);
        final Component second = Component.literal(
            tooltip.getUseImperialUnits() ? String.format("%.2f", (mass * 2.20462262185)) : String.format("%.2f", mass)
        ).withStyle(
            tooltip.getDetailedMassTooltip() ? getColorForMass(mass) : ChatFormatting.DARK_GRAY
        );
        final Component third = Component.literal(
            (tooltip.getUseImperialUnits() ? "lb" : "kg") + (isMass ? "" : "/B")
        ).withStyle(tooltip.getDetailedMassTooltip() ? getColorForMass(mass) : ChatFormatting.DARK_GRAY);

        return Component.empty().append(first).append(second).append(third);
    }

    @Unique
    private Component valkyrienskies$makeFrictionComponent(final double friction) {
        final Component first = Component.translatable("tooltip.valkyrienskies.friction").append(": ").withStyle(ChatFormatting.DARK_GRAY);
        final Component second = Component.literal(String.format("%.2f", friction)).withStyle(ChatFormatting.GRAY);
        return Component.empty().append(first).append(second);
    }

    @Unique
    private Component valkyrienskies$makeElasticityComponent(final double elasticity) {
        final Component first = Component.translatable("tooltip.valkyrienskies.elasticity").append(": ").withStyle(ChatFormatting.DARK_GRAY);
        final Component second = Component.literal(String.format("%.2f", elasticity)).withStyle(ChatFormatting.GRAY);
        return Component.empty().append(first).append(second);
    }
}
