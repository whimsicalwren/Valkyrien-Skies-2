package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.BoxBlockShape
import org.valkyrienskies.core.api.physics.blockstates.LiquidBlockShape
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.MediumState
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.config.MassDatapackResolver.decideDefaultPriority
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.DelegateLogger.provideDelegate
import org.valkyrienskies.mod.util.logger
import java.util.function.Predicate
import java.util.regex.Pattern
import kotlin.math.roundToInt

data class SolidStateProperties (
    val priority: Int,
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val hardness: Double,
    val noCollision: Boolean = false,
    val shapeOverride: SolidBlockShape? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolidStateProperties) return false

        return mass == other.mass && friction == other.friction && elasticity == other.elasticity && hardness == other.hardness && noCollision == other.noCollision && shapeOverride == other.shapeOverride
    }

    override fun hashCode(): Int { // hashbrowns are good but have you ever tried hashcodes
        var result = priority
        result = 31 * result + mass.hashCode()
        result = 31 * result + friction.hashCode()
        result = 31 * result + elasticity.hashCode()
        result = 31 * result + hardness.hashCode()
        result = 31 * result + noCollision.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): SolidStateProperties = SolidStateProperties(
            0,
            VSGameConfig.SERVER.defaultBlockMass,
            VSGameConfig.SERVER.defaultBlockFriction,
            VSGameConfig.SERVER.defaultBlockElasticity,
            VSGameConfig.SERVER.defaultBlockHardness
        )
    }
}

/**
 * @see [LiquidState]
 */
data class LiquidStateProperties (
    val priority: Int,
    val density: Double,
    val dragCoefficient: Double,
    val velocity: Vector3d,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidStateProperties) return false

        return density == other.density && dragCoefficient == other.dragCoefficient && velocity == other.velocity && noCollision == other.noCollision && shapeOverride == other.shapeOverride
    }

    override fun hashCode(): Int {
        var result = priority
        result = 31 * result + density.hashCode()
        result = 31 * result + dragCoefficient.hashCode()
        result = 31 * result + noCollision.hashCode()
        result = 31 * result + velocity.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): LiquidStateProperties = LiquidStateProperties(
            0,
            VSGameConfig.SERVER.defaultLiquidDensity,
            VSGameConfig.SERVER.defaultLiquidDragCoefficient,
            Vector3d(
                VSGameConfig.SERVER.defaultLiquidVelocityX,
                VSGameConfig.SERVER.defaultLiquidVelocityY,
                VSGameConfig.SERVER.defaultLiquidVelocityZ
            )
        )
    }
}

data class DisplacementStateProperties (
    val priority: Int,
    val shape: AABBic? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplacementStateProperties) return false

        return shape == other.shape
    }

    override fun hashCode(): Int {
        var result = priority
        result = 31 * result + shape.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): DisplacementStateProperties = DisplacementStateProperties(0)
    }
}

data class MediumStateProperties (
    val priority: Int,
    val dragCoefficient: Double,
    val shape: AABBic? = null,
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediumStateProperties) return false

        return dragCoefficient == other.dragCoefficient && shape == other.shape
    }

    override fun hashCode(): Int {
        var result = dragCoefficient.hashCode()
        result = 31 * result + shape.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): MediumStateProperties = MediumStateProperties(0, VSGameConfig.SERVER.defaultLiquidDragCoefficient)
    }
}

data class BlockStateString (
    val id: ResourceLocation,
    val properties: String = "default"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockStateString) return false

        return id == other.id && properties == other.properties
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + properties.hashCode()
        return result
    }

    companion object {
        fun fromString(raw: String): BlockStateString {
            if (raw.indexOf('[') == -1) {
                return BlockStateString(ResourceLocation(raw))
                // if a blockstate has no properties, the parser will not append the brackets to it, so we can use that as a check
                // since indexOf returns -1 if the char does not exist in the string.
            }
            val id = ResourceLocation(raw.substring(0, raw.indexOf('[')))
            val properties : String = raw.substring(raw.indexOf('[') + 1, raw.indexOf(']'))
            return BlockStateString(id, properties)
        }

        fun fromBlockState(state: BlockState): BlockStateString = fromString(BlockStateParser.serialize(state))
    }
}

data class BlockStateProperties (
    val solid: SolidStateProperties? = null,
    val liquid: LiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: MediumStateProperties? = null,
)


object BlockStateInfoResolver {
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, BlockStateProperties>> = HashMap()
    private val mcState2VsState: MutableMap<BlockState, VsiBlockState> = HashMap()

    @JvmField
    val defaultProperties: SolidStateProperties = SolidStateProperties(
        0,
        VSGameConfig.SERVER.defaultBlockMass,
        VSGameConfig.SERVER.defaultBlockFriction,
        VSGameConfig.SERVER.defaultBlockElasticity,
        VSGameConfig.SERVER.defaultBlockHardness,
    )


    fun BlockState.getBlockType(): VsiBlockType? {
        val vsState = mcState2VsState[this] ?: return null
        return vsCore.blockTypes.getType(vsState)
    }

    class BlockStateInfoDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_mass") {

        override fun apply(objects: MutableMap<ResourceLocation, JsonElement>?, resourceManager: ResourceManager?, profilerFiller: ProfilerFiller?) {
            objects?.forEach { (location, element) ->
                try {
                    if (element.isJsonArray) {
                        var i = 0
                        element.asJsonArray.forEach { element1: JsonElement ->
                            parse(element1, location, i)
                            i++
                        }
                    } else if (element.isJsonObject) {
                        parse(element, location)
                    } else throw IllegalArgumentException()
                } catch (e: Exception) {
                    logger.error(e)
                }
            }
        }

        /**
         * The type of object a property entry applies to.
         */
        enum class IdType (val string: String) {
            BLOCK("block"),
            FLUID("fluid"),
            BLOCK_TAG("tag"),
            FLUID_TAG("fluid_tag"),
            NONE("");

            fun getId(jsonObject: JsonObject): String {
                return jsonObject.get(string).asString
            }
        }

        /**
         * The structure of a property entry. Used to determine how entries should be parsed.
         */
        enum class StructureType {
            BLOCK_BASIC,
            BLOCK_COMPOUND,
            BLOCK_STATES,
            FLUID_BASIC,
            FLUID_STATES,
            ERROR;

            fun toStructure(): Structure {
                return Structure(this)
            }
        }

        /**
         * Represents the structure of a block/fluid property entry.
         *
         * @param type The [StructureType] of this Structure.
         * @param error If this structure represents an error, this is the message.
         * @param warn If this structure is still valid but has a warning, this is the message.
         * @param state2StructureType If this structure's type is [StructureType.BLOCK_STATES] or
         * [StructureType.FLUID_STATES], this is a map of each state to its structure, allowing for differing structures of states if needed.
         */
        class Structure(val type: StructureType, val error: String? = null, val warn: String? = null, val state2StructureType: Map<String, StructureType>? = null) {
            fun isError(): Boolean {
                return type == StructureType.ERROR
            }

            fun isWarn(): Boolean {
                return warn != null
            }

            companion object {
                /**
                 * Indicates that a problem has occurred with the entry, and that we should skip this entry.
                 */
                fun error(string: String): Structure {
                    return Structure(StructureType.ERROR, string)
                }

                /**
                 * Indicates that a problem has occurred with the entry, but it should be safe to parse.
                 */
                fun warn(type: StructureType, string: String): Structure {
                    return Structure(type, warn = string)
                }
            }
        }

        // matches if the string is "default", or if it matches "key=value,key2=value2,etc"
        // https://regex101.com
        val stateRegex: Predicate<String> = Pattern.compile("^(default|\\w+=[^,=]+(,\\w+=[^,=]+)*)$").asPredicate()

        val blockValues = listOf("mass", "friction", "elasticity", "hardness", "no_collision", "shape_override")
        val mediumValues = listOf("drag", "shape")
        val fluidValues = listOf("density", "drag", "velocity", "no_collision", "shape_override")

        /**
         * Determine the [IdType] for a property entry
         */
        private fun determineId(json: JsonObject): IdType {
            return when {
                json.has("block") -> IdType.BLOCK
                json.has("fluid") -> IdType.FLUID
                json.has("tag") -> IdType.BLOCK_TAG
                json.has("fluid_tag") -> IdType.FLUID_TAG
                else -> IdType.NONE
            }
        }

        /**
         * Determine the [Structure] of a property entry.
         */
        private fun determineStructure(json: JsonObject, idType: IdType, id: String): Structure {
            fun determineCompoundBlockStructure(json: JsonObject, id: String): Structure {
                val hasSolid = json.has("solid")
                val hasMedium = json.has("medium")

                return if (hasSolid && !hasMedium) {
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)

                    if (solidValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else
                        Structure.error("Solid state in block $id is invalid!") // prevent so we default
                } else if (hasMedium && !hasSolid) {
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (mediumValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else
                        Structure.error("Medium state in block $id is invalid!") // prevent so we default
                } else { // has both (unless this gets called when the json has neither members, in which case, :sob:)
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (solidValid && mediumValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else if (!solidValid && !mediumValid) // neither valid
                        Structure.error("Neither medium or solid state in block $id is valid!")
                    else if (!solidValid) // medium is valid but solid isn't
                        Structure.error("Solid state in block $id is invalid!")
                    // solid states are kinda more important so if this is invalid we should just completely error instead of keeping the medium state.
                    else // solid is valid but medium isn't
                        Structure.warn(StructureType.BLOCK_COMPOUND, "Medium state in block $id is invalid")
                }
            }

            // some utility stuff for determining the main structure of the entry
            fun JsonObject.basicB(): Boolean = this.hasAny(blockValues)
            fun JsonObject.basicF(): Boolean = this.hasAny(fluidValues)
            fun JsonObject.compound(): Boolean = this.hasAny("solid", "medium")


            return when (idType) {
                IdType.BLOCK -> {
                    val toReturn: Structure = if (json.basicB()) {
                        StructureType.BLOCK_BASIC.toStructure() // basic structure, same as old version
                    } else if (json.compound()) {
                        determineCompoundBlockStructure(json, id)
                    } else if (json.has("states")) {
                        val states = json["states"].asJsonObject
                        // check to make sure we have a valid default state, otherwise return an error structure
                        if (!states.has("default"))
                            Structure.error("states object for block $id does not have a default state, which is required.")
                        if (!states["default"].asJsonObject.hasAny(blockValues) && !states["default"].asJsonObject.hasAny("solid", "medium"))
                            Structure.error("default state for block $id is not a valid format, but default is required.")
                        // uwu~ *notices your valid default state* o- oh!

                        val fullSize = states.size()

                        states.keySet().forEach {
                            if (!stateRegex.test(it)) {
                                logger.error("$it does not match regex for blockstate strings, skipping.")
                                states.remove(it) // remove invalid states completely
                            }
                        }

                        if (states.size() == 1) // size is one (only default)
                            if (fullSize > 1) { // and the previous size was larger, meaning we removed all states save for default state
                                Structure.warn(StructureType.BLOCK_STATES, "The default state in block $id is the only valid state!")
                            } else {
                                StructureType.BLOCK_STATES.toStructure()
                            }

                        val state2StructureType = mutableMapOf<String, StructureType>()
                        states.asMap().forEach { (state, json) ->
                            json as JsonObject
                            if (json.basicB())
                                state2StructureType[state] = StructureType.BLOCK_BASIC
                            else if (json.compound()) {
                                val structure = determineCompoundBlockStructure(json, "$id[$state]")

                                if (structure.isError()) { // since this is only one state we just discard the state instead of the whole thing if it errors
                                    logger.error("error while parsing $id[$state]: ${structure.error}")
                                    states.remove(state)
                                } else if (structure.isWarn()) {
                                    logger.warn("warning while parsing $id[$state]: ${structure.warn}")
                                    state2StructureType[state] = structure.type
                                } else {
                                    state2StructureType[state] = structure.type
                                }
                            } else {
                                logger.error("invalid format for $id[$state], skipping this state")
                                states.remove(state)
                            }
                        }

                        Structure.error("Could not determine structure for block $id") // sowwy >.<
                    } else {
                        Structure.error("Could not determine structure for block $id") // sowwy >.<
                    }
                    toReturn
                }
                IdType.FLUID -> {
                    Structure.error("Could not determine structure for fluid $id")
                }
                IdType.BLOCK_TAG -> {
                    Structure.error("Could not determine structure for block tag $id")
                }
                IdType.FLUID_TAG -> {
                    Structure.error("Could not determine structure for fluid tag $id")
                }
                IdType.NONE -> Structure.error("how") // this shouldn't be possible but we're checking anyways because i have anxiety
            }
        }

        private fun parseShapeSingle(jsonArray: JsonArray): AABBic {
            return AABBi(jsonArray[0].asInt, jsonArray[1].asInt, jsonArray[2].asInt, jsonArray[3].asInt, jsonArray[4].asInt, jsonArray[5].asInt)
        }

        private fun parseShape(jsonArray: JsonArray?): AABBic? = if (jsonArray != null) if (jsonArray.size() == 6) try { parseShapeSingle(jsonArray) } catch (e: Exception) { null } else null else null // fuck you.

        private fun parse(element: JsonElement, origin: ResourceLocation, index: Int = -1) {
            val json: JsonObject = element.asJsonObject
            val idType = determineId(json)

            if (idType == IdType.NONE) {
                var message = "Error parsing $origin: Could not find member for a valid fluid, block, or tag id"
                if (index >= 0) {
                    message += " in element $index" // tell the user which element this error occurred in
                }
                logger.error(message)
                return
            }

            val id = idType.getId(json)

            val priority = json["priority"]?.asInt ?: decideDefaultPriority(origin)

        }

        private fun parseBlock(json: JsonObject, priority: Int): SolidStateProperties {
            val mass = json["mass"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockMass
            val friction = json["friction"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockFriction
            val elasticity = json["elasticity"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockElasticity
            val hardness = json["hardness"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockHardness
            val noCollision = json["no_collision"]?.asBoolean ?: false

            val shapeOverrideJson = json["shape_override"]
            val shapeOverride = parseShape(json["shape_override"]?.asJsonArray).let { vsCore.solidShapeUtils.generateShapeFromBoxes(mutableListOf(parseShapeSingle(shapeOverrideJson.asJsonArray))) }

            return SolidStateProperties(priority, mass, friction, elasticity, hardness, noCollision, shapeOverride)
        }

        private fun parseMedium(json: JsonObject, priority: Int): MediumStateProperties {
            val dragCoefficient = json["drag"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockElasticity
            val shape = parseShape(json["shape"]?.asJsonArray)

            return MediumStateProperties(priority, dragCoefficient, shape)
        }

        private fun parseFluid(json: JsonObject, priority: Int): LiquidStateProperties {
            val density = json["density"]?.asDouble ?: VSGameConfig.SERVER.defaultLiquidDensity
            val dragCoefficient = json["drag"]?.asDouble ?: VSGameConfig.SERVER.defaultLiquidDragCoefficient

            val velocityArray = json["velocity"]?.asJsonArray
            val velocity = if (velocityArray != null) {
                Vector3d(velocityArray[0].asDouble, velocityArray[1].asDouble, velocityArray[2].asDouble)
            } else {
                Vector3d(
                    VSGameConfig.SERVER.defaultLiquidVelocityX,
                    VSGameConfig.SERVER.defaultLiquidVelocityY,
                    VSGameConfig.SERVER.defaultLiquidVelocityZ,
                )
            }

            val noCollision = json["no_collision"]?.asBoolean ?: false
            val shapeOverride = parseShape(json["shape_override"]?.asJsonArray)

            return LiquidStateProperties(priority, density, dragCoefficient, velocity, noCollision, shapeOverride)
        }
    }

    fun getFluidState(fluidState: FluidState): LiquidState {
        val fluidHeight = if (fluidState.isSource) {
            15
        } else {
            ((fluidState.ownHeight * 16.0).roundToInt() - 1).coerceIn(0, 15)
        }
        val fluidBox = AABBi(0, 0, 0, 15, fluidHeight, 15)

        return vsCore.newLiquidStateBuilder()
            .boxShape(fluidBox)
            .build()
    }

    enum class Composition {
        SOLID,
        MIXED,
        LIQUID
    }

    fun getComposition(blockState: BlockState): Composition {
        val hasFluid = !blockState.fluidState.isEmpty

        val collisionShape = BlockShapeUtil.getCollisionShape(blockState)
        val outlineShape = BlockShapeUtil.getShape(blockState)
        val isSolid = !collisionShape.isEmpty || !outlineShape.isEmpty

        return when {
            isSolid && hasFluid -> Composition.MIXED
            hasFluid -> Composition.LIQUID

            else -> Composition.SOLID
        }
    }

    fun buildMediumState(dragCoefficient: Double, shape: LiquidBlockShape): LiquidState {
        return vsCore.newLiquidStateBuilder()
            .density(0.0)
            .dragCoefficient(dragCoefficient)
            .shape(shape)
            .velocity(Vector3d())
            .build()
    }


    fun buildMediumState(dragCoefficient: Double, shape: AABBic): LiquidState {
        return vsCore.newLiquidStateBuilder()
            .density(0.0)
            .dragCoefficient(dragCoefficient)
            .boxShape(shape)
            .velocity(Vector3d())
            .build()
    }

    fun registerAllBlockStates(blockStates: Iterable<BlockState>) {
        val voxelShapeToSolidShape: MutableMap<VoxelShape, SolidBlockShape?> = HashMap(BlockShapeUtil.generateCommonShapes())

        blockStates.forEach { blockState ->
            val vsiBlockState: VsiBlockState
            if (blockState.isAir) {
                vsiBlockState = vsCore.blockTypes.airState
            } else {
                val composition: Composition = getComposition(blockState)
                val voxelShape = BlockShapeUtil.getShapeForVS(blockState)

                val solidState: SolidState
                val liquidState: LiquidState
                val mediumState: LiquidState // i need access to physics_api and physics_api_krunch to fully implement MediumState so rn we're just using a liquid instead

                when (composition) {
                    Composition.SOLID -> {

                    }
                    Composition.MIXED -> {

                    }
                    Composition.LIQUID -> {

                    }
                }
            }


        }

    }

    fun JsonObject.hasAny(vararg members: String): Boolean {
        members.forEach {
            if (this.has(it)) return true
        }
        return false
    }

    fun JsonObject.hasAny(members: Iterable<String>): Boolean {
        members.forEach {
            if (this.has(it)) return true
        }
        return false
    }

    private val logger by logger()

}
