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
                // Crear overlay del número
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
                
                // Si es engañoso, agregar reborde rojo
                if (product.isDeceptive) {
                    val borderView = createDeceptiveBorderView(boundingBox, scaleX, scaleY)
                    overlayContainer.addView(borderView)
                }
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
            
            // Si es engañoso, usar fondo rojo, sino el fondo normal
            if (product.isDeceptive) {
                background = ContextCompat.getDrawable(context, R.drawable.circle_background)
                background?.setTint(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setTypeface(null, android.graphics.Typeface.BOLD)
                textSize = 18f // Más grande para destacar
            } else {
                background = ContextCompat.getDrawable(context, R.drawable.circle_background)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 16f
            }
            
            setPadding(16, 16, 16, 16)
            gravity = android.view.Gravity.CENTER
            
            // Hacer clic
            setOnClickListener {
                onOverlayClick(product, number)
            }
            
            // Configurar tamaño mínimo, más grande si es engañoso
            if (product.isDeceptive) {
                minWidth = 80
                minHeight = 80
            } else {
                minWidth = 64
                minHeight = 64
            }
        }
        
        return textView
    }
    
    private fun createDeceptiveBorderView(boundingBox: Rect, scaleX: Float, scaleY: Float): View {
        val borderView = View(context).apply {
            // Crear un drawable personalizado para el reborde cuadrado
            val drawable = ContextCompat.getDrawable(context, android.R.drawable.edit_text)
            drawable?.setTint(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            background = drawable
            alpha = 0.8f // Un poco transparente para ver el contenido
        }
        
        val padding = 20 // Padding alrededor del cartel
        val layoutParams = FrameLayout.LayoutParams(
            (boundingBox.width() * scaleX + padding * 2).toInt(),
            (boundingBox.height() * scaleY + padding * 2).toInt()
        )
        
        val scaledLeft = (boundingBox.left * scaleX - padding).toInt()
        val scaledTop = (boundingBox.top * scaleY - padding).toInt()
        
        layoutParams.leftMargin = scaledLeft
        layoutParams.topMargin = scaledTop
        
        borderView.layoutParams = layoutParams
        return borderView
    }
    
    fun hideOverlays() {
        overlayContainer.visibility = View.GONE
        overlayContainer.removeAllViews()
    }
}