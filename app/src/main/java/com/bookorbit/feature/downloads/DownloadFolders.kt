package com.bookorbit.feature.downloads

import android.content.Context
import java.io.File

object DownloadFolders {
    /**
     * Where a book's files live in app storage. The first server's books stay in `downloads/<id>` (so
     * existing downloads keep working); other servers use `downloads/<scope>/<id>` so ids can't clash.
     */
    fun internalDir(context: Context, scope: String, bookId: Int): File =
        File(context.filesDir, if (scope.isEmpty()) "downloads/$bookId" else "downloads/$scope/$bookId")
}
