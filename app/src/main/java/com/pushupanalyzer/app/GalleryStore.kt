package com.pushupanalyzer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object GalleryStore {
    fun save(context: Context, file: File, cancelled: () -> Unit): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,file.name)
            put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,Environment.DIRECTORY_MOVIES+"/PushupAnalyzer")
            put(MediaStore.Video.Media.IS_PENDING,1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)
            ?: error("Cannot create gallery entry. Check free storage.")
        try {
            resolver.openOutputStream(uri,"w")?.use { output ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(256*1024)
                    while (true) {
                        cancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer,0,count)
                    }
                }
            } ?: error("Cannot open gallery output")
            cancelled()
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING,0)
            check(resolver.update(uri,values,null,null) == 1) { "Cannot publish gallery video" }
            return uri
        } catch (t: Throwable) { resolver.delete(uri,null,null); throw t }
    }
}
