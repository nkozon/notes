package com.ozon.notes

import java.io.InputStream
import java.security.MessageDigest

/** Dropbox content hash: SHA-256 of the concatenated SHA-256 digests of 4 MiB blocks. */
internal object DropboxContentHash {
    private const val BLOCK_SIZE = 4 * 1024 * 1024

    fun calculate(input: InputStream): String {
        val result = MessageDigest.getInstance("SHA-256")
        val block = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var remaining = BLOCK_SIZE
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            if (count == 0) continue
            block.update(buffer, 0, count)
            remaining -= count
            if (remaining == 0) {
                result.update(block.digest())
                remaining = BLOCK_SIZE
            }
        }
        if (remaining != BLOCK_SIZE) result.update(block.digest())
        return result.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
