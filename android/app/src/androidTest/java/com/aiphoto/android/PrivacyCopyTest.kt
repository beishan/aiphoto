package com.aiphoto.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyCopyTest {
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "privacy-test-${System.nanoTime()}").apply { mkdirs() }

    @Test fun stripsExifAndXmpWhileApplyingOrientationAndPreservingInput() {
        val dir = directory()
        try {
            val source = File(dir, "source.jpg"); val target = File(dir, "copy.jpg")
            val bitmap = Bitmap.createBitmap(48, 24, Bitmap.Config.ARGB_8888)
            for (y in 0 until 24) for (x in 0 until 48) bitmap.setPixel(x, y, if (x < 24) Color.RED else Color.BLUE)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }; bitmap.recycle()
            ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "31/1,0/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "121/1,0/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                setAttribute(ExifInterface.TAG_MAKE, "Private Camera"); setAttribute(ExifInterface.TAG_USER_COMMENT, "Private Comment"); saveAttributes()
            }
            val raw = source.readBytes()
            val xmp = ("http://ns.adobe.com/xap/1.0/\u0000" + "<x:xmpmeta xmlns:x='adobe:ns:meta/'><private>PrivateXmpIdentifier</private></x:xmpmeta>").toByteArray(Charsets.UTF_8)
            val length = xmp.size + 2
            source.writeBytes(raw.take(2).toByteArray() + byteArrayOf(0xff.toByte(), 0xe1.toByte(), (length shr 8).toByte(), length.toByte()) + xmp + raw.drop(2).toByteArray())
            val before = source.readBytes()
            PrivacyCopy.write(source, target)
            assertArrayEquals(before, source.readBytes())
            assertFalse(target.readBytes().toString(Charsets.ISO_8859_1).contains("PrivateXmpIdentifier"))
            val exif = ExifInterface(target.path)
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)); assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE)); assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
            val output = BitmapFactory.decodeFile(target.path)
            try {
                assertEquals(24, output.width); assertEquals(48, output.height)
                assertTrue(Color.red(output.getPixel(12, 8)) > 180)
                assertTrue(Color.blue(output.getPixel(12, 40)) > 180)
                assertTrue(output.colorSpace?.isSrgb == true)
            } finally { output.recycle() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun transparentPngGetsWhiteBackgroundAndLargeImagesAreBounded() {
        val dir = directory()
        try {
            val source = File(dir, "source.png"); val target = File(dir, "copy.jpg")
            val bitmap = Bitmap.createBitmap(4096, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.TRANSPARENT)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            PrivacyCopy.write(source, target)
            val output = BitmapFactory.decodeFile(target.path)
            try {
                assertEquals(2048, output.width); assertEquals(512, output.height)
                assertTrue(Color.red(output.getPixel(10, 10)) > 245)
                assertTrue(Color.green(output.getPixel(10, 10)) > 245)
                assertTrue(Color.blue(output.getPixel(10, 10)) > 245)
            } finally { output.recycle() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun wideGamutSourceIsConvertedToSrgb() {
        val dir = directory()
        try {
            val source = File(dir, "wide.png"); val target = File(dir, "copy.jpg")
            val space = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3)
            val color = Color.valueOf(0.6f, 0.3f, 0.1f, 1f, space)
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, false, space)
            android.graphics.Canvas(bitmap).drawColor(color.pack())
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            PrivacyCopy.write(source, target)
            val output = BitmapFactory.decodeFile(target.path)
            try {
                assertTrue(output.colorSpace?.isSrgb == true)
                val expected = color.convert(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)).toArgb()
                val actual = output.getPixel(16, 16)
                assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 10)
                assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 10)
                assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 10)
            } finally { output.recycle() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun unsupportedOrBrokenInputNeverProducesAnOriginalFallback() {
        val dir = directory()
        try {
            val input = File(dir, "bad.dat").apply { writeText("not an image") }
            val output = File(dir, "copy.jpg").apply { writeText("partial") }
            assertThrows(Exception::class.java) { PrivacyCopy.write(input, output) }
            assertFalse(output.exists()); assertTrue(input.exists())
            input.writeBytes(android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", 0))
            assertThrows(Exception::class.java) { PrivacyCopy.write(input, output) }
            assertFalse(output.exists())
        } finally { dir.deleteRecursively() }
    }
}
