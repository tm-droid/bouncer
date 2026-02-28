@file:Suppress("DEPRECATION")

package com.tanush.bouncer.ui.transformations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation

class BlurTransformation(
    private val context: Context,
    private val radius: Float = 10f,
    private val sampling: Float = 1f
) : Transformation {

    override val cacheKey: String = "${javaClass.name}-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scaledWidth = (input.width / sampling).toInt()
        val scaledHeight = (input.height / sampling).toInt()
        val output = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(output)
        canvas.scale(1 / sampling, 1 / sampling)
        canvas.drawBitmap(input, 0f, 0f, paint)

        var rs: RenderScript? = null
        var inputAllocation: Allocation? = null
        var outputAllocation: Allocation? = null
        var blurScript: ScriptIntrinsicBlur? = null

        try {
            rs = RenderScript.create(context)
            inputAllocation = Allocation.createFromBitmap(rs, output, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)
            outputAllocation = Allocation.createTyped(rs, inputAllocation.type)
            blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blurScript.setRadius(radius.coerceIn(0f, 25f))
            blurScript.setInput(inputAllocation)
            blurScript.forEach(outputAllocation)
            outputAllocation.copyTo(output)
        } catch (e: Exception) {
            // Fallback or ignore
        } finally {
            rs?.destroy()
            inputAllocation?.destroy()
            outputAllocation?.destroy()
            blurScript?.destroy()
        }

        return output
    }
}
