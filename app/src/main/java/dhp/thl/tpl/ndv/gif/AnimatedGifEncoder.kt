package dhp.thl.tpl.ndv.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.IOException
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin Animated GIF Encoder.
 * Based on the classic open-source GIF encoding specifications (Kevin Weiner, Anthony Dekker).
 * Supports transparency, variable frame delays, and infinite looping.
 */
class AnimatedGifEncoder {
    private var width: Int = 0
    private var height: Int = 0
    private var transparent: Int? = null
    private var transIndex: Int = 0
    private var repeat: Int = 0 // 0 = repeat forever, -1 = no repeat
    private var delay: Int = 100 // Frame delay in milliseconds
    private var started: Boolean = false
    private var out: OutputStream? = null
    private var image: Bitmap? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth: Int = 0
    private var colorTab: ByteArray? = null
    private var usedEntry = BooleanArray(256)
    private var palSize: Int = 7
    private var dispose: Int = -1 // -1 = default, 0 = none, 1 = leave, 2 = restore background, 3 = restore prev
    private var closeStream: Boolean = false
    private var firstFrame: Boolean = true
    private var sizeSet: Boolean = false
    private var sample: Int = 10 // Default quality sample interval (1-30, 10 is standard good)

    fun setDelay(ms: Int) {
        delay = ms
    }

    fun setRepeat(iter: Int) {
        repeat = iter
    }

    fun setTransparent(color: Int) {
        transparent = color
    }

    fun setQuality(quality: Int) {
        sample = if (quality < 1) 1 else quality
    }

    fun setSize(w: Int, h: Int) {
        width = w
        height = h
        sizeSet = true
    }

    fun start(os: OutputStream): Boolean {
        out = os
        closeStream = false
        started = true
        return try {
            writeString("GIF89a") // Header
            true
        } catch (e: IOException) {
            false
        }
    }

    fun addFrame(im: Bitmap): Boolean {
        if (!started || out == null) return false
        try {
            if (!sizeSet) {
                setSize(im.width, im.height)
            }
            image = im
            getImagePixels()
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) {
                    writeNetscapeExt()
                }
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
            return true
        } catch (e: IOException) {
            return false
        }
    }

    fun finish(): Boolean {
        if (!started) return false
        var ok = true
        started = false
        try {
            out?.write(0x3b) // GIF trailer
            out?.flush()
            if (closeStream) {
                out?.close()
            }
        } catch (e: IOException) {
            ok = false
        }
        transIndex = 0
        out = null
        image = null
        pixels = null
        indexedPixels = null
        colorTab = null
        firstFrame = true
        return ok
    }

    private fun getImagePixels() {
        val w = image!!.width
        val h = image!!.height
        val tempBitmap = if (w != width || h != height) {
            val scaled = Bitmap.createScaledBitmap(image!!, width, height, true)
            scaled
        } else {
            image!!
        }

        val argb = IntArray(width * height)
        tempBitmap.getPixels(argb, 0, width, 0, 0, width, height)

        pixels = ByteArray(argb.size * 3)
        var hasTransparentPixels = false
        var k = 0

        for (i in argb.indices) {
            val c = argb[i]
            val alpha = (c shr 24) and 0xff
            if (alpha < 128) {
                hasTransparentPixels = true
            }
            pixels!![k++] = ((c shr 16) and 0xff).toByte() // R
            pixels!![k++] = ((c shr 8) and 0xff).toByte()  // G
            pixels!![k++] = (c and 0xff).toByte()         // B
        }

        if (hasTransparentPixels && transparent == null) {
            // Pick a transparent marker color that is rare, e.g. #00FF00 or #FF00FF
            transparent = Color.MAGENTA
        }

        if (tempBitmap != image) {
            tempBitmap.recycle()
        }
    }

    private fun analyzePixels() {
        val len = pixels!!.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels!!, len, sample)
        colorTab = nq.process()

        // Map colors to palette
        var k = 0
        for (i in 0 until nPix) {
            val r = pixels!![k++].toInt() and 0xff
            val g = pixels!![k++].toInt() and 0xff
            val b = pixels!![k++].toInt() and 0xff
            val index = nq.map(r, g, b)
            usedEntry[index] = true
            indexedPixels!![i] = index.toByte()
        }
        pixels = null
        colorDepth = 8
        palSize = 7

        // Get closest match to transparent color if specified
        transparent?.let { trans ->
            val tr = Color.red(trans)
            val tg = Color.green(trans)
            val tb = Color.blue(trans)
            transIndex = findClosest(tr, tg, tb)
        }
    }

    private fun findClosest(r: Int, g: Int, b: Int): Int {
        if (colorTab == null) return -1
        var minPos = 0
        var dmin = 256 * 256 * 256
        val len = colorTab!!.size
        var i = 0
        var pos = 0
        while (i < len) {
            val dr = r - (colorTab!![i++].toInt() and 0xff)
            val dg = g - (colorTab!![i++].toInt() and 0xff)
            val db = b - (colorTab!![i++].toInt() and 0xff)
            val d = dr * dr + dg * dg + db * db
            if (d < dmin) {
                dmin = d
                minPos = pos
            }
            pos++
        }
        return minPos
    }

    private fun writeGraphicCtrlExt() {
        out!!.write(0x21) // Extension introducer
        out!!.write(0xf9) // GCE label
        out!!.write(4)    // Block size
        val transp: Int
        val disp: Int
        if (transparent == null) {
            transp = 0
            disp = 0
        } else {
            transp = 1
            disp = 2 // Restore to background color
        }
        if (dispose >= 0) {
            // disp = dispose and 7
        }
        out!!.write(0 or (disp shl 2) or 0 or transp) // Packed fields
        writeShort(Math.round(delay / 10.0f))        // Delay in 1/100s of second
        out!!.write(transIndex)                       // Transparent color index
        out!!.write(0)                                // Block terminator
    }

    private fun writeImageDesc() {
        out!!.write(0x2c) // Image separator
        writeShort(0)     // Left
        writeShort(0)     // Top
        writeShort(width)
        writeShort(height)
        if (firstFrame) {
            out!!.write(0)
        } else {
            out!!.write(0x80 or 0 or 0 or 0 or palSize) // Local color table
        }
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out!!.write(0x80 or 0x70 or 0x00 or palSize) // Global color table
        out!!.write(0) // Background color index
        out!!.write(0) // Pixel aspect ratio
    }

    private fun writeNetscapeExt() {
        out!!.write(0x21) // Extension introducer
        out!!.write(0xff) // Application extension
        out!!.write(11)   // Block size
        writeString("NETSCAPE2.0")
        out!!.write(3)    // Sub-block size
        out!!.write(1)    // Sub-block id
        writeShort(repeat) // Loop count (0 = infinite)
        out!!.write(0)    // Block terminator
    }

    private fun writePalette() {
        out!!.write(colorTab!!, 0, colorTab!!.size)
        val n = (3 * 256) - colorTab!!.size
        for (i in 0 until n) {
            out!!.write(0)
        }
    }

    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    private fun writeShort(value: Int) {
        out!!.write(value and 0xff)
        out!!.write((value shr 8) and 0xff)
    }

    private fun writeString(s: String) {
        for (element in s) {
            out!!.write(element.code)
        }
    }

    /**
     * NeuQuant Neural-Net Quantization Algorithm
     * Copyright (c) 1994 Anthony Dekker
     */
    private class NeuQuant(
        private val thepicture: ByteArray,
        private val lengthcount: Int,
        samplefac: Int
    ) {
        private val samplefactor: Int = samplefac
        private val network = Array(256) { DoubleArray(4) }
        private val netindex = IntArray(256)
        private val bias = IntArray(256)
        private val freq = IntArray(256)
        private val radpower = IntArray(32)

        init {
            for (i in 0..255) {
                network[i][0] = ((i shl (3 + 8)) / 256).toDouble()
                network[i][1] = ((i shl (3 + 8)) / 256).toDouble()
                network[i][2] = ((i shl (3 + 8)) / 256).toDouble()
                freq[i] = (1 shl 16) / 256
                bias[i] = 0
            }
        }

        fun colorMap(): ByteArray {
            val map = ByteArray(3 * 256)
            val index = IntArray(256)
            for (i in 0..255) index[network[i][3].toInt()] = i
            var k = 0
            for (i in 0..255) {
                val j = index[i]
                map[k++] = (network[j][0].toInt() shr 4).toByte()
                map[k++] = (network[j][1].toInt() shr 4).toByte()
                map[k++] = (network[j][2].toInt() shr 4).toByte()
            }
            return map
        }

        fun inxbuild() {
            var previouscol = 0
            var startpos = 0
            for (i in 0..255) {
                val p = network[i]
                var smallpos = i
                var smallval = p[1].toInt()
                for (j in i + 1..255) {
                    val q = network[j]
                    if (q[1].toInt() < smallval) {
                        smallpos = j
                        smallval = q[1].toInt()
                    }
                }
                val q = network[smallpos]
                if (i != smallpos) {
                    var t = p[0]; p[0] = q[0]; q[0] = t
                    t = p[1]; p[1] = q[1]; q[1] = t
                    t = p[2]; p[2] = q[2]; q[2] = t
                    t = p[3]; p[3] = q[3]; q[3] = t
                }
                if (smallval != previouscol) {
                    netindex[previouscol] = (startpos + i) shr 1
                    for (j in previouscol + 1 until smallval) netindex[j] = i
                    previouscol = smallval
                    startpos = i
                }
            }
            netindex[previouscol] = (startpos + 255) shr 1
            for (j in previouscol + 1..255) netindex[j] = 255
        }

        fun learn() {
            if (lengthcount < 3 * 503) return
            val alphadec = 30 + ((samplefactor - 1) / 3)
            val samplepixels = lengthcount / (3 * samplefactor)
            var delta = samplepixels / 100
            if (delta == 0) delta = 1
            var alpha = 1 shl 10
            var radius = (256 shr 3) * (1 shl 6)
            var rad = radius shr 6
            if (rad <= 1) rad = 0
            for (i in 0 until rad) {
                radpower[i] = alpha * (((rad * rad - i * i) * (1 shl 8)) / (rad * rad))
            }

            var step = 499
            if ((lengthcount % 499) != 0) step = 499
            else if ((lengthcount % 491) != 0) step = 491
            else if ((lengthcount % 487) != 0) step = 487
            else step = 503

            var pix = 0
            var i = 0
            while (i < samplepixels) {
                val b = (thepicture[pix] .toInt() and 0xff) shl 4
                val g = (thepicture[pix + 1].toInt() and 0xff) shl 4
                val r = (thepicture[pix + 2].toInt() and 0xff) shl 4
                val j = contest(b, g, r)

                altersingle(alpha, j, b, g, r)
                if (rad != 0) alterneigh(rad, j, b, g, r)

                pix += step * 3
                if (pix >= lengthcount) pix -= lengthcount

                i++
                if (delta == 0) delta = 1
                if (i % delta == 0) {
                    alpha -= alpha / alphadec
                    radius -= radius / 30
                    rad = radius shr 6
                    if (rad <= 1) rad = 0
                    for (k in 0 until rad) {
                        radpower[k] = alpha * (((rad * rad - k * k) * (1 shl 8)) / (rad * rad))
                    }
                }
            }
        }

        fun map(b: Int, g: Int, r: Int): Int {
            var bestd = 1000
            var best = -1
            val gb = g
            var i = netindex[gb]
            var j = i - 1

            while (i < 256 || j >= 0) {
                if (i < 256) {
                    val p = network[i]
                    var dist = p[1].toInt() - gb
                    if (dist >= bestd) i = 256
                    else {
                        i++
                        if (dist < 0) dist = -dist
                        var a = p[0].toInt() - b
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            a = p[2].toInt() - r
                            if (a < 0) a = -a
                            dist += a
                            if (dist < bestd) {
                                bestd = dist
                                best = p[3].toInt()
                            }
                        }
                    }
                }
                if (j >= 0) {
                    val p = network[j]
                    var dist = gb - p[1].toInt()
                    if (dist >= bestd) j = -1
                    else {
                        j--
                        if (dist < 0) dist = -dist
                        var a = p[0].toInt() - b
                        if (a < 0) a = -a
                        dist += a
                        if (dist < bestd) {
                            a = p[2].toInt() - r
                            if (a < 0) a = -a
                            dist += a
                            if (dist < bestd) {
                                bestd = dist
                                best = p[3].toInt()
                            }
                        }
                    }
                }
            }
            return best
        }

        fun process(): ByteArray {
            learn()
            for (i in 0..255) network[i][3] = i.toDouble()
            inxbuild()
            return colorMap()
        }

        private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
            network[i][0] -= (alpha * (network[i][0] - b)) / (1 shl 10)
            network[i][1] -= (alpha * (network[i][1] - g)) / (1 shl 10)
            network[i][2] -= (alpha * (network[i][2] - r)) / (1 shl 10)
        }

        private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
            var lo = i - rad
            if (lo < -1) lo = -1
            var hi = i + rad
            if (hi > 256) hi = 256

            var j = i + 1
            var k = i - 1
            var m = 1
            while (j < hi || k > lo) {
                val a = radpower[m++]
                if (j < hi) {
                    val p = network[j++]
                    try {
                        p[0] -= (a * (p[0] - b)) / (1 shl 18)
                        p[1] -= (a * (p[1] - g)) / (1 shl 18)
                        p[2] -= (a * (p[2] - r)) / (1 shl 18)
                    } catch (e: Exception) {}
                }
                if (k > lo) {
                    val p = network[k--]
                    try {
                        p[0] -= (a * (p[0] - b)) / (1 shl 18)
                        p[1] -= (a * (p[1] - g)) / (1 shl 18)
                        p[2] -= (a * (p[2] - r)) / (1 shl 18)
                    } catch (e: Exception) {}
                }
            }
        }

        private fun contest(b: Int, g: Int, r: Int): Int {
            var bestd = Int.MAX_VALUE
            var bestbiasd = bestd
            var bestpos = -1
            var bestbiaspos = bestpos

            for (i in 0..255) {
                val n = network[i]
                var dist = abs(n[0].toInt() - b) + abs(n[1].toInt() - g) + abs(n[2].toInt() - r)
                if (dist < bestd) {
                    bestd = dist
                    bestpos = i
                }
                val biasdist = dist - (bias[i] shr 12)
                if (biasdist < bestbiasd) {
                    bestbiasd = biasdist
                    bestbiaspos = i
                }
                val betafreq = freq[i] shr 10
                freq[i] -= betafreq
                bias[i] += betafreq shl 10
            }
            freq[bestpos] += 1 shl 6
            bias[bestpos] -= 1 shl 16
            return bestbiaspos
        }
    }

    /**
     * LZW GIF Compressor
     * Adapted from Jef Poskanzer's Java port of gifsave.c
     */
    private class LZWEncoder(
        private val imgW: Int,
        private val imgH: Int,
        private val pixAry: ByteArray,
        private val initCodeSize: Int
    ) {
        private var cur_accum = 0
        private var cur_bits = 0
        private val masks = intArrayOf(
            0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F,
            0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
        )
        private var remaining = 0
        private var curPixel = 0
        private val htab = IntArray(5003)
        private val codetab = IntArray(5003)
        private val accum = ByteArray(256)
        private var a_count = 0

        fun encode(os: OutputStream) {
            os.write(initCodeSize)
            remaining = imgW * imgH
            curPixel = 0
            compress(initCodeSize + 1, os)
            os.write(0)
        }

        private fun compress(init_bits: Int, outs: OutputStream) {
            var n_bits = init_bits
            var maxcode = (1 shl n_bits) - 1
            val clear_flg = false
            val clear_code = 1 shl (init_bits - 1)
            val eof_code = clear_code + 1
            var free_ent = clear_code + 2

            var a_count = 0
            var ent = nextPixel()

            var hshift = 0
            var fcode = 5003
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            val hsize_reg = 5003
            for (i in 0 until hsize_reg) htab[i] = -1

            output(clear_code, outs, n_bits)

            outer_loop@ while (true) {
                val c = nextPixel()
                if (c == -1) break

                fcode = (c shl 12) + ent
                var i = (c shl hshift) xor ent

                if (htab[i] == fcode) {
                    ent = codetab[i]
                    continue
                } else if (htab[i] >= 0) {
                    var disp = hsize_reg - i
                    if (i == 0) disp = 1
                    do {
                        i -= disp
                        if (i < 0) i += hsize_reg
                        if (htab[i] == fcode) {
                            ent = codetab[i]
                            continue@outer_loop
                        }
                    } while (htab[i] >= 0)
                }

                output(ent, outs, n_bits)
                ent = c
                if (free_ent < 4096) {
                    codetab[i] = free_ent++
                    htab[i] = fcode
                } else {
                    for (idx in 0 until hsize_reg) htab[idx] = -1
                    free_ent = clear_code + 2
                    output(clear_code, outs, n_bits)
                    n_bits = init_bits
                    maxcode = (1 shl n_bits) - 1
                }

                if (free_ent > maxcode) {
                    n_bits++
                    maxcode = if (n_bits == 12) 4096 else (1 shl n_bits) - 1
                }
            }

            output(ent, outs, n_bits)
            output(eof_code, outs, n_bits)
        }

        private fun nextPixel(): Int {
            if (remaining == 0) return -1
            remaining--
            val pix = pixAry[curPixel++].toInt() and 0xff
            return pix
        }

        private fun output(code: Int, outs: OutputStream, n_bits: Int) {
            cur_accum = cur_accum or (code shl cur_bits)
            cur_bits += n_bits

            while (cur_bits >= 8) {
                accum[a_count++] = (cur_accum and 0xff).toByte()
                if (a_count >= 254) {
                    outs.write(a_count)
                    outs.write(accum, 0, a_count)
                    a_count = 0
                }
                cur_accum = cur_accum shr 8
                cur_bits -= 8
            }
        }
    }
}
