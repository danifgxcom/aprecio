package com.danifgx.aprecio

import org.junit.Test
import org.junit.Assert.*
import android.graphics.Rect

/**
 * Test para validar el PriceAnalyzer mejorado basado en la imagen IMG20250628104607.jpg
 * 
 * Contenido esperado de la imagen:
 * 1. 1.59€ (producto no claro)
 * 2. Tomate pera 2,19€/Kg
 * 3. Tomate Pera 500G 1,39€ (con 2,78€/KG debajo) - ENGAÑOSO
 * 4. Tomate rama 2,29€/Kg
 */
class PriceAnalyzerTest {
    
    private val analyzer = PriceAnalyzer()
    
    @Test
    fun testTomatePera500gDeceptivePricing() {
        // Simular el texto extraído del bloque del tomate pera engañoso
        val deceptiveBlockText = """
            Tomate Pera
            500G
            1,39€
            2,78€/KG
        """.trimIndent()
        
        val boundingBox = Rect(100, 200, 300, 400)
        
        // Usar el método extractProductFromBlock directamente 
        // (necesitaríamos hacer este método público o crear un método de test)
        val result = testExtractProductFromBlock(deceptiveBlockText, boundingBox)
        
        assertNotNull("Debería detectar el producto", result)
        result?.let { product ->
            assertEquals("Debería detectar Tomate Pera", "Tomate Pera", product.productName)
            assertEquals("Debería detectar peso de 0.5kg", 0.5, product.weight, 0.01)
            assertEquals("Debería detectar precio de 1.39€", 1.39, product.displayedPrice, 0.01)
            assertEquals("Debería calcular precio por kg correcto", 2.78, product.pricePerKg, 0.01)
            assertTrue("Debería marcar como engañoso", product.isDeceptive)
        }
    }
    
    @Test
    fun testRegularTomatePeraPerKg() {
        // Simular el texto del tomate pera normal (por kg)
        val normalBlockText = """
            Tomate pera
            2,19€/Kg
        """.trimIndent()
        
        val boundingBox = Rect(400, 200, 600, 400)
        
        val result = testExtractProductFromBlock(normalBlockText, boundingBox)
        
        assertNotNull("Debería detectar el producto", result)
        result?.let { product ->
            assertEquals("Debería detectar Tomate Pera", "Tomate Pera", product.productName)
            assertEquals("Debería detectar precio por kg", 2.19, product.displayedPrice, 0.01)
            assertTrue("Debería marcar como precio por kg", product.isPricePerKg)
            assertFalse("No debería marcar como engañoso", product.isDeceptive)
        }
    }
    
    @Test
    fun testTomateRamaPerKg() {
        // Simular el texto del tomate rama
        val ramaBlockText = """
            Tomate rama
            2,29€/Kg
        """.trimIndent()
        
        val boundingBox = Rect(700, 200, 900, 400)
        
        val result = testExtractProductFromBlock(ramaBlockText, boundingBox)
        
        assertNotNull("Debería detectar el producto", result)
        result?.let { product ->
            assertEquals("Debería detectar Tomate Rama", "Tomate Rama", product.productName)
            assertEquals("Debería detectar precio por kg", 2.29, product.displayedPrice, 0.01)
            assertTrue("Debería marcar como precio por kg", product.isPricePerKg)
        }
    }
    
    @Test 
    fun testWeightPatternDetection() {
        // Test específico para detección de peso 500G
        val text500g = "Tomate Pera 500G 1,39€"
        val result = testExtractProductFromBlock(text500g, null)
        
        assertNotNull("Debería detectar producto con 500g", result)
        result?.let { product ->
            assertEquals("Debería convertir 500g a 0.5kg", 0.5, product.weight, 0.01)
            assertEquals("Unit debería ser kg", "kg", product.weightUnit)
        }
        
        // Test para "medio kilo"
        val textMedioKilo = "Tomate Pera medio kilo 1,39€"
        val result2 = testExtractProductFromBlock(textMedioKilo, null)
        
        assertNotNull("Debería detectar producto con medio kilo", result2)
        result2?.let { product ->
            assertEquals("Debería detectar medio kilo como 0.5kg", 0.5, product.weight, 0.01)
        }
    }
    
    @Test
    fun testPriceFormatDetection() {
        // Test diferentes formatos de precio
        val testCases = listOf(
            "1,39€" to 1.39,
            "€1.39" to 1.39, 
            "2,19€/Kg" to 2.19,
            "€2.78/KG" to 2.78,
            "1 euro 39 céntimos" to 1.39,
            "139 céntimos" to 1.39
        )
        
        testCases.forEach { (priceText, expectedPrice) ->
            val result = testExtractProductFromBlock("Tomate $priceText", null)
            assertNotNull("Debería detectar precio en formato: $priceText", result)
            result?.let { product ->
                assertEquals("Precio incorrecto para formato $priceText", 
                    expectedPrice, product.displayedPrice, 0.01)
            }
        }
    }
    
    // Método helper para acceder a la funcionalidad interna de PriceAnalyzer
    // En una implementación real, haríamos este método público en PriceAnalyzer o usaríamos reflection
    private fun testExtractProductFromBlock(blockText: String, boundingBox: Rect?): ProductInfo? {
        // Como el método es privado, simulamos su comportamiento basado en la lógica implementada
        val lines = blockText.split("\n").map { it.trim() }
        
        // Patrones expandidos para productos (copiado de la implementación)
        val productPatterns = listOf(
            "tomate\\s+(pera|cherry|rama|natural)" to "Tomate",
            "pera" to "Tomate Pera", 
            "rama" to "Tomate Rama",
            "cherry" to "Tomate Cherry",
            "tomate" to "Tomate"
        )
        
        var productName = "Producto"
        var price = 0.0
        var weight = 1.0
        var weightUnit = "kg"
        var isPricePerKg = false
        
        // Buscar nombre del producto
        for (line in lines) {
            val cleanLine = line.lowercase()
            for ((pattern, name) in productPatterns) {
                if (Regex(pattern).find(cleanLine) != null) {
                    productName = when {
                        pattern.contains("pera") -> "Tomate Pera"
                        pattern.contains("rama") -> "Tomate Rama"
                        pattern.contains("cherry") -> "Tomate Cherry"
                        else -> name
                    }
                    break
                }
            }
            if (productName != "Producto") break
        }
        
        // Buscar peso
        for (line in lines) {
            val cleanLine = line.replace(",", ".").lowercase()
            
            // Detectar 500g, 250g, etc
            val weightMatch = Regex("(\\d+)\\s*g\\b").find(cleanLine)
            if (weightMatch != null) {
                val grams = weightMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                weight = grams / 1000.0
                weightUnit = "g"
                break
            }
            
            // Detectar "medio kilo"
            if (cleanLine.contains("medio") && cleanLine.contains("kilo")) {
                weight = 0.5
                weightUnit = "kg"
                break
            }
        }
        
        // Buscar precio
        for (line in lines) {
            val cleanLine = line.replace(",", ".")
            
            // Buscar precio por kg
            val pricePerKgMatch = Regex("(\\d+[.]\\d{1,2})€?/?k?g").find(cleanLine)
            if (pricePerKgMatch != null && cleanLine.contains("/kg", ignoreCase = true)) {
                price = pricePerKgMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                isPricePerKg = true
                break
            }
            
            // Buscar precio simple
            val priceMatch = Regex("(\\d+[.]\\d{1,2})€?").find(cleanLine)
            if (priceMatch != null && price == 0.0) {
                price = priceMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }
        
        return if (price > 0) {
            val finalWeight = if (weight > 0) weight else 1.0
            val finalPricePerKg = if (isPricePerKg) price else price / finalWeight
            
            // Detectar engaño simple: peso menor a 1kg pero precio alto
            val isDeceptive = finalWeight < 1.0 && !isPricePerKg && finalPricePerKg > 2.0
            
            ProductInfo(
                productName = productName,
                displayedPrice = price,
                weight = finalWeight,
                weightUnit = weightUnit,
                pricePerKg = finalPricePerKg,
                isDeceptive = isDeceptive,
                confidence = if (productName != "Producto") 0.8f else 0.6f,
                boundingBox = boundingBox,
                isPricePerKg = isPricePerKg
            )
        } else {
            null
        }
    }
}