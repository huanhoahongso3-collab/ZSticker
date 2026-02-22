fun removeBackground(bitmap: Bitmap): Bitmap? {
    val segmenter = imageSegmenter ?: return null
    
    // 1. FORCE SOFTWARE BITMAP (Crucial for Android 8 compatibility)
    // MediaPipe can fail on older versions if the bitmap isn't explicitly ARGB_8888
    val softwareBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    } else {
        bitmap
    }

    val mpImage = BitmapImageBuilder(softwareBitmap).build()
    val result = segmenter.segment(mpImage)
    
    val categoryMask = result.categoryMask()
    if (!categoryMask.isPresent) return null
    
    val mask = categoryMask.get()
    val w = mask.width
    val h = mask.height
    
    // 2. Prepare the pixel array
    val pixels = IntArray(w * h)
    softwareBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    
    // 3. UNIVERSAL BUFFER EXTRACTION
    val byteBuffer = ByteBufferExtractor.extract(mask)
    byteBuffer.rewind()

    // 4. ROBUST MASK LOOP
    // Using a limit check and absolute positioning to prevent BufferOverflow/Underflow
    val limit = byteBuffer.limit()
    for (i in 0 until (w * h)) {
        if (i < limit) {
            val maskValue = byteBuffer.get(i).toInt() and 0xFF
            
            // Your logic: If NOT category 0 (the person), make it transparent
            if (maskValue != 0) {
                pixels[i] = Color.TRANSPARENT
            }
        }
    }
    
    // 5. Create final bitmap
    val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

    // Cleanup the copy if we made one
    if (softwareBitmap != bitmap) softwareBitmap.recycle()

    return outBitmap
}
