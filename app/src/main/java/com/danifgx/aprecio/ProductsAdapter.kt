package com.danifgx.aprecio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ProductsAdapter(
    private var products: List<ProductInfo>,
    private val onProductClick: (ProductInfo, Int) -> Unit
) : RecyclerView.Adapter<ProductsAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productNumber: TextView = itemView.findViewById(R.id.productNumber)
        val productName: TextView = itemView.findViewById(R.id.productName)
        val productPrice: TextView = itemView.findViewById(R.id.productPrice)
        val productDetails: TextView = itemView.findViewById(R.id.productDetails)
        val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        val productNumber = position + 1

        holder.productNumber.text = productNumber.toString()
        holder.productName.text = product.productName
        holder.productPrice.text = "${product.displayedPrice}€"
        
        val details = if (product.weight != 1.0) {
            "${String.format("%.2f", product.pricePerKg)}€/kg • ${product.weight}${product.weightUnit}"
        } else {
            "${String.format("%.2f", product.pricePerKg)}€/kg"
        }
        holder.productDetails.text = details

        // Si es producto engañoso, marcar en rojo
        if (product.isDeceptive) {
            val redColor = ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
            val whiteColor = ContextCompat.getColor(holder.itemView.context, android.R.color.white)
            
            // Fondo rojo para el item completo
            holder.itemView.setBackgroundResource(0) // Remover fondo de card
            holder.itemView.setBackgroundColor(redColor)
            
            // Textos en blanco para contraste
            holder.productNumber.setTextColor(whiteColor)
            holder.productName.setTextColor(whiteColor)
            holder.productPrice.setTextColor(whiteColor)
            holder.productDetails.setTextColor(whiteColor)
            
            // Añadir texto de alerta
            holder.productDetails.text = details + " ⚠️ POSIBLE ENGAÑO"
        } else {
            // Restaurar apariencia normal de la card - dejar que MaterialCardView maneje su propio fondo
            holder.itemView.setBackgroundResource(0)
            holder.itemView.background = null
            
            // Restaurar colores por defecto del XML
            val defaultTextColor = ContextCompat.getColor(holder.itemView.context, android.R.color.black)
            val numberTextColor = ContextCompat.getColor(holder.itemView.context, android.R.color.white) // El número siempre blanco
            val priceTextColor = ContextCompat.getColor(holder.itemView.context, android.R.color.holo_blue_dark)
            val detailTextColor = ContextCompat.getColor(holder.itemView.context, android.R.color.darker_gray)
            
            holder.productNumber.setTextColor(numberTextColor)
            holder.productName.setTextColor(defaultTextColor)
            holder.productPrice.setTextColor(priceTextColor)
            holder.productDetails.setTextColor(detailTextColor)
        }

        holder.itemView.setOnClickListener {
            onProductClick(product, productNumber)
        }
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<ProductInfo>) {
        products = newProducts
        notifyDataSetChanged()
    }
}