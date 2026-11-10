package com.beatsbybony.tvguide

/**
 * Edit these values to personalise the guide.
 * Find your coordinates by searching "my latitude longitude" or using any maps app.
 */
object Config {
    const val LATITUDE = 40.5992
    const val LONGITUDE = -77.5714
    const val LOCATION_LABEL = "MIFFLIN CO"
    const val FAHRENHEIT = true

    /** How many programs a normal channel loops through. */
    const val ITEMS_PER_CHANNEL = 14

    /** How many music videos a music channel pulls in (grouped into ~30 minute mixes). */
    const val MIX_TRACKS = 36
}
