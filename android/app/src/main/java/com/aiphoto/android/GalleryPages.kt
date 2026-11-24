package com.aiphoto.android

import java.util.RandomAccess

/** Cursor pages share their already loaded photos without copying every photo on append. */
internal class GalleryPages private constructor(
    private val pages: List<List<Photo>>,
    private val starts: IntArray,
    override val size: Int,
) : AbstractList<Photo>(), RandomAccess {
    override fun get(index: Int): Photo {
        if (index !in indices) throw IndexOutOfBoundsException("Photo index $index, size $size")
        val match = starts.binarySearch(index)
        val page = if (match >= 0) match else -match - 2
        return pages[page][index - starts[page]]
    }

    fun indexOfId(id: Long): Int {
        val index = binarySearch { id.compareTo(it.id) }
        return if (index >= 0) index else -1
    }

    private fun append(page: List<Photo>): GalleryPages =
        GalleryPages(pages + listOf(page), starts + size, size + page.size)

    companion object {
        fun append(loaded: List<Photo>, incoming: List<Photo>, beforeId: Long?): List<Photo> {
            // The NAS returns strictly descending IDs below its cursor, even if older loaded
            // items have since been removed or edited. Only the new page needs deduplication.
            val boundary = beforeId ?: loaded.lastOrNull()?.id
            val page = incoming.filter { boundary == null || it.id < boundary }.distinctBy { it.id }
            if (page.isEmpty()) return loaded
            if (loaded.isEmpty()) return GalleryPages(listOf(page), intArrayOf(0), page.size)
            val chunks = loaded as? GalleryPages
                ?: GalleryPages(listOf(loaded), intArrayOf(0), loaded.size)
            return chunks.append(page)
        }
    }
}
