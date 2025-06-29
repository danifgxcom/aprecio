package com.danifgx.aprecio

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class OverlayManager(
    private val context: Context,
    private val overlayContainer: FrameLayout
) {
    
    fun showProductOverlays(
        products: List<ProductInfo>,
        imageWidth: Int,
        imageHeight: Int,
        onOverlayClick: (ProductInfo, Int) -> Unit
    ) {
        // Limpiar overlays anteriores
        overlayContainer.removeAllViews()
        
        products.forEachIndexed { index, product ->
            val productNumber = index + 1
            val boundingBox = product.boundingBox
            
            if (boundingBox != null) {
                val overlayView = createOverlayView(productNumber, product, onOverlayClick)
                
                // Calcular la posición del overlay en la imagen
                val layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                
                // Escalar las coordenadas del bounding box a las dimensiones de la ImageView
                val scaleX = overlayContainer.width.toFloat() / imageWidth
                val scaleY = overlayContainer.height.toFloat() / imageHeight
                
                val scaledLeft = (boundingBox.left * scaleX).toInt()
                val scaledTop = (boundingBox.top * scaleY).toInt()
                
                layoutParams.leftMargin = scaledLeft
                layoutParams.topMargin = scaledTop
                
                overlayView.layoutParams = layoutParams
                overlayContainer.addView(overlayView)
            }
        }
        
        overlayContainer.visibility = View.VISIBLE
    }
    
    private fun createOverlayView(
        number: Int,
        product: ProductInfo,
        onOverlayClick: (ProductInfo, Int) -> Unit
    ): View {
        val textView = TextView(context).apply {
            text = number.toString()
            background = ContextCompat.getDrawable(context, R.drawable.circle_background)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 16f
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER
            
            // Hacer clic
            setOnClickListener {
                onOverlayClick(product, number)
            }
            
            // Configurar tamaño mínimo
            minWidth = 64
            minHeight = 64
        }
        
        return textView
    }
    
    fun hideOverlays() {
        overlayContainer.visibility = View.GONE
        overlayContainer.removeAllViews()
    }
}