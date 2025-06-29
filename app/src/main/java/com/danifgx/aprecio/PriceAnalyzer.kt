package com.danifgx.aprecio

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

data class ProductInfo(
    val productName: String,
    val displayedPrice: Double,
    val weight: Double,
    val weightUnit: String,
    val pricePerKg: Double,
    val isDeceptive: Boolean,
    val confidence: Float,
    val boundingBox: Rect? = null,
    val isPricePerKg: Boolean = false // Indica si el precio mostrado es por kg
)

data class PriceAnalysisResult(
    val products: List<ProductInfo>,
    val totalProducts: Int
)

class PriceAnalyzer {
    
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    fun analyzeImage(bitmap: Bitmap, callback: (PriceAnalysisResult?) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                Log.d("PriceAnalyzer", "Extracted text: $extractedText")
                val result = analyzeMultipleProducts(visionText)
                Log.d("PriceAnalyzer", "Analysis result: $result")
                callback(result)
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                callback(null)
            }
    }
    
    private fun analyzeMultipleProducts(visionText: com.google.mlkit.vision.text.Text): PriceAnalysisResult? {
        val products = mutableListOf<ProductInfo>()
        
        // Agrupar bloques de texto por proximidad espacial
        val textBlocks = visionText.textBlocks
        
        for (block in textBlocks) {
            val blockText = block.text
            val boundingBox = block.boundingBox
            Log.d("PriceAnalyzer", "Processing block: $blockText at ${boundingBox}")
            
            val productInfo = extractProductFromBlock(blockText, boundingBox)
            if (productInfo != null) {
                products.add(productInfo)
                Log.d("PriceAnalyzer", "Found product: ${productInfo.productName} - ${productInfo.displayedPrice}€")
            }
        }
        
        return if (products.isNotEmpty()) {
            PriceAnalysisResult(products, products.size)
        } else {
            // Fallback al método anterior si no encuentra productos por bloques
            val legacyResult = extractPriceInfo(visionText.text)
            if (legacyResult != null) {
                val product = ProductInfo(
                    productName = "Producto",
                    displayedPrice = legacyResult.displayedPrice,
                    weight = legacyResult.weight,
                    weightUnit = legacyResult.weightUnit,
                    pricePerKg = legacyResult.pricePerKg,
                    isDeceptive = legacyResult.isDeceptive,
                    confidence = legacyResult.confidence
                )
                PriceAnalysisResult(listOf(product), 1)
            } else {
                null
            }
        }
    }
    
    private fun extractProductFromBlock(blockText: String, boundingBox: Rect?): ProductInfo? {
        val lines = blockText.split("\n").map { it.trim() }
        
        // Patrones para productos
        val productPatterns = listOf(
            "tomate\\s+(pera|rama)" to "Tomate",
            "pera" to "Tomate Pera", 
            "rama" to "Tomate Rama",
            "tomate" to "Tomate"
        )
        
        // Patrones mejorados para precios y pesos
        val pricePerKgPattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€?\\s*/\\s*k?g")
        val euroPerKgPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})\\s*/\\s*k?g")
        val priceWithWeightPattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€?.*?(\\d+)\\s*gr?a?m?o?s?")
        val simpleEuroPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})")
        val simplePricePattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€")
        val weightPattern = Pattern.compile("(\\d+)\\s*(gr?a?m?o?s?|g|kg|kilo)")
        
        var productName = "Producto"
        var price = 0.0
        var weight = 1.0
        var weightUnit = "kg"
        var isPricePerKg = false
        
        // Buscar nombre del producto
        for (line in lines) {
            val cleanLine = line.lowercase()
            for ((pattern, name) in productPatterns) {
                if (Pattern.compile(pattern).matcher(cleanLine).find()) {
                    productName = if (pattern.contains("pera")) "Tomate Pera" 
                                 else if (pattern.contains("rama")) "Tomate Rama"
                                 else name
                    break
                }
            }
        }
        
        // Buscar precio - primero intentar detectar precio por kg
        for (line in lines) {
            val cleanLine = line.replace(",", ".").lowercase()
            Log.d("PriceAnalyzer", "Analyzing line for prices: '$cleanLine'")
            
            // 1. Buscar X.XX€/kg
            val pricePerKgMatcher = pricePerKgPattern.matcher(cleanLine)
            if (pricePerKgMatcher.find()) {
                val priceStr = pricePerKgMatcher.group(1)
                price = priceStr?.toDoubleOrNull() ?: 0.0
                if (price > 0) {
                    isPricePerKg = true
                    Log.d("PriceAnalyzer", "Found price per kg: $price")
                    break
                }
            }
            
            // 2. Buscar €X.XX/kg
            val euroPerKgMatcher = euroPerKgPattern.matcher(cleanLine)
            if (euroPerKgMatcher.find()) {
                val priceStr = euroPerKgMatcher.group(1)
                price = priceStr?.toDoubleOrNull() ?: 0.0
                if (price > 0) {
                    isPricePerKg = true
                    Log.d("PriceAnalyzer", "Found euro per kg: $price")
                    break
                }
            }
        }
        
        // Si no encontramos precio por kg, buscar precio simple
        if (price == 0.0) {
            for (line in lines) {
                val cleanLine = line.replace(",", ".")
                
                // 3. Buscar €X.XX
                val euroMatcher = simpleEuroPattern.matcher(cleanLine)
                if (euroMatcher.find()) {
                    val priceStr = euroMatcher.group(1)
                    price = priceStr?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found simple euro price: $price")
                        break
                    }
                }
                
                // 4. Buscar X.XX€
                val priceMatcher = simplePricePattern.matcher(cleanLine)
                if (priceMatcher.find()) {
                    val priceStr = priceMatcher.group(1)
                    price = priceStr?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found simple price: $price")
                        break
                    }
                }
            }
        }
        
        return if (price > 0) {
            ProductInfo(
                productName = productName,
                displayedPrice = price,
                weight = 1.0, // Asumimos 1kg por defecto
                weightUnit = "kg",
                pricePerKg = price,
                isDeceptive = false, // Para productos simples no analizamos engaño
                confidence = if (productName != "Producto") 0.8f else 0.6f,
                boundingBox = boundingBox
            )
        } else {
            null
        }
    }
    
    // Método legacy mantenido para compatibilidad
    private fun extractPriceInfo(text: String): ProductInfo? {
        val lines = text.split("\n").map { it.trim() }
        Log.d("PriceAnalyzer", "Processing lines: $lines")
        
        // Patrones más flexibles para precios
        val euroPricePattern = Pattern.compile("(?:€\\s*)?(\\d+[.,]\\d{1,2})\\s*€?|€\\s*(\\d+[.,]\\d{1,2})")
        val simplePricePattern = Pattern.compile("(\\d+[.,]\\d{1,2})")
        val weightPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(gr?|g|kg|kilo|gramo|kilogramo)")
        val unitPricePattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})\\s*/\\s*(kg|kilo)")
        
        val prices = mutableListOf<Double>()
        var weight = 0.0
        var weightUnit = ""
        var unitPrice = 0.0
        var hasUnitPrice = false
        
        for (line in lines) {
            val cleanLine = line.lowercase().replace(",", ".")
            
            // Buscar precios con €
            val euroMatcher = euroPricePattern.matcher(cleanLine)
            while (euroMatcher.find()) {
                val price1 = euroMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()
                val price2 = euroMatcher.group(2)?.replace(",", ".")?.toDoubleOrNull()
                price1?.let { if (it > 0) prices.add(it) }
                price2?.let { if (it > 0) prices.add(it) }
            }
            
            // Si no encontramos precios con €, buscar números decimales
            if (prices.isEmpty()) {
                val simpleMatcher = simplePricePattern.matcher(cleanLine)
                while (simpleMatcher.find()) {
                    val price = simpleMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()
                    price?.let { if (it > 0 && it < 1000) prices.add(it) } // Filtrar precios razonables
                }
            }
            
            val weightMatcher = weightPattern.matcher(cleanLine)
            if (weightMatcher.find()) {
                val weightValue = weightMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                val unit = weightMatcher.group(2) ?: ""
                
                when {
                    unit.startsWith("kg") || unit.startsWith("kilo") -> {
                        weight = weightValue
                        weightUnit = "kg"
                    }
                    unit.startsWith("gr") || unit.startsWith("g") -> {
                        weight = weightValue / 1000.0
                        weightUnit = "g"
                    }
                }
            }
            
            val unitPriceMatcher = unitPricePattern.matcher(cleanLine)
            if (unitPriceMatcher.find()) {
                val pricePerKg = unitPriceMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()
                pricePerKg?.let { 
                    unitPrice = it
                    hasUnitPrice = true
                }
            }
        }
        
        if (prices.isNotEmpty()) {
            prices.sortDescending()
            val displayedPrice = prices.first()
            
            // Si no hay peso, asumimos 1kg para el cálculo
            val effectiveWeight = if (weight > 0) weight else 1.0
            val effectiveWeightUnit = if (weightUnit.isNotEmpty()) weightUnit else "kg"
            val calculatedPricePerKg = displayedPrice / effectiveWeight
            
            val isDeceptive = detectDeceptivePricing(
                displayedPrice, 
                prices, 
                effectiveWeight, 
                unitPrice,
                hasUnitPrice,
                text
            )
            
            Log.d("PriceAnalyzer", "Found prices: $prices, weight: $weight, effective weight: $effectiveWeight")
            
            return ProductInfo(
                productName = "Producto detectado",
                displayedPrice = displayedPrice,
                weight = effectiveWeight,
                weightUnit = effectiveWeightUnit,
                pricePerKg = calculatedPricePerKg,
                isDeceptive = isDeceptive && weight > 0, // Solo marcar como engañoso si hay peso real
                confidence = calculateConfidence(prices, effectiveWeight, hasUnitPrice)
            )
        }
        
        Log.d("PriceAnalyzer", "No prices found in text")
        return null
    }
    
    private fun detectDeceptivePricing(
        displayedPrice: Double,
        prices: List<Double>,
        weight: Double,
        unitPrice: Double,
        hasUnitPrice: Boolean,
        originalText: String
    ): Boolean {
        if (weight != 1.0) return true
        
        if (prices.size > 1) {
            val maxPrice = prices.maxOrNull() ?: 0.0
            val minPrice = prices.minOrNull() ?: 0.0
            val priceDifference = maxPrice - minPrice
            if (priceDifference > 0.5) return true
        }
        
        if (hasUnitPrice && unitPrice > 0) {
            val calculatedPricePerKg = displayedPrice / weight
            val difference = kotlin.math.abs(calculatedPricePerKg - unitPrice)
            if (difference > 0.1) return true
        }
        
        val hasSmallPrint = originalText.contains("kilo", ignoreCase = true) ||
                           originalText.contains("kg", ignoreCase = true) ||
                           originalText.contains("/kg", ignoreCase = true)
        
        if (hasSmallPrint && weight < 1.0) return true
        
        val suspiciousWeights = listOf(0.5, 0.75, 0.25, 0.33, 0.66)
        if (suspiciousWeights.any { kotlin.math.abs(weight - it) < 0.05 }) {
            return true
        }
        
        return false
    }
    
    private fun calculateConfidence(
        prices: List<Double>,
        weight: Double,
        hasUnitPrice: Boolean
    ): Float {
        var confidence = 0.5f
        
        if (prices.isNotEmpty()) confidence += 0.2f
        if (weight > 0) confidence += 0.2f
        if (hasUnitPrice) confidence += 0.1f
        
        return confidence.coerceAtMost(1.0f)
    }
}