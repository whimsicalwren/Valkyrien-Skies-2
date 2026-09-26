package org.valkyrienskies.mod.common.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.phys.shapes.VoxelShape
import org.jetbrains.annotations.ApiStatus.Internal
import org.joml.Vector3d
import org.joml.primitives.AABBi
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.physics.blockstates.LiquidState
import org.valkyrienskies.core.api.physics.blockstates.DisplacementState
import org.valkyrienskies.core.api.physics.blockstates.SolidBlockShape
import org.valkyrienskies.core.api.physics.blockstates.SolidState
import org.valkyrienskies.core.internal.physics.blockstates.VsiBlockState
import org.valkyrienskies.mod.api_impl.events.RegisterBlockStateEventImpl
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.config.MassDatapackResolver.decideDefaultPriority
import org.valkyrienskies.mod.common.networking.PacketSyncBlockStateProperties
import org.valkyrienskies.mod.common.util.BlockShapeUtil
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.logger
import oshi.util.tuples.Pair
import java.util.function.Predicate
import java.util.regex.Pattern
import kotlin.math.roundToInt
import kotlin.system.measureNanoTime

// massive fucking file oml

// region data classes
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
}

/**
 * @see [LiquidState]
 */
data class LiquidStateProperties (
    val density: Double,
    val dragCoefficient: Double,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiquidStateProperties) return false

        return density == other.density && dragCoefficient == other.dragCoefficient && velocity == other.velocity && shapeOverride == other.shapeOverride
    }

    override fun hashCode(): Int {
        var result = density.hashCode()
        result = 31 * result + dragCoefficient.hashCode()
        result = 31 * result + velocity.hashCode()
        result = 31 * result + shapeOverride.hashCode()
        return result
    }
}

/**
 * @see [DisplacementState]
 */
data class DisplacementStateProperties (
    val shape: AABBic? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DisplacementStateProperties) return false

        return shape == other.shape
    }

    override fun hashCode(): Int {
        return shape.hashCode()
    }
}

/**
 * Liquid state in a costume until MediumState gets implemented
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
}

data class BlockStateProperties (
    val priority: Int,
    val solid: SolidStateProperties? = null,
    val liquid: LiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: MediumStateProperties? = null,
)

data class PendingSolidStateProperties(
    val mass: NumericValue,
    val friction: NumericValue,
    val elasticity: NumericValue,
    val hardness: NumericValue,
    val noCollision: Boolean = false,
    val shapeOverride: AABBic? = null
)

data class PendingLiquidStateProperties(
    val density: NumericValue,
    val dragCoefficient: NumericValue,
    val velocity: Vector3d,
    val shapeOverride: AABBic? = null
)

data class PendingMediumStateProperties(
    val dragCoefficient: NumericValue,
    val shape: AABBic? = null
)

data class PendingBlockStateProperties(
    val priority: Int,
    val solid: PendingSolidStateProperties? = null,
    val liquid: PendingLiquidStateProperties? = null,
    val displacement: DisplacementStateProperties? = null,
    val medium: PendingMediumStateProperties? = null
)

data class PendingTagProperties(
    val properties: PendingBlockStateProperties,
    val exclude: Set<ResourceLocation>,
    val block: Boolean
)

sealed class NumericValue {
    data class Literal(val value: Double) : NumericValue()
    data class Dependent(val targetId: ResourceLocation, val targetState: String, val mult: Double) : NumericValue()
}
// endregion

/**
 * todo this is a reminder to myself to write docs for the mass system
 */
object BlockStateInfoResolver {
    private val blockState2Properties: MutableMap<ResourceLocation, MutableMap<String, BlockStateProperties>> = HashMap()
    private val pendingBlockState2Properties: MutableMap<ResourceLocation, MutableMap<String, PendingBlockStateProperties>> = HashMap()
    private val tag2Properties: MutableMap<ResourceLocation, PendingTagProperties> = HashMap()
    private val mcState2VsState: MutableMap<BlockState, VsiBlockState> = HashMap()

    val blockStateData: Collection<VsiBlockState> = mcState2VsState.values

    var hasRegistered = false
        private set

    val loader get() = BlockStateInfoDataLoader()

    fun getProperties(blockState: BlockState): BlockStateProperties? {
        val string = stateToString(blockState)
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getProperties(fluidState: FluidState): BlockStateProperties? {
        val string = stateToString(fluidState)
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    fun getProperties(id: ResourceLocation): BlockStateProperties? {
        return blockState2Properties[id]?.get("default")
    }

    fun getProperties(raw: String): BlockStateProperties? {
        val string = stateToString(raw)
        return blockState2Properties[string.a]?.getOrOther(string.b, "default")
    }

    /**
     * This is left public so that it can be called in [org.valkyrienskies.mod.mixin.server.MixinMinecraftServer]
     * **Do not call this method otherwise!**
     */
    @Internal
    fun registerAllBlockStates(blockStates: Iterable<BlockState>) {
        val voxelShapeToSolidShape: MutableMap<VoxelShape, SolidBlockShape> = HashMap(BlockShapeUtil.generateCommonShapes())

        fun generateShape(voxelShape: VoxelShape): SolidBlockShape =
            if (voxelShapeToSolidShape.contains(voxelShape)) {
                voxelShapeToSolidShape[voxelShape]!!
            } else {
                val generatedShape = BlockShapeUtil.generateShapeFromVoxel(voxelShape)
                if (generatedShape != null) {
                    voxelShapeToSolidShape[voxelShape] = generatedShape
                    generatedShape
                } else {
                    BlockShapeUtil.fullBlockCollisionShape
                }
            }
        fun buildDefaultSolidState(voxelShape: VoxelShape) =
            vsCore.newSolidStateBuilder()
                .shape(generateShape(voxelShape))
                .mass(VSGameConfig.SERVER.blockProperties.defaultBlockMass)
                .friction(VSGameConfig.SERVER.blockProperties.defaultBlockFriction)
                .elasticity(VSGameConfig.SERVER.blockProperties.defaultBlockElasticity)
                .hardness(VSGameConfig.SERVER.blockProperties.defaultBlockHardness)
                .build()
        fun buildDefaultLiquidState(shape: AABBic) =
            vsCore.newLiquidStateBuilder()
                .boxShape(shape)
                .density(VSGameConfig.SERVER.blockProperties.defaultLiquidDensity)
                .dragCoefficient(VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)
                .velocity(Vector3d())
                .build()

        blockStates.forEach { blockState ->
            val vsiBlockState: VsiBlockState
            if (blockState.isAir) {
                vsiBlockState = vsCore.blockTypes.airState
            } else {
                val composition: Composition = getComposition(blockState)
                val voxelShape = BlockShapeUtil.getShapeForVS(blockState)
                val props = getProperties(blockState)
                val fluidState = blockState.fluidState

                fun getSolidState() = if (props?.solid != null) {
                    val shape: SolidBlockShape =
                        if (props.solid.noCollision) BlockShapeUtil.noCollisionShape
                        else if (props.solid.shapeOverride != null)
                            vsCore.solidShapeUtils.generateShapeFromBoxes(mutableListOf(props.solid.shapeOverride)) ?: generateShape(voxelShape)
                        else generateShape(voxelShape)

                    vsCore.newSolidStateBuilder()
                        .shape(shape)
                        .mass(props.solid.mass)
                        .friction(props.solid.friction)
                        .elasticity(props.solid.elasticity)
                        .hardness(props.solid.hardness)
                        .build()
                } else buildDefaultSolidState(voxelShape)

                // todo change liquid stuff here to use LiquidBlockShape instead of AABBi?
                fun getLiquidState() = if (props?.liquid != null) {
                    val shape: AABBic = props.liquid.shapeOverride ?: fluidBox(fluidState)

                    vsCore.newLiquidStateBuilder()
                        .boxShape(shape)
                        .density(props.liquid.density)
                        .dragCoefficient(props.liquid.dragCoefficient)
                        .velocity(props.liquid.velocity)
                        .build()
                } else buildDefaultLiquidState(fluidBox(fluidState))

                when (composition) {
                    Composition.SOLID -> {
                        val mediumState = if (props?.medium != null) {
                            buildMediumState(props.medium.dragCoefficient, props.medium.shape ?: BlockShapeUtil.fullLodBoundingBox)
                        } else null

                        vsiBlockState = VsiBlockState(getSolidState(), mediumState)
                    }
                    Composition.MIXED -> {
                        vsiBlockState = VsiBlockState(getSolidState(), getLiquidState())
                    }
                    Composition.LIQUID -> {
                        vsiBlockState = VsiBlockState(null, getLiquidState())
                    }
                }
            }
            mcState2VsState[blockState] = vsiBlockState
        }

        val event = RegisterBlockStateEventImpl()
        ValkyrienSkiesMod.api.registerBlockStateEvent.emit(event)
        mcState2VsState.putAll(event.toRegister)
    }

    fun syncBlockStates(player: MinecraftPlayer) {
        logger.info("Syncing ${mcState2VsState.size} blockstates to ${player.uuid}")
        with(vsCore.simplePacketNetworking) {
            val packetMap = blockState2Properties.mapKeys { it.key.toString() }
            PacketSyncBlockStateProperties(
                packetMap,
                VSGameConfig.SERVER.blockProperties.defaultBlockMass,
                VSGameConfig.SERVER.blockProperties.defaultBlockFriction,
                VSGameConfig.SERVER.blockProperties.defaultBlockElasticity,
                VSGameConfig.SERVER.blockProperties.defaultLiquidDensity
            ).sendToClient(player)
        }
    }

    fun clearBlockStates(player: MinecraftPlayer) {
        logger.info("Clearing synced blockstates from ${player.uuid}")
        with(vsCore.simplePacketNetworking) {
            PacketSyncBlockStateProperties().sendToClient(player)
        }
    }

    fun loadTags() {
        logger.info("Loading tag entries.")
        var tagsLoaded = 0
        var blocksLoaded = 0
        tag2Properties.forEach { (tagId, tagProperties) ->
            val tag = if (tagProperties.block) {
                BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, tagId))
            } else {
                BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tagId))
            }

            if (tag != null) {
                if (!tag.isPresent) {
                    // todo: i am going to make a pr to improve handing compat and installed mods, with it i will add a way to check loaded mods, and change this so that it only warns when the namespace is one of a loaded mod
                    // avoid logspam of the previous loader by only warning for minecraft tags (see above)
                    if (tagId.namespace == "minecraft") {
                        logger.warn("Tag '$tagId' does not exist!")
                    }
                    return@forEach
                }

                tagsLoaded++

                tag.get().forEach {
                    val id = if (tagProperties.block)
                        BuiltInRegistries.BLOCK.getKey(it.value() as Block)
                    else
                        BuiltInRegistries.FLUID.getKey(it.value() as Fluid)
                    if (!tagProperties.exclude.contains(id)) {
                        blocksLoaded++
                        putPendingProperties(id, "default", tagProperties.properties)
                    }
                }
            }
        }

        logger.info("Loaded $tagsLoaded tag entries (properties for $blocksLoaded blocks).")
        resolveAll()
    }

    // region resolve pending values -> actual values
    /**
     * Resolve all values.
     * **This can take extremely long if there are deep dependency chains!**
     */
    private fun resolveAll() {
        if (pendingBlockState2Properties.isEmpty()) return
        logger.info("Resolving all values. This may take a while if there are a large amount of dependent values!")

        var unresolvedCount = 0
        val elapsedNanos = measureNanoTime {
            blockState2Properties.clear()

            var unresolved = pendingBlockState2Properties.flatMap { (id, states) ->
                states.map { (state, props) -> Triple(id, state, props) }
            }

            var progress = true
            while (unresolved.isNotEmpty() && progress) {
                progress = false
                val stillUnresolved = mutableListOf<Triple<ResourceLocation, String, PendingBlockStateProperties>>()

                for ((id, state, pending) in unresolved) {
                    val resolved = resolve(pending, false)
                    if (resolved != null) {
                        putProperties(id, state, resolved)
                        progress = true
                    } else {
                        stillUnresolved.add(Triple(id, state, pending))
                    }
                }
                unresolved = stillUnresolved
            }

            unresolved.forEach { (id, state, pending) ->
                logger.error("couldn't fully resolve dependent values for $id[$state] due to a circular or missing reference, falling back to defaults for unresolved fields.")
                putProperties(id, state, resolve(pending, true)!!) // force guarantees a non-null value
            }

            unresolvedCount = unresolved.size
            pendingBlockState2Properties.clear()
        }

        logger.info("Resolving all values took %.3f seconds (%d unresolved).".format(elapsedNanos / 1_000_000_000.0, unresolvedCount))
    }

    private fun resolve(pending: PendingBlockStateProperties, force: Boolean): BlockStateProperties? {
        val solid = pending.solid?.let { resolveSolid(it, force) ?: return null }
        val liquid = pending.liquid?.let { resolveLiquid(it, force) ?: return null }
        val medium = pending.medium?.let { resolveMedium(it, force) ?: return null }
        return BlockStateProperties(pending.priority, solid, liquid, pending.displacement, medium)
    }

    private fun resolveSolid(p: PendingSolidStateProperties, force: Boolean): SolidStateProperties? {
        val mass = resolveValue(p.mass, VSGameConfig.SERVER.blockProperties.defaultBlockMass, force) { it.solid?.mass } ?: return null
        val friction = resolveValue(p.friction, VSGameConfig.SERVER.blockProperties.defaultBlockFriction, force) { it.solid?.friction } ?: return null
        val elasticity = resolveValue(p.elasticity, VSGameConfig.SERVER.blockProperties.defaultBlockElasticity, force) { it.solid?.elasticity } ?: return null
        val hardness = resolveValue(p.hardness, VSGameConfig.SERVER.blockProperties.defaultBlockHardness, force) { it.solid?.hardness } ?: return null
        return SolidStateProperties(mass, friction, elasticity, hardness, p.noCollision, p.shapeOverride)
    }

    private fun resolveLiquid(p: PendingLiquidStateProperties, force: Boolean): LiquidStateProperties? {
        val density = resolveValue(p.density, VSGameConfig.SERVER.blockProperties.defaultLiquidDensity, force) { it.liquid?.density } ?: return null
        val drag = resolveValue(p.dragCoefficient, VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient, force) { it.liquid?.dragCoefficient } ?: return null
        return LiquidStateProperties(density, drag, p.velocity, p.shapeOverride)
    }

    private fun resolveMedium(p: PendingMediumStateProperties, force: Boolean): MediumStateProperties? {
        val drag = resolveValue(p.dragCoefficient, VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient, force) { it.medium?.dragCoefficient } ?: return null
        return MediumStateProperties(drag, p.shape)
    }

    private fun resolveValue(value: NumericValue, default: Double, force: Boolean, extractor: (BlockStateProperties) -> Double?): Double? = when (value) {
        is NumericValue.Literal -> value.value
        is NumericValue.Dependent -> {
            val target = blockState2Properties[value.targetId]?.getOrOther(value.targetState, "default")
            val resolved = target?.let(extractor)?.let { it * value.mult }
            when {
                resolved != null -> resolved
                force -> {
                    logger.error("could not resolve dependent value pointing at ${value.targetId}[${value.targetState}], using default $default.")
                    default
                }
                else -> null
            }
        }
    }
    // endregion

    // region utility functions
    /**
     * add properties to the map for a given block.
     * creates a new [MutableMap] for the given [id] if one is not already present, then computes the value for [key].
     * if the value associated with the given key is null, we put [propertiesToPut] to that key.
     * if there is a value associated with the given key, we compare the priority of both and put [propertiesToPut] if its priority is higher than the existing value.
     */
    private fun putProperties(id: ResourceLocation, key: String, propertiesToPut: BlockStateProperties) {
        blockState2Properties.computeIfAbsent(id) { mutableMapOf() }.compute(key) { _, properties ->
            if (properties == null)
                propertiesToPut
            else {
                if (propertiesToPut.priority > properties.priority) propertiesToPut
                else properties
            }
        }
    }

    private fun putPendingProperties(id: ResourceLocation, key: String, propertiesToPut: PendingBlockStateProperties) {
        pendingBlockState2Properties.computeIfAbsent(id) { mutableMapOf() }.compute(key) { _, properties ->
            if (properties == null) propertiesToPut
            else if (propertiesToPut.priority > properties.priority) propertiesToPut
            else properties
        }
    }

    private fun putPendingProperties(id: String, key: String, propertiesToPut: PendingBlockStateProperties) {
        putPendingProperties(ResourceLocation.of(id, ':'), key, propertiesToPut)
    }

    private fun putTagProperties(id: String, propertiesToPut: PendingTagProperties) {
        tag2Properties.compute(ResourceLocation.of(id, ':')) { _, properties ->
            if (properties == null) propertiesToPut
            else if (propertiesToPut.properties.priority > properties.properties.priority) propertiesToPut
            else properties
        }
    }

    fun stateToString(blockState: BlockState) = stateToString(BlockStateParser.serialize(blockState))
    fun stateToString(fluidState: FluidState) = stateToString(serializeFluid(fluidState))

    fun stateToString(raw: String): Pair<ResourceLocation, String> {
        if (raw.indexOf('[') == -1) {
            return Pair(ResourceLocation.of(raw, ':'), "default")
            // if a blockstate has no properties, the parser will not append the brackets to it, so we can use that as a check
            // since indexOf returns -1 if the char does not exist in the string.
        }
        val id = ResourceLocation(raw.substring(0, raw.indexOf('[')))
        val properties = raw.substring(raw.indexOf('[') + 1, raw.indexOf(']'))
        return Pair(id, properties)
    }

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

    fun fluidBox(fluidState: FluidState): AABBic {
        val fluidHeight = if (fluidState.isSource) {
            15
        } else {
            ((fluidState.ownHeight * 16.0).roundToInt() - 1).coerceIn(0, 15)
        }
        return AABBi(0, 0, 0, 15, fluidHeight, 15)
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

    fun buildMediumState(dragCoefficient: Double, shape: AABBic): LiquidState {
        return vsCore.newLiquidStateBuilder()
            .density(0.0)
            .dragCoefficient(dragCoefficient)
            .boxShape(shape)
            .velocity(Vector3d())
            .build()
    }
    // endregion

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

        // region enums n stuff
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

        val blockValues = listOf("mass", "friction", "elasticity", "hardness", "no_collision", "shape_override")
        val mediumValues = listOf("drag", "shape")
        val fluidValues = listOf("density", "drag", "velocity", "no_collision", "shape_override")
        // displacement state values are just "shape"
        // endregion

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
        private fun determineStructure(json: JsonObject, idType: IdType, id: String): Structure {
            fun determineCompoundBlockStructure(json: JsonObject, id: String, tag: Boolean = false): Structure {
                val hasSolid = json.has("solid")
                val hasMedium = json.has("medium")

                return if (hasSolid && !hasMedium) {
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)

                    if (solidValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else throw MassJsonParseException("Solid state is invalid!", id) // prevent so we default
                } else if (hasMedium && !hasSolid) {
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (mediumValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else throw MassJsonParseException("Medium state is invalid!", id) // prevent so we default
                } else if (!hasMedium) { // we know that hasSolid is false here already so we don't need to check
                    throw MassJsonParseException("Json does not have a solid or medium state!", id)
                } else { // has both
                    val solidValid = json["solid"].asJsonObject.hasAny(blockValues)
                    val mediumValid = json["medium"].asJsonObject.hasAny(mediumValues)

                    if (solidValid && mediumValid) {
                        if (tag) StructureType.BLOCK_TAG_COMPOUND.toStructure() else StructureType.BLOCK_COMPOUND.toStructure()
                    } else if (!solidValid && !mediumValid) { // neither valid
                        throw MassJsonParseException("Neither medium or solid state is valid!", id)
                    } else if (!solidValid) { // medium is valid but solid isn't
                        // solid states are kinda more important so if this is invalid we should just completely error instead of keeping the medium state.
                        throw MassJsonParseException("Medium state is valid, but solid state is invalid!", id)
                    } else {
                        json.remove("medium")
                        Structure.warn(if (tag) StructureType.BLOCK_TAG_COMPOUND else StructureType.BLOCK_COMPOUND, "Medium state in block $id is invalid, but solid state is.")
                    }
                }
            }

            // some utility stuff for determining the main structure of the entry
            fun JsonObject.basic(block: Boolean = true): Boolean = if (block) this.hasAny(blockValues) else this.hasAny(fluidValues)
            fun JsonObject.compound(): Boolean = this.hasAny("solid", "medium")

            return when (idType) {
                IdType.BLOCK -> {
                    if (json.basic()) {
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
                }
                IdType.FLUID -> {
                    if (json.basic(false)) {
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
                    } else throw MassJsonParseException("Could not determine fluid structure!", id)
                }
                IdType.BLOCK_TAG -> {
                    if (json.basic()) {
                        StructureType.BLOCK_TAG_BASIC.toStructure()
                    } else if (json.compound()) {
                        determineCompoundBlockStructure(json, id, true) // i could implement states for tags but also that doesn't really make sense
                    } else throw MassJsonParseException("Could not determine block tag structure!", id)
                }
                IdType.FLUID_TAG -> {
                    if (json.hasAny(fluidValues)) {
                        StructureType.FLUID_TAG_BASIC.toStructure()
                    } else throw MassJsonParseException("Could not determine fluid tag structure!", id)
                }
                IdType.NONE -> throw MassJsonParseException("how") // this shouldn't be possible but we're checking anyways because i have anxiety
            }
        }

        private fun parseNumericValue(element: JsonElement?, default: Double): NumericValue {
            if (element == null) return NumericValue.Literal(default)
            return when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> NumericValue.Literal(element.asDouble)
                element.isJsonObject -> {
                    val json = element.asJsonObject
                    val target = json["value"]?.asString
                    val mult = json["mult"]?.asDouble ?: 1.0

                    if (target == null) {
                        logger.error("no target value in dependent value, defaulting to $default")
                        NumericValue.Literal(default)
                    } else {
                        val targetState = stateToString(target)
                        NumericValue.Dependent(targetState.a, targetState.b, mult)
                    }
                }
                else -> {
                    logger.error("invalid numeric value format, using default $default.")
                    NumericValue.Literal(default)
                }
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
            if (!ResourceLocation.isValidResourceLocation(id)) {
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

            when (structure.type) {
                StructureType.BLOCK_BASIC -> {
                    putPendingProperties(id, "default", PendingBlockStateProperties(priority, parseSolid(json)))
                }
                StructureType.BLOCK_COMPOUND -> {
                    putPendingProperties(id, "default", PendingBlockStateProperties(priority,
                        solid = if (json.has("solid")) parseSolid(json.getAsJsonObject("solid")) else null,
                        medium = if (json.has("medium")) parseMedium(json.getAsJsonObject("medium")) else null
                    ))
                }
                StructureType.BLOCK_STATES -> {
                    structure.state2StructureType!!.forEach { (state, type) ->
                        when (type) {
                            StructureType.BLOCK_BASIC -> {
                                putPendingProperties(id, state, PendingBlockStateProperties(priority, parseSolid(json.getAsJsonObject(state))))
                            }
                            StructureType.BLOCK_COMPOUND -> {
                                val stateJson = json.getAsJsonObject(state)
                                putPendingProperties(id, "default", PendingBlockStateProperties(priority,
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
                    putPendingProperties(id, "default", PendingBlockStateProperties(priority, liquid = parseLiquid(json)))
                }
                StructureType.FLUID_STATES -> { // type really doesn't matter here because fluids can only be parsed one way, but i don't feel like adding a list of states to this, map works just fine
                    structure.state2StructureType!!.keys.forEach { state ->
                        putPendingProperties(id, state, PendingBlockStateProperties(priority, liquid = parseLiquid(json.getAsJsonObject(state))))
                    }
                }
                StructureType.BLOCK_TAG_BASIC -> {
                    putTagProperties(id, PendingTagProperties(PendingBlockStateProperties(priority, parseSolid(json)), parseTagExclusions(json), true))
                }
                StructureType.BLOCK_TAG_COMPOUND -> {
                    putTagProperties(id, PendingTagProperties(PendingBlockStateProperties(priority,
                        solid = if (json.has("solid")) parseSolid(json.getAsJsonObject("solid")) else null,
                        medium = if (json.has("medium")) parseMedium(json.getAsJsonObject("medium")) else null
                    ), parseTagExclusions(json), true))
                }
                StructureType.FLUID_TAG_BASIC -> {
                    putTagProperties(id, PendingTagProperties(PendingBlockStateProperties(priority, liquid = parseLiquid(json)), parseTagExclusions(json), false))
                }
            }
        }

        // region parse functions
        private fun parseSolid(json: JsonObject): PendingSolidStateProperties {
            val mass = parseNumericValue(json["mass"], VSGameConfig.SERVER.blockProperties.defaultBlockMass)
            val friction = parseNumericValue(json["friction"], VSGameConfig.SERVER.blockProperties.defaultBlockFriction)
            val elasticity = parseNumericValue(json["elasticity"], VSGameConfig.SERVER.blockProperties.defaultBlockElasticity)
            val hardness = parseNumericValue(json["hardness"], VSGameConfig.SERVER.blockProperties.defaultBlockHardness)
            val noCollision = json["no_collision"]?.asBoolean ?: false
            val shapeOverride = json["shape_override"]?.let { parseShape(it) }
            return PendingSolidStateProperties(mass, friction, elasticity, hardness, noCollision, shapeOverride)
        }

        private fun parseMedium(json: JsonObject): PendingMediumStateProperties {
            val dragCoefficient = parseNumericValue(json["drag"], VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)
            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return PendingMediumStateProperties(dragCoefficient, shapeOverride)
        }

        private fun parseLiquid(json: JsonObject): PendingLiquidStateProperties {
            val density = parseNumericValue(json["density"], VSGameConfig.SERVER.blockProperties.defaultLiquidDensity)
            val dragCoefficient = parseNumericValue(json["drag"], VSGameConfig.SERVER.blockProperties.defaultLiquidDragCoefficient)

            val velocityArray = json["velocity"]?.asJsonArray
            val velocity = if (velocityArray != null) {
                Vector3d(velocityArray[0].asDouble, velocityArray[1].asDouble, velocityArray[2].asDouble)
            } else {
                Vector3d()
            }

            val shapeOverride = json["shape_override"]?.let { parseShape(it) }

            return PendingLiquidStateProperties(density, dragCoefficient, velocity, shapeOverride)
        }

        private fun parseShape(jsonArray: JsonElement): AABBic? =
            if (jsonArray.isJsonArray) {
                jsonArray as JsonArray
                if (jsonArray.size() != 6) null else {
                    AABBi(jsonArray[0].asInt, jsonArray[1].asInt, jsonArray[2].asInt,
                        jsonArray[3].asInt, jsonArray[4].asInt, jsonArray[5].asInt)
                }
            } else null

        private fun parseTagExclusions(json: JsonObject): Set<ResourceLocation> {
            val set = mutableSetOf<ResourceLocation>()
            if (json.has("exclude") && json.get("exclude").isJsonArray) {
                val exclusionArray = json.get("exclude").asJsonArray
                exclusionArray.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }.forEach {
                    if (ResourceLocation.isValidResourceLocation(it.asString)) set.add(ResourceLocation.of(it.asString, ':'))
                }
            }
            return set.toSet()
        }
        // endregion
    }

    fun JsonObject.hasAny(members: Iterable<String>): Boolean = members.any { has(it) }
    fun JsonObject.hasAny(vararg members: String): Boolean = hasAny(members.asIterable())
    fun <K, V> Map<K, V>.getOrOther(key: K, other: K): V? = if (key == other) get(key) else get(key) ?: get(other)

    private val logger by logger()
}
