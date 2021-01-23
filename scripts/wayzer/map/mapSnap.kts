package wayzer.map

import arc.util.Log
import mindustry.content.Blocks
import mindustry.core.ContentLoader
import mindustry.core.World
import mindustry.world.Tile
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO

//参考 mindustry.graphics.MinimapRenderer
object MapRenderer {
    init {
        loadColors(content)
    }

    var img: BufferedImage? = null
    fun drawAll(world: World) {
        img = BufferedImage(world.width(), world.height(), BufferedImage.TYPE_INT_ARGB).apply {
            repeat(world.width()) { x ->
                repeat(world.height()) { y ->
                    setRGB(x, height - 1 - y, getARGB(world.tile(x, y)))
                }
            }
        }
    }

    fun update(tile: Tile) {
        img?.apply {
            setRGB(tile.x.toInt(), height - 1 - tile.y, getARGB(tile))
        }
    }

    //参考 mindustry.core.ContentLoader.loadColors
    private fun loadColors(content: ContentLoader) {
        if (content.blocks().isEmpty) return
        val img = Config.getModuleDir("wayzer").resolve("res/block_colors.png")
            .takeIf { it.exists() && it.canRead() }?.inputStream()?.use { ImageIO.read(it) }
        if (img == null) Log.warn("[wayzer/ext/mapSnap] Can't find the image set res/block_colors.png")
        img?.apply {
            repeat(width) { i ->
                val color = getRGB(i, 0)
                if (color != 0 && color != 255) {
                    content.block(i).apply {
                        mapColor.argb8888(color)
                        squareSprite = mapColor.a > 0.5f
                        mapColor.a = 1.0f
                        hasColor = true
                    }
                }
            }
            Log.info("[wayzer/ext/mapSnap] Loading square color set successfully")
        }
    }

    private fun getARGB(tile: Tile?): Int {
        val rgba = getRGBA(tile)
        val a = rgba and 255
        val rgb = rgba ushr 8
        return (a shl 24) + rgb
    }

    private fun getRGBA(tile: Tile?): Int {
        return when {
            tile == null -> 0
            tile.block().minimapColor(tile) != 0 -> tile.block().minimapColor(tile)
            tile.block().synthetic() -> tile.team().color.rgba()
            tile.block().solid -> tile.block().mapColor.rgba()
            tile.overlay() != Blocks.air -> tile.overlay().mapColor.rgba()
            else -> tile.floor().mapColor.rgba()
        }
    }
}
listen<EventType.WorldLoadEvent> {
    MapRenderer.drawAll(world)
}
listen<EventType.TileChangeEvent> {
    MapRenderer.update(it.tile)
}
onEnable {
    if (net.server())
        MapRenderer.drawAll(world)
}
registerVar("wayzer.ext.mapSnap._get", "Map Snapshot Screenshot Interface", { MapRenderer.img })

command("saveSnap", "Save a screenshot of the current server map") {
    type = CommandType.Server
    body {
        val dir = dataDirectory.child("mapSnap").apply { mkdirs() }
        val file = dir.child("mapSnap-${SimpleDateFormat("YYYYMMdd-hhmm").format(Date())}.png")
        file.write().use { ImageIO.write(MapRenderer.img, "png", it) }
        reply("[green] Snapshot saved to {file}".with("file" to file))
    }
}