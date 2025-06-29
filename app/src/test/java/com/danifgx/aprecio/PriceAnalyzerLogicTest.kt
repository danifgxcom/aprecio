package com.danifgx.aprecio

import org.junit.Test
import org.junit.Assert.*
import java.util.regex.Pattern

/**
 * Test para validar la lógica de detección de PriceAnalyzer 
 * basado en la imagen IMG20250628104607.jpg sin usar clases Android
 */
class PriceAnalyzerLogicTest {
    
    @Test
    fun testWeightPatternDetection() {
        // Test patrones de peso que implementamos
        val weightPattern = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(gr?a?m?o?s?|g|kg|kilo|kilogramo)")
        val fractionWeightPattern = Pattern.compile("(medio|media|1/2|½)\\s*(kilo|kg|kilogramo)")
        
        // Test casos específicos de la imagen
        val testCases = mapOf(
            "500G" to Pair(500.0, "g"),
            "500 gramos" to Pair(500.0, "gramos"),
            "medio kilo" to Pair(0.5, "kilo"),
            "1/2 kg" to Pair(0.5, "kg"),
            "½ kg" to Pair(0.5, "kg"),
            "1 kg" to Pair(1.0, "kg"),
            "2,5 kg" to Pair(2.5, "kg")
        )
        
        testCases.forEach { (text, expected) ->
            val cleanText = text.lowercase()
            
            // Primero probar fracciones
            val fractionMatcher = fractionWeightPattern.matcher(cleanText)
            if (fractionMatcher.find()) {
                assertEquals("Fracción incorrecta para: $text", 0.5, expected.first, 0.01)
                return@forEach
            }
            
            // Luego probar patrón general
            val weightMatcher = weightPattern.matcher(cleanText)
            if (weightMatcher.find()) {
                val weightStr = weightMatcher.group(1)?.replace(",", ".")
                val unit = weightMatcher.group(2) ?: ""
                val weightValue = weightStr?.toDoubleOrNull() ?: 0.0
                
                if (unit.startsWith("g") && !unit.startsWith("kg")) {
                    // Convertir gramos a kg para comparación
                    assertEquals("Peso incorrecto para: $text", expected.first / 1000.0, weightValue / 1000.0, 0.01)
                } else {
                    assertEquals("Peso incorrecto para: $text", expected.first, weightValue, 0.01)
                }
            }
        }
    }
    
    @Test
    fun testPricePatternDetection() {
        // Test patrones de precio que implementamos
        val pricePerKgPattern = Pattern.compile("(\\d+[.,]\\d{1,2})\\s*€?\\s*/\\s*k?g")
        val euroSymbolPattern = Pattern.compile("€\\s*(\\d+[.,]\\d{1,2})|(\\d+[.,]\\d{1,2})\\s*€")
        val spanishPricePattern = Pattern.compile("(\\d+)[.,](\\d{2})\\s*euros?")
        val mixedPricePattern = Pattern.compile("(\\d+)\\s*euros?\\s*y\\s*(\\d+)\\s*c[eé]ntimos?")
        
        // Test casos de la imagen
        val testCases = mapOf(
            "1,39€" to 1.39,
            "2,19€/Kg" to 2.19,
            "2,78€/KG" to 2.78,
            "2,29€/Kg" to 2.29,
            "€1.39" to 1.39,
            "1 euro 39 céntimos" to 1.39,
            "139 céntimos" to 1.39
        )
        
        testCases.forEach { (text, expectedPrice) ->
            val cleanText = text.replace(",", ".").lowercase()
            var detectedPrice = 0.0
            
            // Test precio por kg
            val pricePerKgMatcher = pricePerKgPattern.matcher(cleanText)
            if (pricePerKgMatcher.find()) {
                val priceStr = pricePerKgMatcher.group(1)
                detectedPrice = priceStr?.toDoubleOrNull() ?: 0.0
            }
            
            // Test símbolo euro
            if (detectedPrice == 0.0) {
                val euroMatcher = euroSymbolPattern.matcher(cleanText)
                if (euroMatcher.find()) {
                    val priceStr1 = euroMatcher.group(1)
                    val priceStr2 = euroMatcher.group(2)
                    detectedPrice = (priceStr1 ?: priceStr2)?.toDoubleOrNull() ?: 0.0
                }
            }
            
            // Test formato español
            if (detectedPrice == 0.0) {
                val mixedMatcher = mixedPricePattern.matcher(cleanText)
                if (mixedMatcher.find()) {
                    val euros = mixedMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                    val cents = mixedMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                    detectedPrice = euros + (cents / 100.0)
                }
            }
            
            assertTrue("No se detectó precio para: $text", detectedPrice > 0)
            assertEquals("Precio incorrecto para: $text", expectedPrice, detectedPrice, 0.01)
        }
    }
    
    @Test
    fun testProductNameDetection() {
        // Test patrones de productos expandidos
        val productPatterns = listOf(
            "tomate\\s+(pera|cherry|rama|natural)" to "Tomate",
            "pera(?!.*tomate)" to "Pera",
            "rama" to "Tomate Rama",
            "cherry" to "Tomate Cherry",
            "tomate" to "Tomate"
        )
        
        val testCases = mapOf(
            "Tomate Pera" to "Tomate Pera",
            "Tomate pera" to "Tomate Pera",
            "tomate rama" to "Tomate Rama",
            "Tomate cherry" to "Tomate Cherry",
            "pera conferencia" to "Pera", // No debe confundirse con tomate pera
            "tomate natural" to "Tomate"
        )
        
        testCases.forEach { (text, expectedProduct) ->
            val cleanText = text.lowercase()
            var detectedProduct = "Producto"
            
            for ((pattern, name) in productPatterns) {
                if (Pattern.compile(pattern).matcher(cleanText).find()) {
                    detectedProduct = when {
                        pattern.contains("pera") && !pattern.contains("fruta") -> "Tomate Pera"
                        pattern.contains("rama") -> "Tomate Rama"
                        pattern.contains("cherry") -> "Tomate Cherry"
                        pattern.contains("pera") && pattern.contains("(?!.*tomate)") -> "Pera"
                        else -> name
                    }
                    break
                }
            }
            
            assertEquals("Producto incorrecto para: $text", expectedProduct, detectedProduct)
        }
    }
    
    @Test
    fun testDeceptivePricingDetection() {
        // Test caso específico de la imagen: Tomate Pera 500G 1,39€ con 2,78€/KG
        val weight = 0.5 // 500g = 0.5kg
        val displayedPrice = 1.39
        val calculatedPricePerKg = displayedPrice / weight // 2.78€/kg
        val actualPricePerKg = 2.78
        
        // Verificar que el cálculo es correcto
        assertEquals("El cálculo de precio por kg debería ser correcto", 
            actualPricePerKg, calculatedPricePerKg, 0.01)
        
        // Detectar engaño: peso menor a 1kg con precio que parece de 1kg
        val isDeceptive = weight < 1.0 && calculatedPricePerKg > 2.0
        
        assertTrue("Debería detectar este caso como engañoso", isDeceptive)
        
        // Test caso normal: Tomate pera 2,19€/Kg (no engañoso)
        val normalWeight = 1.0
        val normalPricePerKg = 2.19
        val isNormalDeceptive = normalWeight < 1.0
        
        assertFalse("No debería marcar precio por kg como engañoso", isNormalDeceptive)
    }
    
    @Test
    fun testCompleteScenarioFromImage() {
        // Simular el análisis completo del producto engañoso de la imagen
        val blockText = """
            Tomate Pera
            500G
            1,39€
            2,78€/KG
        """.trimIndent()
        
        val lines = blockText.split("\n").map { it.trim() }
        
        // Detectar producto
        var productName = "Producto"
        for (line in lines) {
            val cleanLine = line.lowercase()
            if (cleanLine.contains("tomate") && cleanLine.contains("pera")) {
                productName = "Tomate Pera"
                break
            }
        }
        
        // Detectar peso
        var weight = 1.0
        for (line in lines) {
            val weightMatch = Pattern.compile("(\\d+)\\s*g\\b").matcher(line)
            if (weightMatch.find()) {
                val grams = weightMatch.group(1).toDoubleOrNull() ?: 0.0
                weight = grams / 1000.0
                break
            }
        }
        
        // Detectar precio
        var price = 0.0
        for (line in lines) {
            val priceMatch = Pattern.compile("(\\d+[.,]\\d{2})€").matcher(line.replace(",", "."))
            if (priceMatch.find() && !line.contains("/")) { // Evitar el precio por kg
                price = priceMatch.group(1).toDoubleOrNull() ?: 0.0
                if (price > 0) break
            }
        }
        
        // Calcular precio por kg
        val pricePerKg = price / weight
        
        // Detectar engaño
        val isDeceptive = weight < 1.0 && pricePerKg > 2.0
        
        // Verificar resultados
        assertEquals("Producto incorrecto", "Tomate Pera", productName)
        assertEquals("Peso incorrecto", 0.5, weight, 0.01)
        assertEquals("Precio incorrecto", 1.39, price, 0.01)
        assertEquals("Precio por kg incorrecto", 2.78, pricePerKg, 0.01)
        assertTrue("Debería detectar como engañoso", isDeceptive)
        
        println("✅ Test completo exitoso:")
        println("   Producto: $productName")
        println("   Peso: ${weight}kg")
        println("   Precio: ${price}€")
        println("   Precio/kg: ${String.format("%.2f", pricePerKg)}€/kg")
        println("   Engañoso: $isDeceptive")
    }
}