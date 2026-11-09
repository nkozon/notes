package com.ozon.notes

import java.io.File
import java.io.IOException
import java.io.InputStream

internal fun copyImportedFile(destination: File, openInput: () -> InputStream?): File {
    try {
        (openInput() ?: throw IOException("Could not open PDF file")).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return destination
    } catch (failure: Throwable) {
        if (destination.exists() && !destination.delete()) {
            failure.addSuppressed(IOException("Could not remove incomplete import: $destination"))
        }
        throw failure
    }
}
