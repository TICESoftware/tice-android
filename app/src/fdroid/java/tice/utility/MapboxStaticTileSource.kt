package tice.utility

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

class CustomMapboxTileSource(private val accessToken: String) : OnlineTileSourceBase(
    "mapbox",
    1,
    19,
    512,
    ".png",
    arrayOf("https://api.mapbox.com/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
                "styles/v1/mapbox/streets-v11/tiles/" +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) +
                "?access_token=" + accessToken
}