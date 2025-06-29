package com.danifgx.aprecio

import android.graphics.Bitmap
import android.graphics.Color
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
        // Primero detectar regiones de etiquetas blancas
        val whiteRegions = detectWhiteLabelRegions(bitmap)
        Log.d("PriceAnalyzer", "Detected ${whiteRegions.size} white label regions")
        
        val image = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                Log.d("PriceAnalyzer", "Extracted text: $extractedText")
                val result = analyzeMultipleProductsWithRegions(visionText, whiteRegions)
                Log.d("PriceAnalyzer", "Analysis result: $result")
                callback(result)
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                callback(null)
            }
    }
    
    private fun analyzeMultipleProductsWithRegions(visionText: com.google.mlkit.vision.text.Text, whiteRegions: List<Rect>): PriceAnalysisResult? {
        val products = mutableListOf<ProductInfo>()
        
        // Filtrar bloques de texto que están dentro de las regiones blancas detectadas
        val textBlocksInWhiteRegions = visionText.textBlocks.filter { block ->
            val blockBox = block.boundingBox ?: return@filter false
            whiteRegions.any { whiteRegion ->
                isBlockInRegion(blockBox, whiteRegion)
            }
        }
        
        Log.d("PriceAnalyzer", "Found ${textBlocksInWhiteRegions.size} text blocks in white regions out of ${visionText.textBlocks.size} total blocks")
        
        // Si encontramos bloques en regiones blancas, procesarlos preferentemente
        if (textBlocksInWhiteRegions.isNotEmpty()) {
            val groupedBlocks = groupBlocksByProximity(textBlocksInWhiteRegions)
            
            for ((index, group) in groupedBlocks.withIndex()) {
                val combinedText = group.joinToString("\\n") { it.text }
                val combinedBoundingBox = calculateCombinedBlockBoundingBox(group)
                
                Log.d("PriceAnalyzer", "Processing white region group $index: $combinedText at $combinedBoundingBox")
                
                val productInfo = extractProductFromBlock(combinedText, combinedBoundingBox)
                if (productInfo != null) {
                    products.add(productInfo)
                    Log.d("PriceAnalyzer", "Found product in white region: ${productInfo.productName} - ${productInfo.displayedPrice}€")
                }
            }
        }
        
        // Si no encontramos suficientes productos en regiones blancas, usar el método anterior como fallback
        if (products.size < 2) {
            Log.d("PriceAnalyzer", "Not enough products found in white regions, falling back to general analysis")
            return analyzeMultipleProducts(visionText)
        }
        
        return if (products.isNotEmpty()) {
            PriceAnalysisResult(products, products.size)
        } else {
            null
        }
    }
    
    private fun isBlockInRegion(blockBox: Rect, whiteRegion: Rect): Boolean {
        // Verificar si el bloque de texto está dentro o se superpone significativamente con la región blanca
        val intersection = Rect()
        if (intersection.setIntersect(blockBox, whiteRegion)) {
            val intersectionArea = intersection.width() * intersection.height()
            val blockArea = blockBox.width() * blockBox.height()
            // Si al menos el 30% del bloque está en la región blanca, considerarlo válido
            return intersectionArea.toFloat() / blockArea > 0.3f
        }
        return false
    }
    
    private fun analyzeMultipleProducts(visionText: com.google.mlkit.vision.text.Text): PriceAnalysisResult? {
        val products = mutableListOf<ProductInfo>()
        
        // Agrupar bloques por columnas (carteles lado a lado)
        val columnGroups = groupBlocksByColumns(visionText.textBlocks)
        
        Log.d("PriceAnalyzer", "Found ${columnGroups.size} column groups")
        
        for ((index, group) in columnGroups.withIndex()) {
            val combinedText = group.joinToString("\\n") { it.text }
            val combinedBoundingBox = calculateCombinedBlockBoundingBox(group)
            
            Log.d("PriceAnalyzer", "Processing column $index: $combinedText at $combinedBoundingBox")
            
            val productInfo = extractProductFromBlock(combinedText, combinedBoundingBox)
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
        
        // Patrones expandidos para productos
        val productPatterns = listOf(
            "tomate\\s+(pera|cherry|rama|natural)" to "Tomate",
            "pera" to "Tomate Pera", 
            "rama" to "Tomate Rama",
            "cherry" to "Tomate Cherry",
            "tomate" to "Tomate",
            "patata\\s+(blanca|roja|nueva)" to "Patata",
            "patata" to "Patata",
            "cebolla" to "Cebolla",
            "lechuga" to "Lechuga",
            "zanahoria" to "Zanahoria",
            "pimiento" to "Pimiento",
            "calabac[íi]n" to "Calabacín",
            "berenjena" to "Berenjena",
            "pepino" to "Pepino",
            "apio" to "Apio",
            "br[óo]coli" to "Brócoli",
            "coliflor" to "Coliflor",
            "espinaca" to "Espinaca",
            "r[úu]cula" to "Rúcula",
            "acelga" to "Acelga",
            "nabo" to "Nabo",
            "r[áa]bano" to "Rábano",
            "puerro" to "Puerro",
            "ajo" to "Ajo",
            "jengibre" to "Jengibre",
            "lim[óo]n" to "Limón",
            "naranja" to "Naranja",
            "manzana" to "Manzana",
            "pl[áa]tano" to "Plátano",
            "pera\\s+(fruta)" to "Pera",
            "pera(?!.*tomate)" to "Pera",
            "melocot[óo]n" to "Melocotón",
            "albaricoque" to "Albaricoque",
            "ciruela" to "Ciruela",
            "uva" to "Uva",
            "fresa" to "Fresa",
            "sand[íi]a" to "Sandía",
            "mel[óo]n" to "Melón",
            "pi[ñn]a" to "Piña",
            "kiwi" to "Kiwi",
            "aguacate" to "Aguacate",
            "mango" to "Mango",
            "papaya" to "Papaya"
        )
        
        // Patrones mejorados para precios con formatos españoles
        val pricePerKgPattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€?\\s*/\\s*k?g")
        val euroPerKgPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})\\s*/\\s*k?g")
        val priceWithWeightPattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€?.*?(\\d+)\\s*gr?a?m?o?s?")
        val simpleEuroPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})")
        val simplePricePattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€")
        // Patrones adicionales para formatos españoles
        val spanishPricePattern = Pattern.compile("(\\d+)[.,](\\d{2})\\s*euros?")
        val centPattern = Pattern.compile("(\\d+)\\s*c[eé]ntimos?")
        val mixedPricePattern = Pattern.compile("(\\d+)\\s*euros?\\s*y\\s*(\\d+)\\s*c[eé]ntimos?")
        val euroSymbolPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})|\\b(\\d+[.,]\\d{1,2})\\s*€")
        // Patrón para precios sin símbolo (como 159., 229.)
        val priceWithDotPattern = Pattern.compile("(\\d+[.,]\\d{1,2})[.:]?$")
        val simplePriceNoSymbolPattern = Pattern.compile("^(\\d{1,2}[.,]\\d{2})[.:]?$")
        // Patrones específicos para detectar precios como en la imagen
        val commaNumberPattern = Pattern.compile("(\\d{1,2})[.,](\\d{2})[.:]?\\s*$")
        val threeDigitPattern = Pattern.compile("^(\\d{3})[.:]\\s*$")
        // Patrones de peso mejorados para detectar decimales y fracciones
        val weightPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?|0[.,][0-9]+)\\s*(gr?a?m?o?s?|g|kg|kilo|kilogramo)")
        val fractionWeightPattern = Pattern.compile("(medio|media|1/2|½)\\s*(kilo|kg|kilogramo)")
        val wholeWeightPattern = Pattern.compile("(un|1|dos|2|tres|3)\\s*(kilo|kg|kilogramo)")
        val decimalWeightPattern = Pattern.compile("(0[.,]5|0[.,]25|0[.,]75|1[.,]5|2[.,]5)\\s*(kg|kilo|kilogramo)")
        
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
                    productName = when {
                        cleanLine.contains("tomate") && cleanLine.contains("pera") -> "Tomate Pera"
                        cleanLine.contains("tomate") && cleanLine.contains("rama") -> "Tomate Rama"
                        cleanLine.contains("tomate") && cleanLine.contains("cherry") -> "Tomate Cherry"
                        pattern.contains("pera") && pattern.contains("fruta") -> "Pera"
                        pattern.contains("rama") && !cleanLine.contains("tomate") -> "Tomate Rama"
                        pattern.contains("pera") && !cleanLine.contains("tomate") -> "Tomate Pera"
                        else -> name
                    }
                    Log.d("PriceAnalyzer", "Detected product: $productName from pattern: $pattern")
                    break
                }
            }
            if (productName != "Producto") break // Si ya encontramos un producto, salir
        }
        
        // Buscar peso primero para determinar si es por kg o por unidad
        for (line in lines) {
            val cleanLine = line.replace(",", ".").lowercase()
            Log.d("PriceAnalyzer", "Analyzing line for weight: '$cleanLine'")
            
            // Detectar fracciones como "medio kilo", "1/2 kg", "½ kg"
            val fractionMatcher = fractionWeightPattern.matcher(cleanLine)
            if (fractionMatcher.find()) {
                weight = 0.5
                weightUnit = "kg"
                Log.d("PriceAnalyzer", "Found fraction weight: 0.5 kg")
                break
            }
            
            // Detectar números enteros como "un kilo", "1 kg", "dos kilos"
            val wholeMatcher = wholeWeightPattern.matcher(cleanLine)
            if (wholeMatcher.find()) {
                val weightStr = wholeMatcher.group(1)
                weight = when (weightStr) {
                    "un", "1" -> 1.0
                    "dos", "2" -> 2.0
                    "tres", "3" -> 3.0
                    else -> 1.0
                }
                weightUnit = "kg"
                Log.d("PriceAnalyzer", "Found whole weight: $weight kg")
                break
            }
            
            // Detectar pesos decimales como "0.5 kg", "1.5 kg"
            val decimalMatcher = decimalWeightPattern.matcher(cleanLine)
            if (decimalMatcher.find()) {
                val weightStr = decimalMatcher.group(1)?.replace(",", ".")
                weight = weightStr?.toDoubleOrNull() ?: 1.0
                weightUnit = "kg"
                Log.d("PriceAnalyzer", "Found decimal weight: $weight kg")
                break
            }
            
            // Patrón general de peso
            val weightMatcher = weightPattern.matcher(cleanLine)
            if (weightMatcher.find()) {
                val weightStr = weightMatcher.group(1)?.replace(",", ".")
                val unit = weightMatcher.group(2) ?: ""
                val weightValue = weightStr?.toDoubleOrNull() ?: 0.0
                
                if (weightValue > 0) {
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
                    Log.d("PriceAnalyzer", "Found general weight: $weight $weightUnit")
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
        
        // Si no encontramos precio por kg, buscar precio simple con formatos españoles
        if (price == 0.0) {
            // También buscar en el texto completo para casos multilinea
            val allLines = lines + listOf(blockText.replace("\\n", " "))
            
            for (line in allLines) {
                val cleanLine = line.replace(",", ".").lowercase()
                
                // 3. Buscar €X.XX o X.XX€ con símbolo euro
                val euroSymbolMatcher = euroSymbolPattern.matcher(cleanLine)
                if (euroSymbolMatcher.find()) {
                    val priceStr1 = euroSymbolMatcher.group(1)
                    val priceStr2 = euroSymbolMatcher.group(2)
                    price = (priceStr1 ?: priceStr2)?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found euro symbol price: $price")
                        break
                    }
                }
                
                // 4. Buscar "X euros Y céntimos"
                val mixedMatcher = mixedPricePattern.matcher(cleanLine)
                if (mixedMatcher.find()) {
                    val euros = mixedMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val cents = mixedMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                    price = euros + (cents / 100.0)
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found mixed price: $price (${euros} euros + ${cents} céntimos)")
                        break
                    }
                }
                
                // 5. Buscar "X.XX euros"
                val spanishMatcher = spanishPricePattern.matcher(cleanLine)
                if (spanishMatcher.find()) {
                    val euros = spanishMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val cents = spanishMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                    price = euros + (cents / 100.0)
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found spanish price: $price")
                        break
                    }
                }
                
                // 6. Buscar solo céntimos
                val centMatcher = centPattern.matcher(cleanLine)
                if (centMatcher.find()) {
                    val cents = centMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                    price = cents / 100.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found cents price: $price (${cents} céntimos)")
                        break
                    }
                }
                
                // 7. Buscar €X.XX (fallback)
                val euroMatcher = simpleEuroPattern.matcher(cleanLine)
                if (euroMatcher.find()) {
                    val priceStr = euroMatcher.group(1)
                    price = priceStr?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found simple euro price: $price")
                        break
                    }
                }
                
                // 8. Buscar X.XX€ (fallback)
                val priceMatcher = simplePricePattern.matcher(cleanLine)
                if (priceMatcher.find()) {
                    val priceStr = priceMatcher.group(1)
                    price = priceStr?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found simple price: $price")
                        break
                    }
                }
                
                // 9. Buscar precios como "2,19:", "1,39." con coma
                val commaMatcher = commaNumberPattern.matcher(line.trim())
                if (commaMatcher.find()) {
                    val euros = commaMatcher.group(1)?.toIntOrNull() ?: 0
                    val cents = commaMatcher.group(2)?.toIntOrNull() ?: 0
                    price = euros + (cents / 100.0)
                    // Redondear para evitar problemas de precisión
                    price = kotlin.math.round(price * 100) / 100.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found comma price: $price (${euros},${cents})")
                        break
                    }
                }
                
                // 10. Buscar precios de 3 dígitos como "159.", "229." 
                val threeDigitMatcher = threeDigitPattern.matcher(line.trim())
                if (threeDigitMatcher.find()) {
                    val digits = threeDigitMatcher.group(1)?.toIntOrNull() ?: 0
                    // Convertir 159 -> 1.59, 229 -> 2.29
                    if (digits >= 100 && digits < 1000) {
                        price = digits / 100.0
                        Log.d("PriceAnalyzer", "Found three digit price: $price (from $digits)")
                        break
                    }
                }
                
                // 11. Buscar también en texto que incluye salto de línea
                if (line.contains("\\n")) {
                    val lineparts = line.split("\\n")
                    for (part in lineparts) {
                        val threeDigitMatcher2 = threeDigitPattern.matcher(part.trim())
                        if (threeDigitMatcher2.find()) {
                            val digits = threeDigitMatcher2.group(1)?.toIntOrNull() ?: 0
                            if (digits >= 100 && digits < 1000) {
                                price = digits / 100.0
                                Log.d("PriceAnalyzer", "Found three digit price in multiline: $price (from $digits)")
                                break
                            }
                        }
                    }
                    if (price > 0) break
                }
                
                // 11. Buscar precios sin símbolo como fallback
                val priceNoDotMatcher = simplePriceNoSymbolPattern.matcher(line.trim())
                if (priceNoDotMatcher.find()) {
                    val priceStr = priceNoDotMatcher.group(1)?.replace(",", ".")
                    price = priceStr?.toDoubleOrNull() ?: 0.0
                    if (price > 0) {
                        Log.d("PriceAnalyzer", "Found price without symbol: $price")
                        break
                    }
                }
            }
        }
        
        return if (price > 0) {
            val finalWeight = if (weight > 0) weight else 1.0
            val finalPricePerKg = if (isPricePerKg) price else price / finalWeight
            // Redondear precio final para evitar problemas de precisión
            val roundedPrice = kotlin.math.round(price * 100) / 100.0
            val roundedPricePerKg = kotlin.math.round(finalPricePerKg * 100) / 100.0
            
            ProductInfo(
                productName = productName,
                displayedPrice = roundedPrice,
                weight = finalWeight,
                weightUnit = weightUnit,
                pricePerKg = roundedPricePerKg,
                isDeceptive = detectDeceptivePricing(roundedPrice, listOf(roundedPrice), finalWeight, roundedPricePerKg, isPricePerKg, blockText),
                confidence = calculateConfidence(listOf(roundedPrice), finalWeight, isPricePerKg, productName != "Producto"),
                boundingBox = adjustBoundingBoxForOverlay(boundingBox ?: createDefaultBoundingBox()),
                isPricePerKg = isPricePerKg
            )
        } else if (productName != "Producto") {
            // Crear producto sin precio si al menos tenemos un nombre válido
            ProductInfo(
                productName = productName,
                displayedPrice = 0.0,
                weight = 1.0,
                weightUnit = "kg",
                pricePerKg = 0.0,
                isDeceptive = false,
                confidence = 0.3f,
                boundingBox = adjustBoundingBoxForOverlay(boundingBox ?: createDefaultBoundingBox()),
                isPricePerKg = false
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
        hasUnitPrice: Boolean,
        hasProductName: Boolean = false
    ): Float {
        var confidence = 0.3f
        
        if (prices.isNotEmpty()) confidence += 0.2f
        if (weight > 0 && weight != 1.0) confidence += 0.2f // Mejor si detectamos peso específico
        if (hasUnitPrice) confidence += 0.1f
        if (hasProductName) confidence += 0.2f // Bonus si detectamos nombre específico
        
        return confidence.coerceAtMost(1.0f)
    }
    
    private fun groupBlocksByProximity(textBlocks: List<com.google.mlkit.vision.text.Text.TextBlock>): List<List<com.google.mlkit.vision.text.Text.TextBlock>> {
        val groups = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.TextBlock>>()
        val processed = mutableSetOf<com.google.mlkit.vision.text.Text.TextBlock>()
        
        for (block in textBlocks) {
            if (block in processed) continue
            
            val group = mutableListOf(block)
            processed.add(block)
            
            val blockBox = block.boundingBox ?: continue
            
            // Buscar bloques cercanos
            for (otherBlock in textBlocks) {
                if (otherBlock in processed) continue
                
                val otherBox = otherBlock.boundingBox ?: continue
                
                // Calcular distancia entre bloques
                val distance = calculateBlockDistance(blockBox, otherBox)
                val maxDistance = 200 // Aumentar distancia para agrupar mejor precios con productos
                
                if (distance < maxDistance) {
                    group.add(otherBlock)
                    processed.add(otherBlock)
                }
            }
            
            groups.add(group)
        }
        
        return groups
    }
    
    private fun calculateCombinedBlockBoundingBox(blocks: List<com.google.mlkit.vision.text.Text.TextBlock>): Rect? {
        if (blocks.isEmpty()) return null
        
        val boxes = blocks.mapNotNull { it.boundingBox }
        if (boxes.isEmpty()) return null
        
        val left = boxes.minOf { it.left }
        val top = boxes.minOf { it.top }
        val right = boxes.maxOf { it.right }
        val bottom = boxes.maxOf { it.bottom }
        
        return Rect(left, top, right, bottom)
    }
    
    private fun calculateBlockDistance(box1: Rect, box2: Rect): Double {
        val centerX1 = box1.centerX()
        val centerY1 = box1.centerY()
        val centerX2 = box2.centerX()
        val centerY2 = box2.centerY()
        
        val dx = (centerX2 - centerX1).toDouble()
        val dy = (centerY2 - centerY1).toDouble()
        
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    private fun createDefaultBoundingBox(): Rect {
        // Crear un boundingBox por defecto para productos sin coordenadas
        return Rect(0, 0, 100, 50)
    }
    
    private fun adjustBoundingBoxForOverlay(boundingBox: Rect): Rect {
        // Ajustar boundingBox para que el overlay aparezca ARRIBA del cartel
        val centerX = boundingBox.centerX()
        val topY = boundingBox.top - 40 // Colocar arriba del cartel
        
        // Crear un punto arriba del cartel para el overlay
        return Rect(
            centerX - 25, 
            topY - 25, 
            centerX + 25, 
            topY + 25
        )
    }
    
    /**
     * Detecta etiquetas blancas cuadradas con texto negro características de este supermercado
     */
    private fun detectWhiteLabelRegions(bitmap: Bitmap): List<Rect> {
        val regions = mutableListOf<Rect>()
        val width = bitmap.width
        val height = bitmap.height
        
        // Procesar imagen en bloques para encontrar regiones blancas
        val blockSize = 50 // Tamaño del bloque para análisis
        
        for (y in 0 until height - blockSize step blockSize/2) {
            for (x in 0 until width - blockSize step blockSize/2) {
                val endX = minOf(x + blockSize, width)
                val endY = minOf(y + blockSize, height)
                
                if (isWhiteLabelRegion(bitmap, x, y, endX, endY)) {
                    // Expandir la región para capturar toda la etiqueta
                    val expandedRegion = expandWhiteRegion(bitmap, x, y, endX, endY)
                    if (isValidLabelSize(expandedRegion)) {
                        regions.add(expandedRegion)
                    }
                }
            }
        }
        
        return mergeOverlappingRegions(regions)
    }
    
    private fun isWhiteLabelRegion(bitmap: Bitmap, startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        var whitePixels = 0
        var totalPixels = 0
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                // Considerar píxeles claros (blancos/grises claros) como parte de la etiqueta
                if (brightness > 200) {
                    whitePixels++
                }
                totalPixels++
            }
        }
        
        // Si más del 70% de los píxeles son claros, es potencialmente una etiqueta
        return whitePixels.toFloat() / totalPixels > 0.7f
    }
    
    private fun expandWhiteRegion(bitmap: Bitmap, initialX: Int, initialY: Int, initialEndX: Int, initialEndY: Int): Rect {
        var left = initialX
        var top = initialY
        var right = initialEndX
        var bottom = initialEndY
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Expandir hacia la izquierda
        while (left > 0) {
            if (!isColumnMostlyWhite(bitmap, left - 1, top, bottom)) break
            left--
        }
        
        // Expandir hacia la derecha
        while (right < width - 1) {
            if (!isColumnMostlyWhite(bitmap, right + 1, top, bottom)) break
            right++
        }
        
        // Expandir hacia arriba
        while (top > 0) {
            if (!isRowMostlyWhite(bitmap, left, right, top - 1)) break
            top--
        }
        
        // Expandir hacia abajo
        while (bottom < height - 1) {
            if (!isRowMostlyWhite(bitmap, left, right, bottom + 1)) break
            bottom++
        }
        
        return Rect(left, top, right, bottom)
    }
    
    private fun isColumnMostlyWhite(bitmap: Bitmap, x: Int, startY: Int, endY: Int): Boolean {
        var whitePixels = 0
        var totalPixels = 0
        
        for (y in startY until endY) {
            val pixel = bitmap.getPixel(x, y)
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            if (brightness > 200) whitePixels++
            totalPixels++
        }
        
        return whitePixels.toFloat() / totalPixels > 0.6f
    }
    
    private fun isRowMostlyWhite(bitmap: Bitmap, startX: Int, endX: Int, y: Int): Boolean {
        var whitePixels = 0
        var totalPixels = 0
        
        for (x in startX until endX) {
            val pixel = bitmap.getPixel(x, y)
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            if (brightness > 200) whitePixels++
            totalPixels++
        }
        
        return whitePixels.toFloat() / totalPixels > 0.6f
    }
    
    private fun isValidLabelSize(region: Rect): Boolean {
        val width = region.width()
        val height = region.height()
        
        // Las etiquetas de supermercado suelen ser cuadradas o rectangulares
        // Tamaño mínimo y máximo razonable
        return width > 80 && height > 60 && width < 500 && height < 400
    }
    
    private fun mergeOverlappingRegions(regions: List<Rect>): List<Rect> {
        if (regions.isEmpty()) return emptyList()
        
        val merged = mutableListOf<Rect>()
        val processed = mutableSetOf<Int>()
        
        for (i in regions.indices) {
            if (i in processed) continue
            
            var currentRegion = regions[i]
            processed.add(i)
            
            // Buscar regiones que se superponen
            for (j in regions.indices) {
                if (j in processed) continue
                
                if (Rect.intersects(currentRegion, regions[j])) {
                    // Fusionar regiones
                    currentRegion = Rect(
                        minOf(currentRegion.left, regions[j].left),
                        minOf(currentRegion.top, regions[j].top),
                        maxOf(currentRegion.right, regions[j].right),
                        maxOf(currentRegion.bottom, regions[j].bottom)
                    )
                    processed.add(j)
                }
            }
            
            merged.add(currentRegion)
        }
        
        return merged
    }
    
    private fun groupBlocksByColumns(textBlocks: List<com.google.mlkit.vision.text.Text.TextBlock>): List<List<com.google.mlkit.vision.text.Text.TextBlock>> {
        if (textBlocks.isEmpty()) return emptyList()
        
        // Ordenar bloques por posición X (izquierda a derecha)
        val sortedBlocks = textBlocks.sortedBy { it.boundingBox?.left ?: 0 }
        
        val columns = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.TextBlock>>()
        
        for (block in sortedBlocks) {
            val blockBox = block.boundingBox ?: continue
            val blockCenterX = blockBox.centerX()
            
            // Buscar columna existente donde este bloque encaje
            var foundColumn = false
            for (column in columns) {
                val columnCenterX = column.firstOrNull()?.boundingBox?.centerX() ?: continue
                
                // Si está cerca horizontalmente (misma columna), agregarlo
                if (kotlin.math.abs(blockCenterX - columnCenterX) < 150) {
                    column.add(block)
                    foundColumn = true
                    break
                }
            }
            
            // Si no encontró columna, crear una nueva
            if (!foundColumn) {
                columns.add(mutableListOf(block))
            }
        }
        
        // Ordenar cada columna por posición Y (arriba a abajo)
        columns.forEach { column ->
            column.sortBy { it.boundingBox?.top ?: 0 }
        }
        
        Log.d("PriceAnalyzer", "Created ${columns.size} columns from ${textBlocks.size} blocks")
        
        return columns
    }
}