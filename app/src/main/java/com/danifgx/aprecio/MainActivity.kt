package com.danifgx.aprecio

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.danifgx.aprecio.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var priceAnalyzer: PriceAnalyzer
    private lateinit var overlayManager: OverlayManager
    private lateinit var productsAdapter: ProductsAdapter
    
    private var imageCapture: ImageCapture? = null
    private var currentBitmap: Bitmap? = null
    private var currentProducts: List<ProductInfo> = emptyList()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Permiso de cámara requerido", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImageFromGallery(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        priceAnalyzer = PriceAnalyzer()
        cameraExecutor = Executors.newSingleThreadExecutor()
        overlayManager = OverlayManager(this, binding.overlayContainer)
        
        setupUI()
        setupRecyclerView()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }
    
    private fun setupUI() {
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        
        binding.galleryButton.setOnClickListener {
            selectFromGallery()
        }
        
        binding.backToCameraButton.setOnClickListener {
            showCameraView()
        }
    }
    
    private fun setupRecyclerView() {
        productsAdapter = ProductsAdapter(currentProducts) { product, number ->
            showProductDetails(product, number)
        }
        
        binding.productsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = productsAdapter
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder().build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Error iniciando cámara", exc)
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Error capturando foto: ${exception.message}", exception)
                    Toast.makeText(this@MainActivity, "Error capturando foto", Toast.LENGTH_SHORT).show()
                }
                
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        processImageFromUri(uri)
                    }
                }
            }
        )
    }
    
    private fun selectFromGallery() {
        pickImageLauncher.launch("image/*")
    }
    
    private fun processImageFromGallery(uri: Uri) {
        processImageFromUri(uri)
    }
    
    private fun processImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                analyzeImage(bitmap)
            } else {
                Toast.makeText(this, "Error procesando imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error leyendo imagen", e)
            Toast.makeText(this, "Error leyendo imagen", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun analyzeImage(bitmap: Bitmap) {
        // Optimizar el bitmap para reducir memoria
        val optimizedBitmap = optimizeBitmap(bitmap)
        currentBitmap = optimizedBitmap
        showAnalysisView(optimizedBitmap)
        
        priceAnalyzer.analyzeImage(optimizedBitmap) { result ->
            runOnUiThread {
                if (result != null && result.products.isNotEmpty()) {
                    displayAnalysisResults(result)
                } else {
                    Toast.makeText(this, "No se pudieron detectar productos en la imagen", Toast.LENGTH_SHORT).show()
                    showCameraView()
                }
            }
        }
    }
    
    private fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        val maxWidth = 1920
        val maxHeight = 1080
        
        return if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
            val ratio = minOf(
                maxWidth.toFloat() / bitmap.width,
                maxHeight.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * ratio).toInt()
            val newHeight = (bitmap.height * ratio).toInt()
            
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                if (it != bitmap) bitmap.recycle() // Liberar memoria del bitmap original
            }
        } else {
            bitmap
        }
    }
    
    private fun displayAnalysisResults(result: PriceAnalysisResult) {
        currentProducts = result.products
        
        // Actualizar adapter
        productsAdapter.updateProducts(currentProducts)
        
        // Verificar si hay productos engañosos
        val hasDeceptiveProducts = currentProducts.any { it.isDeceptive }
        if (hasDeceptiveProducts) {
            showDeceptionAlert()
        } else {
            hideDeceptionAlert()
        }
        
        // Mostrar overlays si tenemos bitmap y coordenadas
        currentBitmap?.let { bitmap ->
            overlayManager.showProductOverlays(
                currentProducts,
                bitmap.width,
                bitmap.height
            ) { product, number ->
                showProductDetails(product, number)
            }
        }
        
        // Mostrar la lista de productos
        binding.resultsScrollView.visibility = View.VISIBLE
    }
    
    private fun showAnalysisView(bitmap: Bitmap) {
        // Ocultar cámara y mostrar imagen
        binding.viewFinder.visibility = View.GONE
        binding.analyzedImage.visibility = View.VISIBLE
        binding.analyzedImage.setImageBitmap(bitmap)
        
        // Mostrar botón de volver
        binding.backToCameraButton.visibility = View.VISIBLE
        binding.captureButton.visibility = View.GONE
        binding.galleryButton.visibility = View.GONE
    }
    
    private fun showCameraView() {
        // Mostrar cámara y ocultar imagen
        binding.viewFinder.visibility = View.VISIBLE
        binding.analyzedImage.visibility = View.GONE
        
        // Ocultar overlays y resultados
        overlayManager.hideOverlays()
        binding.resultsScrollView.visibility = View.GONE
        
        // Restaurar botones
        binding.backToCameraButton.visibility = View.GONE
        binding.captureButton.visibility = View.VISIBLE
        binding.galleryButton.visibility = View.VISIBLE
        
        // Limpiar datos y liberar memoria
        currentBitmap?.recycle()
        currentBitmap = null
        currentProducts = emptyList()
        binding.analyzedImage.setImageBitmap(null)
        
        // Forzar garbage collection
        System.gc()
    }
    
    private fun showProductDetails(product: ProductInfo, number: Int) {
        val message = buildString {
            append("Producto #$number\n\n")
            append("Nombre: ${product.productName}\n")
            append("Precio: ${product.displayedPrice}€\n")
            append("Peso: ${product.weight} ${product.weightUnit}\n")
            append("Precio por kg: ${String.format("%.2f", product.pricePerKg)}€\n")
            append("Confianza: ${(product.confidence * 100).toInt()}%")
            
            if (product.isDeceptive) {
                append("\n\n⚠️ Posible precio engañoso")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Detalles del Producto")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showDeceptionAlert() {
        binding.deceptionAlert.visibility = View.VISIBLE
    }
    
    private fun hideDeceptionAlert() {
        binding.deceptionAlert.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        
        // Limpiar recursos
        currentBitmap?.recycle()
        currentBitmap = null
        overlayManager.hideOverlays()
    }
    
    companion object {
        private const val TAG = "MainActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}