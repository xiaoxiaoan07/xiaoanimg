package native

actual object PayloadExtractNative {
    init {
        System.loadLibrary("payload_extract_jni")
    }

    actual external fun open(input: String): Long
    actual external fun listPartitionsJson(handle: Long, withHash: Boolean): String
    actual external fun getMetadataJson(handle: Long): String
    actual external fun extractPartition(input: String, outputDir: String, partitionName: String, threads: Int, verify: Boolean, token: Long)
    actual external fun getExtractProgress(token: Long): Long
    actual external fun close(handle: Long)
}
