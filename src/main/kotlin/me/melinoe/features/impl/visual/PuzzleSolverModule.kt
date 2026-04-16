package me.melinoe.features.impl.visual

import com.mojang.math.Transformation
import me.melinoe.Melinoe
import me.melinoe.events.DungeonEntryEvent
import me.melinoe.events.DungeonExitEvent
import me.melinoe.events.RenderEvent
import me.melinoe.events.TickEvent
import me.melinoe.events.core.on
import me.melinoe.features.Category
import me.melinoe.features.Module
import me.melinoe.utils.Color
import me.melinoe.utils.Message
import me.melinoe.utils.render.drawText3D
import me.melinoe.utils.renderPos
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Brightness
import net.minecraft.util.Mth
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Display.ItemDisplay
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.abs


object PuzzleSolverModule : Module(
    name = "Puzzle Solver",
    category = Category.VISUAL,
    description = "Solves puzzles in dungeons for you."
) {
    data class MiddleBlock(
        val pos: BlockPos?,
        val originalDirection: Direction?
    ) {
        companion object {
            val EMPTY = MiddleBlock(null, null)
        }
    }

    private val START_ROOM_MIDDLE_BLOCKS = listOf(
        MiddleBlock(BlockPos(47, 101, 8), Direction.WEST), // East room
        MiddleBlock(BlockPos(-31, 101, 8), Direction.EAST) // West room
    )

    private var inAuroraSanctum: Boolean = false

    private val mirrorRooms = mutableMapOf<MiddleBlock, MiddleBlock>()
    private val solutions = mutableMapOf<MiddleBlock, Map<ItemDisplay, Int>>()

    init {
        on<TickEvent.End> {
            if (!enabled) return@on
            if (!inAuroraSanctum) return@on
            val player = mc.player ?: return@on

            for (startMiddleBlock in START_ROOM_MIDDLE_BLOCKS) {
                val mirrorRoomMiddleBlock = getMirrorRoom(world, startMiddleBlock)
                if (mirrorRoomMiddleBlock == null) {
                    Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find the mirror room!")
                    continue
                }
                mirrorRooms[startMiddleBlock] = mirrorRoomMiddleBlock

                val mirrorRoomPos = mirrorRoomMiddleBlock.pos ?: continue

                if (isEntityInMirrorRoom(player.blockPosition(), mirrorRoomPos)) {
                    val mirrors = world.entitiesForRendering().filterIsInstance<ItemDisplay>().filter { e ->
                        val transformation = Display.createTransformation(e.entityData)
                        val quaternion = transformation.leftRotation

                        isEntityInMirrorRoom(e.blockPosition(), mirrorRoomPos) &&
                                e.brightnessOverride == Brightness(15, 15) &&
                                !(quaternion.x == 0f && quaternion.y == 0f && quaternion.z == 0f && quaternion.w == 1f)
                    }.distinctBy { it.blockPosition() } // remove the duplicates
                    if (mirrors.isEmpty()) {
                        Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find any mirrors!")
                        continue
                    }

                    val startMirrorArmorStand = world.entitiesForRendering().filterIsInstance<ArmorStand>()
                        .firstOrNull() { it.customName?.string == "Right-Click to rotate" }
                    if (startMirrorArmorStand == null) {
                        Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find the invisible armor stand above the start mirror")
                        continue
                    }
                    val startMirrorPos = startMirrorArmorStand.position().subtract(0.0, 2.3, 0.0)
                    val startMirror = mirrors.find { it.position().distanceTo(startMirrorPos) < 0.1 }
                    if (startMirror == null) {
                        Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find the start mirror!")
                        continue
                    }

                    val endMirror = mirrors.find {
                        it.position().y == mirrorRoomPos.y.toDouble() + 1.0 &&
                                if (mirrorRoomMiddleBlock.originalDirection?.axis == Direction.Axis.X) {
                                    it.position().z == mirrorRoomPos.z.toDouble() + 0.5
                                } else {
                                    it.position().x == mirrorRoomPos.x.toDouble() + 0.5
                                }
                    }
                    if (endMirror == null) {
                        Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find the end mirror!")
                        continue
                    }

                    val solution = getMirrorSolution(world, startMirror, endMirror, mirrors, mirrorRoomMiddleBlock)
                    if (solution == null) {
                        Melinoe.logger.error("${isEastOrWest(startMiddleBlock)}: Couldn't find a solution")
                        continue
                    }
                    Message.chat(solution.entries.joinToString { "${it.key}:${it.value}" })
                    solutions[startMiddleBlock] = solution
                }
            }
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (!inAuroraSanctum) return@on
            val player = mc.player ?: return@on

            for (startMiddleBlock in START_ROOM_MIDDLE_BLOCKS) {
                val solution = solutions[startMiddleBlock] ?: continue
                val mirrorRoomPos = mirrorRooms[startMiddleBlock]?.pos ?: continue

                if (isEntityInMirrorRoom(player.blockPosition(), mirrorRoomPos)) {
                    for ((mirror, rotations) in solution) {
                        val adjustedRotations = if (rotations > 6) 12 - rotations else rotations

                        val color = if (rotations == 0)
                            Color(255, 215, 0, 1.0f) // gold
                        else if (rotations > 6)
                            Color(255, 0, 0, 1.0f) // red
                        else
                            Color(0, 255, 0, 1.0f) // green

                        drawText3D(adjustedRotations.toString(), mirror.renderPos.add(0.0, 2.5, 0.0), 2.0f, color, false)
                    }
                }
            }
        }

        on<DungeonEntryEvent> {
            if (dungeon.areaName == "Aurora Sanctum") {
                inAuroraSanctum = true
            }
        }

        on<DungeonExitEvent> {
            if (dungeon.areaName == "Aurora Sanctum") {
                inAuroraSanctum = false
                mirrorRooms.clear()
                solutions.clear()
            }
        }
    }

    private fun isEastOrWest(middleBlock: MiddleBlock): String {
        return when (middleBlock) {
            START_ROOM_MIDDLE_BLOCKS[0] -> "EAST"
            START_ROOM_MIDDLE_BLOCKS[1] -> "WEST"
            else -> "NULL"
        }
    }

    fun getOriginYaw(middleBlock: MiddleBlock): Float {
        return Mth.wrapDegrees(middleBlock.originalDirection?.toYRot()!!)
    }

    fun yawDifference(a: Float, b: Float): Double {
        val diff = (b - a + 540) % 360 - 180
        return diff.toDouble()
    }

    fun areMirrorsFacingEachOther(a: Float, b: Float): Boolean {
        val diff = abs(yawDifference(a, b))
        return diff <= 100.0
    }

    fun getMirrorSolution(
        world: ClientLevel,
        startMirror: ItemDisplay,
        endMirror: ItemDisplay,
        mirrors: List<ItemDisplay>,
        mirrorRoomMiddleBlock: MiddleBlock
    ): Map<ItemDisplay, Int>? {

        val reverseGraph = buildReverseGraph(world, mirrors, mirrorRoomMiddleBlock.pos!!)

        var currentMirrors = listOf(endMirror)
        val visitedMirrors = mutableSetOf<ItemDisplay>()

        val childMirrors = mutableMapOf<ItemDisplay, ItemDisplay>()
        val rotations = mutableMapOf<ItemDisplay, Int>()

        visitedMirrors.add(endMirror)
        rotations[endMirror] = ((((getOriginYaw(mirrorRoomMiddleBlock) - getYawPitch(endMirror).first) / 30).toInt() % 12) + 12) % 12

        while (currentMirrors.isNotEmpty()) {
            val nextMirrors = mutableListOf<ItemDisplay>()

            for (mirror in currentMirrors) {
                val incoming = reverseGraph[mirror] ?: continue
                val mirrorsThatSeeMe = mutableListOf<ItemDisplay>()
                incoming.forEach { mirrorsThatSeeMe.add(it.first) }

                for ((fromMirror, fromRotation) in incoming) {
                    val yawFrom = Mth.wrapDegrees(getYawPitch(fromMirror).first + (fromRotation * 30))
                    // TODO better check cuz yaw isnt working right, maybe yaw + hitpos?
                    if (!areMirrorsFacingEachOther(Mth.wrapDegrees((yawFrom + 180) % 360), Mth.wrapDegrees(getYawPitch(mirror).first + (rotations[mirror]!! * 30)))) {
                        Melinoe.logger.info("denied yawFrom: ${Mth.wrapDegrees((yawFrom + 180) % 360)} yawTo: ${Mth.wrapDegrees(getYawPitch(mirror).first + (rotations[mirror]!! * 30))}")
                        continue
                    }
                    Melinoe.logger.info("accepted yawFrom: ${Mth.wrapDegrees((yawFrom + 180) % 360)} yawTo: ${Mth.wrapDegrees(getYawPitch(mirror).first + (rotations[mirror]!! * 30))}")

                    if (fromMirror in visitedMirrors) continue

                    visitedMirrors.add(fromMirror)
                    childMirrors[fromMirror] = mirror
                    rotations[fromMirror] = fromRotation
                    nextMirrors.add(fromMirror)

                    if (fromMirror == startMirror) {
                        val path = mutableMapOf<ItemDisplay, Int>()
                        var current: ItemDisplay? = startMirror

                        while (current != null && current != endMirror) {
                            path[current] = rotations[current] ?: 0
                            current = childMirrors[current]
                        }

                        path[endMirror] = rotations[endMirror] ?: 0
                        return path
                    }
                }
            }

            currentMirrors = nextMirrors
        }

        return null
    }

    fun buildReverseGraph(world: ClientLevel, mirrors: List<ItemDisplay>, mirrorRoomPos: BlockPos): Map<ItemDisplay, List<Pair<ItemDisplay, Int>>> {
        val reverse = mutableMapOf<ItemDisplay, MutableList<Pair<ItemDisplay, Int>>>()

        for (mirror in mirrors.sortedBy {  1 / (it.position().x * it.position().z) }) {
            val (yaw, pitch) = getYawPitch(mirror)

            val connections = findConnectedMirrors(world, yaw, pitch, mirror.position(), mirrors, emptyList(), mirrorRoomPos)

            for ((to, rotations) in connections) {
                reverse.getOrPut(to) { mutableListOf() }.add(mirror to rotations)
            }
        }

        return reverse
    }

    fun findConnectedMirrors(world: ClientLevel, startYaw: Float, startPitch: Float, startPos: Vec3, mirrors: List<ItemDisplay>, visitedMirrors: List<ItemDisplay>, mirrorRoomPos: BlockPos): Map<ItemDisplay, Int> {
        val hitMirrors = mutableMapOf<ItemDisplay, Int>()

        for (i in 0 until 12) {
            val yaw = Mth.wrapDegrees((startYaw + i * 30.0).toFloat())
            val direction = Vec3.directionFromRotation(startPitch, yaw)

            val hit = raycastMirror(world, startPos.add(0.0, 1.0, 0.0), direction, 50.0, mirrorRoomPos)

            if (hit != null) {
                val hitMirror = mirrors.find { it.position().distanceTo(hit.position()) < 0.1}

                if (hitMirror == null) {
                    Melinoe.logger.error("Raycast hit mirror but couldn't find the mirror it hit")
                    Melinoe.logger.error("Raycast: Yaw $yaw: hits ${hit.position()}, from $startPos")
                } else if (hitMirror !in visitedMirrors) {
                    hitMirrors[hitMirror] = i
                    Melinoe.logger.info("Raycast: Yaw $yaw: hits ${hit.position()}, from $startPos")
                }
            }
        }
        return hitMirrors
    }

    fun raycastMirror(world: Level, origin: Vec3, direction: Vec3, maxDistance: Double, mirrorRoomPos: BlockPos): Entity? {
        val dir = direction.normalize()
        val end = origin.add(dir.scale(maxDistance))
        val searchBox = AABB(origin, end)

        val entities = world.getEntities(null, searchBox) { entity ->
            entity.type == EntityType.INTERACTION &&
                    isEntityInMirrorRoom(entity.blockPosition(), mirrorRoomPos)
        }

        var closestEntity: Entity? = null
        var closestDist = Double.MAX_VALUE

        for (entity in entities) {
            val aabb = entity.boundingBox.inflate(0.25)

            val hit = aabb.clip(origin, end).orElse(null) ?: continue
            val dist = origin.distanceToSqr(hit)

            if (dist < closestDist) {
                closestDist = dist
                closestEntity = entity
            }
        }

        return closestEntity
    }

    private fun getYawPitch(e: ItemDisplay): Pair<Float, Float> {
        val t: Transformation = Display.createTransformation(e.entityData)

        val left = t.getLeftRotation()
        val euler = left.getEulerAnglesYXZ(Vector3f())

        val yaw = Mth.wrapDegrees((-Math.toDegrees(euler.y.toDouble())).toFloat())
        val pitch = Math.toDegrees(euler.x.toDouble()).toFloat()

        return yaw to pitch
    }

    private fun isEntityInMirrorRoom(entityPos: BlockPos, mirrorRoomPos: BlockPos): Boolean {
        val dx = entityPos.x - mirrorRoomPos.x
        val dy = entityPos.y - mirrorRoomPos.y
        val dz = entityPos.z - mirrorRoomPos.z

        return dx in -12..12 && dz in -12..12 && dy in 0..99
    }

    private fun getMirrorRoom(world: ClientLevel, startMiddleBlock: MiddleBlock): MiddleBlock? {
        var currentMiddleBlock = startMiddleBlock
        while (true) {
            val (successful, nextMiddleBlock) = getNextMiddleBlock(world, currentMiddleBlock)
            if (!successful) return null

            if (nextMiddleBlock == MiddleBlock.EMPTY) break
            currentMiddleBlock = nextMiddleBlock
        }
        return currentMiddleBlock
    }

    private val DIRECTIONS = listOf(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    )

    private fun getNextMiddleBlock(world: ClientLevel, middleBlock: MiddleBlock): Pair<Boolean, MiddleBlock> {
        val basePos = middleBlock.pos ?: return false to MiddleBlock.EMPTY

        for (direction in DIRECTIONS) {
            if (direction == middleBlock.originalDirection) continue

            val checkPos = basePos.relative(direction, 13).above(1)

            if (!world.isLoaded(checkPos)) continue

            // check if there is a door in this direction
            if (world.getBlockState(checkPos).isAir) {
                return true to MiddleBlock(basePos.relative(direction, 27), direction.opposite)
            }
        }
        return true to MiddleBlock.EMPTY
    }
}