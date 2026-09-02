package org.valkyrienskies.mod.common.command.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.config.BlockStateInfoResolver
import org.valkyrienskies.mod.common.config.BlockStateInfoResolver.getBlockType
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.vsCore

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
                getBlockState(it)
            }
        )
    }

    private fun getBlockState(context: CommandContext<CommandSourceStack>): Int {
        val source: CommandSourceStack = context.source
        val hitResult = source.entityOrException.pick(25.0, 1.0f, false)
        if (hitResult is BlockHitResult) {
            val blockState: BlockState = source.level.getBlockState(hitResult.blockPos)

            val vsiBlockType: VsiBlockType = MassDatapackResolver.getBlockStateType(blockState) ?: return fail(BLOCKSTATE_NO_TYPE, source)
            val vsiBlockState: VsiBlockState = vsCore.blockTypes.getState(vsiBlockType) ?: return fail(BLOCKSTATE_NOT_REGISTERED, source)

            val message = Component.literal("BlockState: ${BlockStateParser.serialize(blockState)}")
            message.nextLine().append("VsiBlockType: $vsiBlockType")
            message.nextLine().append("SolidState: ${vsiBlockState.solidState}")
            message.nextLine().append("LiquidState: ${vsiBlockState.liquidState}")
            message.nextLine().append("DisplacementState: ${vsiBlockState.displacementState}")
            message.nextLine().append("Composition: ${BlockStateInfoResolver.getComposition(blockState)}")

            source.sendSuccess({ message }, false)

            return 1
        }

        return fail(NO_STATE_FOUND, source)
    }

    private fun fail(message: String, source: CommandSourceStack): Int {
        source.sendFailure(translatable(message))
        return 0
    }

    fun MutableComponent.nextLine(): MutableComponent {
        return this.append("\n")
    }
}
