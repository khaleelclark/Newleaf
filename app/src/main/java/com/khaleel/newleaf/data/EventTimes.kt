package com.khaleel.newleaf.data

import java.time.*

object EventTimes {
    fun localDate(instant: String, zone: String): String = Instant.parse(instant).atZone(ZoneId.of(zone)).toLocalDate().toString()
    fun localTimestamp(instant: String, zone: String): String = Instant.parse(instant).atZone(ZoneId.of(zone)).toOffsetDateTime().toString()

    /** Reject DST gaps; an ambiguous overlap requires an explicit valid offset. */
    fun resolve(local: LocalDateTime, zone: ZoneId, offset: ZoneOffset? = null): Instant {
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.isNotEmpty()) { "This local time does not exist in $zone; choose another time" }
        require(offsets.size == 1 || offset != null) { "This local time occurs twice; choose an offset" }
        val chosen = offset ?: offsets.single()
        require(chosen in offsets) { "Offset is not valid for this local time" }
        return local.toInstant(chosen)
    }

    fun at(entry: Entry, instant: Instant, zone: ZoneId = ZoneId.of(entry.zoneId)): Entry = entry.copy(
        occurredAt = instant.toString(), zoneId = zone.id, localDate = instant.atZone(zone).toLocalDate().toString(),
        localTimestamp = instant.atZone(zone).toOffsetDateTime().toString(), updatedAt = Instant.now().toString()
    )
    fun elapsedSeconds(checkIn: Entry, meal: Entry): Long = Duration.between(Instant.parse(meal.occurredAt), Instant.parse(checkIn.occurredAt)).seconds
}
