package com.danifgx.aprecio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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