package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.BlockHitResult
import org.valkyrienskies.mod.common.config.BlockStateInfoResolver.serializeFluid
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.BlockShapeUtil

object BlockStateCommand {

    const val NO_STATE_FOUND = "command.valkyrienskies.no_state_found"
    const val BLOCKSTATE_NO_TYPE = "command.valkyrienskies.blockstate_no_type"
    const val BLOCKSTATE_NOT_REGISTERED = "command.valyrienskies.blockstate_not_registered"

    var render: Boolean = false
        private set

    fun register(vs: LiteralArgumentBuilder<CommandSourceStack>) {
        vs.then(literal("blockstate")
            .requires { it.hasPermission(VSGameConfig.SERVER.Commands.blockstateCommandPerms) }
            .then(literal("getShape").executes {
                val hitResult = it.source.entityOrException.pick(25.0, 1.0f, false)
                if (hitResult is BlockHitResult) {
                    val blockState = it.source.level.getBlockState(hitResult.blockPos)
                    it.source.sendSuccess({ Component.literal("getShape: ${BlockShapeUtil.getShape(blockState)}") }, false)
                    it.source.sendSuccess({ Component.literal("getCollisionShape: ${BlockShapeUtil.getCollisionShape(blockState)}") }, false)
                    it.source.sendSuccess({ Component.literal("getShapeForVS: ${BlockShapeUtil.getShapeForVS(blockState)}") }, false)
                    if (blockState.fluidState != null) {
                        it.source.sendSuccess({ Component.literal("getShape (fluid): ${BlockShapeUtil.getFluidShape(blockState.fluidState)}") }, false)
                    }
                }
                0
            })
            .then(literal("renderShape").executes {
                render = !render
                1
            })
            .executes {
                fun send(s: String) {
                    it.source.sendSuccess({ Component.literal(s) }, false)
                }
                val hit = it.source.playerOrException.pick(20.0, 1.0f, true)
                val fluidState: FluidState?
                val blockState: BlockState?
                if (hit is BlockHitResult) {
                    blockState = it.source.level.getBlockState(hit.blockPos)
                    fluidState = it.source.level.getFluidState(hit.blockPos)
                    if (blockState != null) {
                        send("Block: ${BlockStateParser.serialize(blockState)}")
                        if (blockState.fluidState != null) {
                            send("Block (fluid): ${serializeFluid(blockState.fluidState)}")
                        }
                    }
                    if (fluidState != null) {
                        send("Fluid: ${serializeFluid(fluidState)}")
                    }
                }
                1
            }
        )
    }

}
