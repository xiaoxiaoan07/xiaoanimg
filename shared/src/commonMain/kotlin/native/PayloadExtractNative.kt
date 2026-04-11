package native

expect object PayloadExtractNative {
    fun open(input: String): Long
    fun listPartitionsJson(handle: Long, withHash: Boolean): String
    fun getMetadataJson(handle: Long): String
    fun extractPartition(input: String, outputDir: String, partitionName: String, threads: Int, verify: Boolean)
    fun close(handle: Long)
}
