package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.LiquidBlockShape
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.DisplacementState
import org.valkyrienskies.core.api.physics.blockstates.MediumState
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.config.MassDatapackResolver.decideDefaultPriority
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.logger
import oshi.util.tuples.Pair
import java.util.function.Predicate
import java.util.regex.Pattern
import kotlin.math.roundToInt

/**
 * @see [SolidState]
 */
data class SolidStateProperties (
    val mass: Double,
    val friction: Double,
    val elasticity: Double,
    val hardness: Double,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SolidStateProperties) return false

        return mass == other.mass && friction == other.friction && elasticity == other.elasticity && hardness == other.hardness && noCollision == other.noCollision && shapeOverride == other.shapeOverride
    }

    override fun hashCode(): Int { // hashbrowns are good but have you ever tried hashcodes
        var result = mass.hashCode()
        result = 31 * result + friction.hashCode()
        result = 31 * result + elasticity.hashCode()
        result = 31 * result + hardness.hashCode()
        result = 31 * result + noCollision.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): SolidStateProperties = SolidStateProperties(
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
    val density: Double,
    val dragCoefficient: Double,
    val velocity: Vector3d,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidStateProperties) return false

        return density == other.density && dragCoefficient == other.dragCoefficient && velocity == other.velocity && noCollision == other.noCollision && shapeOverride == other.shapeOverride
    }

    override fun hashCode(): Int {
        var result = density.hashCode()
        result = 31 * result + dragCoefficient.hashCode()
        result = 31 * result + noCollision.hashCode()
        result = 31 * result + velocity.hashCode()
        result = 31 * result + shapeOverride.hashCode()
        return result
    }

    companion object {
        fun defaultProperties(): LiquidStateProperties = LiquidStateProperties(
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

/**
 * @see [DisplacementState]
 */
data class DisplacementStateProperties (val shape: AABBic? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplacementStateProperties) return false

        return shape == other.shape
    }

    override fun hashCode(): Int {
        return shape.hashCode()
    }

    companion object {
        fun defaultProperties(): DisplacementStateProperties = DisplacementStateProperties()
    }
}

/**
 * @see [MediumState]
 */
data class MediumStateProperties (
    val dragCoefficient: Double,
    val shape: AABBic? = null
) {
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
        fun defaultProperties(): MediumStateProperties = MediumStateProperties(VSGameConfig.SERVER.defaultLiquidDragCoefficient)
    }
}

// todo reminder to myself to remove this class
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

data class BlockStateProperties(
    val priority: Int,
    val solid: SolidStateProperties? = null,
    val liquid: LiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: MediumStateProperties? = null,
)

data class TagProperties(
    val priority: Int,
    val properties: BlockStateProperties,
    val exclusions: Set<String>?
)

/**
 * todo this is a reminder to myself to write docs for the mass system
 */
object BlockStateInfoResolver { // yall better be happy with this because i ain't touching this file for the rest of my life after this
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, BlockStateProperties>> = HashMap()
    private val tag2Properties: MutableMap<ResourceLocation, TagProperties> = HashMap()
    private val mcState2VsState: MutableMap<BlockState, VsiBlockState> = HashMap()

    val loader get() = BlockStateInfoDataLoader()

    fun serializeFluid(fluidState: FluidState): String {
        val stringBuilder = StringBuilder(fluidState.holder().unwrapKey().map { key -> key.location().toString() }.orElse("empty"))
        if (fluidState.properties.isNotEmpty()) {
            stringBuilder.append('[')
            var afterFirst = false

            // i would like a better solution to this but java and kotlin wildcards are different so i can't do it the same way BlockStateParser does
            fun <T : Comparable<T>> appendProperty(property: Property<T>, comparable: Any) {
                stringBuilder.append(property.name)
                stringBuilder.append('=')
                @Suppress("UNCHECKED_CAST")
                stringBuilder.append(property.getName(comparable as T))
            }

            for ((property, value) in fluidState.values.entries) {
                if (afterFirst) {
                    stringBuilder.append(',')
                }

                appendProperty(property, value)
                afterFirst = true
            }

            stringBuilder.append(']')
        }

        return stringBuilder.toString()
    }


    fun blockStateToString(blockState: BlockState) = blockStateToString(BlockStateParser.serialize(blockState))

    fun blockStateToString(raw: String): Pair<ResourceLocation, String> {
        if (raw.indexOf('[') == -1) {
            return Pair(ResourceLocation(raw), "default")
            // if a blockstate has no properties, the parser will not append the brackets to it, so we can use that as a check
            // since indexOf returns -1 if the char does not exist in the string.
        }
        val id = ResourceLocation(raw.substring(0, raw.indexOf('[')))
        val properties = raw.substring(raw.indexOf('[') + 1, raw.indexOf(']'))
        return Pair(id, properties)
    }

    fun getProperties(blockState: BlockState): BlockStateProperties? {
        val string = blockStateToString(blockState)
        return blockState2Properties[string.a]?.get(string.b)
    }

    fun getProperties(raw: String): BlockStateProperties? {
        val string = blockStateToString(raw)
        return blockState2Properties[string.a]?.get(string.b)
    }

    fun BlockState.getBlockType(): VsiBlockType? {
        val vsState = mcState2VsState[this] ?: return null
        return vsCore.blockTypes.getType(vsState)
    }

    class BlockStateInfoDataLoader : SimpleJsonResourceReloadListener(Gson(), "vs_mass") {
        override fun apply(objects: MutableMap<ResourceLocation, JsonElement>, resourceManager: ResourceManager, profilerFiller: ProfilerFiller) {
            objects.forEach { (location, element) ->
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

        class MassJsonParseException(override val message: String, val id: String? = null) : Exception(message) {
            fun getParseError(): String {
                return if (id == null) {
                    message
                } else {
                    "Parsing exception for $id: $message"
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
            BLOCK_TAG_BASIC,
            BLOCK_TAG_COMPOUND,
            FLUID_TAG_BASIC
            ;

            fun toStructure(): Structure {
                return Structure(this)
            }
        }

        /**
         * Represents the structure of a block/fluid property entry.
         *
         * @param type The [StructureType] of this Structure.
         * @param warn If this structure is still valid but has a warning, this is the message.
         * @param state2StructureType If this structure's type is [StructureType.BLOCK_STATES] or
         * [StructureType.FLUID_STATES], this is a map of each state to its structure, allowing for differing structures of states if needed.
         */
        class Structure(val type: StructureType, val warn: String? = null, val state2StructureType: Map<String, StructureType>? = null) {
            fun isWarn(): Boolean {
                return warn != null
            }

            companion object {
                /**
                 * Indicates that a problem has occurred with the entry, but it should be safe to parse.
                 * This means that when we return the warning, we should also have **fixed the problem**.
                 */
                fun warn(type: StructureType, string: String): Structure {
                    return Structure(type, warn = string)
                }
            }
        }

        // matches if the string is "default", or if it matches "key=value,key2=value2,etc"
        val stateRegex: Predicate<String> = Pattern.compile("^(default|\\w+=[^,=]+(,\\w+=[^,=]+)*)$").asPredicate()
        // matches the format of a resource location ("namespace:path")
        val resourceRegex: Predicate<String> = Pattern.compile("^[a-z_]+:[a-z_]+$").asPredicate()

        val blockValues = listOf("mass", "friction", "elasticity", "hardness", "no_collision", "shape_override")
        val mediumValues = listOf("drag", "shape")
        val fluidValues = listOf("density", "drag", "velocity", "no_collision", "shape_override")
        // displacement state values are just "shape"

        /**
         * Determine the [IdType] for a property entry
         */
        private fun determineId(json: JsonObject): IdType {
            return when {
                json.has("block") -> IdType.BLOCK
                json.has("fluid") -> IdType.FLUID
                json.has("tag") || json.has("block_tag") -> IdType.BLOCK_TAG
                json.has("fluid_tag") -> IdType.FLUID_TAG
                else -> IdType.NONE
            }
        }

        /**
         * Determine the [Structure] of a property entry.
         */
        private fun determineStructure(json: JsonObject, idType: IdType, id: String): Structure{
            fun determineCompoundBlockStructure(json: JsonObject, id: String): Structure {
                val hasSolid = json.has("solid")
                val hasMedium = json.has("medium")

                return if (hasSolid && !hasMedium) {
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)

                    if (solidValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else
                        throw MassJsonParseException("Solid state is invalid!", id) // prevent so we default
                } else if (hasMedium && !hasSolid) {
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (mediumValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else
                        throw MassJsonParseException("Medium state is invalid!", id) // prevent so we default
                } else { // has both (unless this gets called when the json has neither members, in which case, :sob:)
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (solidValid && mediumValid)
                        StructureType.BLOCK_COMPOUND.toStructure()
                    else if (!solidValid && !mediumValid) // neither valid
                        throw MassJsonParseException("Neither medium or solid state is valid!", id)
                    else if (!solidValid) // medium is valid but solid isn't
                        throw MassJsonParseException("Medium state is valid, but solid state is invalid!", id)
                    // solid states are kinda more important so if this is invalid we should just completely error instead of keeping the medium state.
                    else // solid is valid but medium isn't
                        json.remove("medium")
                        Structure.warn(StructureType.BLOCK_COMPOUND, "Medium state in block $id is invalid, but solid state is.")
                }
            }

            // some utility stuff for determining the main structure of the entry
            fun JsonObject.basic(block: Boolean = true): Boolean = if (block) this.hasAny(blockValues) else this.hasAny(fluidValues)
            fun JsonObject.compound(): Boolean = this.hasAny("solid", "medium")

            return when (idType) {
                IdType.BLOCK -> {
                    val toReturn: Structure = if (json.basic()) {
                        StructureType.BLOCK_BASIC.toStructure() // basic structure, same as old version
                    } else if (json.compound()) {
                        determineCompoundBlockStructure(json, id)
                    } else if (json.has("states")) {
                        val states = json["states"].asJsonObject
                        // check to make sure we have a valid default state, otherwise return an error structure
                        // technically this check will succeed if the default state has solid or medium but the internal format of those is invalid, we'll just do that check later.
                        if (!states.has("default"))
                            throw MassJsonParseException("states object does not have a default state, which is required.", id)
                        if (!states["default"].asJsonObject.hasAny(blockValues) && !states["default"].asJsonObject.hasAny("solid", "medium"))
                            throw MassJsonParseException("default state is not in a valid format, but default is required.", id)
                        // uwu~ *notices your valid default state* o- oh!

                        val fullSize = states.size()

                        states.keySet().forEach {
                            if (!stateRegex.test(it)) {
                                logger.error("$it does not match regex for blockstate strings, skipping.")
                                states.remove(it) // remove invalid states completely
                            }
                        }

                        val state2StructureType = mutableMapOf<String, StructureType>()
                        states.asMap().forEach { (state, json) ->
                            json as JsonObject
                            if (json.basic())
                                state2StructureType[state] = StructureType.BLOCK_BASIC
                            else if (json.compound()) {
                                try {
                                    val structure = determineCompoundBlockStructure(json, "$id[$state]")

                                    if (structure.isWarn())
                                        logger.warn("warning while parsing blockstate $id[$state]: ${structure.warn}")

                                    state2StructureType[state] = structure.type
                                } catch (parseE: MassJsonParseException) {
                                    logger.error(parseE.getParseError())
                                    states.remove(state)
                                }
                            } else { // state format isn't valid here for obvious reasons
                                logger.error("invalid format for $id[$state], skipping this block state")
                                states.remove(state)
                            }
                        }

                        if (states.size() == 0) {
                            throw MassJsonParseException("No valid block states for $id!")
                        }

                        Structure(StructureType.BLOCK_STATES, warn = if (states.size() == 1 && fullSize > 1) "The default state in block $id is the only valid state!" else null, state2StructureType = state2StructureType)
                    } else throw MassJsonParseException("Could not determine block structure", id) // sowwy >.<
                    toReturn
                }
                IdType.FLUID -> {
                    val toReturn: Structure = if (json.basic(false)) {
                        StructureType.FLUID_BASIC.toStructure()
                    } else if (json.has("states")) {
                        val states = json["states"].asJsonObject
                        if (!states.has("default"))
                            throw MassJsonParseException("states object does not have a default fluid state, which is required.", id)
                        if (!states["default"].asJsonObject.basic(false))
                            throw MassJsonParseException("default state is not in a valid format, but default is required.", id)

                        val fullSize = states.size()

                        states.keySet().forEach {
                            if (!stateRegex.test(it)) {
                                logger.error("$it does not match regex for fluidstate strings, skipping.")
                                states.remove(it)
                            }
                        }

                        val state2StructureType = mutableMapOf<String, StructureType>()
                        states.asMap().forEach { (state, json) ->
                            json as JsonObject
                            if (json.basic()) {
                                state2StructureType[state] = StructureType.FLUID_BASIC
                            } else {
                                logger.error("invalid format for $id[$state], skipping this fluidstate")
                                states.remove(state)
                            }
                        }

                        if (states.size() == 0) {
                            throw MassJsonParseException("No valid fluid states for $id!")
                        }

                        Structure(StructureType.FLUID_STATES, warn = if (states.size() == 1 && fullSize > 1) "The default state in fluid $id is the only valid state!" else null, state2StructureType = state2StructureType)
                    } else
                        throw MassJsonParseException("Could not determine fluid structure!", id)
                    toReturn
                }
                IdType.BLOCK_TAG -> { // todo implement these
                    throw MassJsonParseException("Could not determine block tag structure!", id)
                }
                IdType.FLUID_TAG -> {
                    throw MassJsonParseException("Could not determine fluid tag structure!", id)
                }
                IdType.NONE -> throw MassJsonParseException("how") // this shouldn't be possible but we're checking anyways because i have anxiety
            }
        }

        /**
         * Parses a single entry for a block or fluid.
         */
        private fun parse(element: JsonElement, origin: ResourceLocation, index: Int = -1) {
            val json: JsonObject = element.asJsonObject
            val idType = determineId(json)

            if (idType == IdType.NONE) {
                var message = "error parsing $origin: Could not find member for a valid fluid, block, or tag id"
                if (index != -1) {
                    message += " in element $index" // tell the user which element this error occurred in
                }
                logger.error(message)
                return
            }

            val id = idType.getId(json)
            if (!resourceRegex.test(id)) {
                logger.error("error while parsing entry: $id is not a valid id!")
                return
            }

            val priority = json["priority"]?.asInt ?: decideDefaultPriority(origin)

            val structure: Structure

            try {
                structure = determineStructure(json, idType, id)
            } catch (parseE: MassJsonParseException) {
                logger.error(parseE.getParseError()) // parseE jackson
                return
            }

            if (structure.isWarn())
                logger.warn("warning while parsing entry for $id: ${structure.warn}")
            // all clear

            /**
             * add properties to the map for a given block.
             * creates a new [MutableMap] for the given [id] if one is not already present, then computes the value for [key].
             * if the value associated with the given key is null, we put [propertiesToPut] to that key.
             * if there is a value associated with the given key, we compare the priority of both and put [propertiesToPut] if its priority is higher than the existing value.
             */
            fun putProperties(id: String, key: String, propertiesToPut: BlockStateProperties) {
                blockState2Properties.computeIfAbsent(ResourceLocation.of(id, ':')) { mutableMapOf() }.compute(key) { _, properties ->
                    val toPut: BlockStateProperties = if (properties == null) // we should grade code by number of different colors per line
                        propertiesToPut
                    else
                        if (propertiesToPut.priority > properties.priority)
                            propertiesToPut
                        else
                            propertiesToPut
                    toPut
                }
            }

            when (structure.type) {
                StructureType.BLOCK_BASIC -> {
                    putProperties(id, "default", BlockStateProperties(priority, parseSolid(json)))
                }
                StructureType.BLOCK_COMPOUND -> {
                    putProperties(id, "default", BlockStateProperties(priority,
                        solid = if (json.has("solid")) parseSolid(json.getAsJsonObject("solid")) else null,
                        medium = if (json.has("medium")) parseMedium(json.getAsJsonObject("medium")) else null
                    ))
                }
                StructureType.BLOCK_STATES -> {
                    structure.state2StructureType!!.forEach { (state, type) ->
                        when (type) {
                            StructureType.BLOCK_BASIC -> {
                                putProperties(id, state, BlockStateProperties(priority, parseSolid(json.getAsJsonObject(state))))
                            }
                            StructureType.BLOCK_COMPOUND -> {
                                val stateJson = json.getAsJsonObject(state)
                                putProperties(id, "default", BlockStateProperties(priority,
                                    solid = if (stateJson.has("solid")) parseSolid(stateJson.getAsJsonObject("solid")) else null,
                                    medium = if (stateJson.has("medium")) parseMedium(stateJson.getAsJsonObject("medium")) else null
                                ))
                            }
                            else -> {
                                logger.error("what are you doing this should be impossible to reach what (id: $id, index: $index, origin: $origin)")
                            }
                        }
                    }
                }
                StructureType.FLUID_BASIC -> {
                    putProperties(id, "default", BlockStateProperties(priority, liquid = parseLiquid(json)))
                }
                StructureType.FLUID_STATES -> { // type really doesn't matter here because fluids can only be parsed one way, but i don't feel like adding a list of states to this, map works just fine
                    structure.state2StructureType!!.keys.forEach { state ->
                        putProperties(id, state, BlockStateProperties(priority, liquid = parseLiquid(json.getAsJsonObject(state))))
                    }
                }
                StructureType.BLOCK_TAG_BASIC -> TODO()
                StructureType.BLOCK_TAG_COMPOUND -> TODO()
                StructureType.FLUID_TAG_BASIC -> TODO()
            }
        }

        private fun parseSolid(json: JsonObject): SolidStateProperties {
            val mass = json["mass"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockMass
            val friction = json["friction"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockFriction
            val elasticity = json["elasticity"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockElasticity
            val hardness = json["hardness"]?.asDouble ?: VSGameConfig.SERVER.defaultBlockHardness // i know hardness isnt implemented yet really but im putting it anyways
            val noCollision = json["no_collision"]?.asBoolean ?: false

            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return SolidStateProperties(mass, friction, elasticity, hardness, noCollision, shapeOverride)
        }

        private fun parseMedium(json: JsonObject): MediumStateProperties {
            val dragCoefficient = json["drag"]?.asDouble ?: VSGameConfig.SERVER.defaultLiquidDragCoefficient
            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return MediumStateProperties(dragCoefficient, shapeOverride)
        }

        private fun parseLiquid(json: JsonObject): LiquidStateProperties {
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
            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return LiquidStateProperties(density, dragCoefficient, velocity, noCollision, shapeOverride)
        }

        private fun parseShape(jsonArray: JsonElement): AABBic? =
            if (jsonArray.isJsonArray) {
                jsonArray as JsonArray
                AABBi(jsonArray[0].asInt, jsonArray[1].asInt, jsonArray[2].asInt,
                    jsonArray[3].asInt, jsonArray[4].asInt, jsonArray[5].asInt)
            } else null
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


    fun JsonObject.hasAll(members: Iterable<String>): Boolean = members.all { has(it) }

    fun JsonObject.hasAny(members: Iterable<String>): Boolean = members.any { has(it) }

    fun JsonObject.returnAllPresent(members: Iterable<String>): List<String> = members.filter { has(it) }

    fun JsonObject.returnAllAbsent(members: Iterable<String>): List<String> = members.filter { !has(it) }


    fun JsonObject.hasAll(vararg members: String): Boolean = hasAll(members.asIterable())
    fun JsonObject.hasAny(vararg members: String): Boolean = hasAny(members.asIterable())
    fun JsonObject.returnAllPresent(vararg members: String): List<String> = returnAllPresent(members.asIterable())
    fun JsonObject.returnAllAbsent(vararg members: String): List<String> = returnAllAbsent(members.asIterable())

    fun <T> List<T>.readable(open: String = "[", close: String = "]"): String {
        var string: String = ""
        forEach { string += it.toString() }
        return open + string + close
    }

    private val logger by logger()

}
