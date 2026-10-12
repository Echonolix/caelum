package net.echonolix.caelum.mdbx

/**
 * Creation/open-time libmdbx geometry. Sizes are bytes and rounded by libmdbx
 * to database/system pages. -1 keeps the existing value or uses the native default;
 * zero lower/initial/page size asks for the native minimum. Zero shrink disables
 * automatic shrinking; set both growth/shrink to zero to disable automatic resizing
 * (libmdbx may round zero growth up when shrinking is enabled).
 * The upper bound is [MdbxEnvironment.open]'s maxSizeBytes.
 * These settings do not change the online upper mapping bound or implement application eviction.
 */
public data class MdbxGeometry(
    public val minSizeBytes: Long = -1,
    public val initialSizeBytes: Long = -1,
    public val growthStepBytes: Long = -1,
    public val shrinkThresholdBytes: Long = -1,
    public val pageSizeBytes: Long = -1,
) {
    init {
        require(minSizeBytes >= -1 && initialSizeBytes >= -1 && growthStepBytes >= -1 && shrinkThresholdBytes >= -1) {
            "Geometry sizes must be -1 or nonnegative"
        }
        require(pageSizeBytes == -1L || pageSizeBytes == 0L ||
            (pageSizeBytes in 256L..65536L && pageSizeBytes and (pageSizeBytes - 1) == 0L)) {
            "pageSizeBytes must be -1, 0, or a power of two from 256 to 65536"
        }
        require(growthStepBytes <= 0 || shrinkThresholdBytes <= 0 || shrinkThresholdBytes >= growthStepBytes) {
            "shrinkThresholdBytes must be at least growthStepBytes when both are enabled"
        }
    }
}
