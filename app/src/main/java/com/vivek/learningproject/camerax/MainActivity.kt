package com.vivek.learningproject.camerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import android.graphics.BitmapFactory

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.io.IOException


typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback2{

    private val HEIGHT_PERCENTAGE = 90
    private val WIDTH_PERCENTAGE = 75

    private var imageCapture: ImageCapture? = null
    private lateinit var mHolder : SurfaceHolder
    private lateinit var surfaceView : SurfaceView
    private lateinit var layout:ConstraintLayout
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var rectF : RectF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.constraintLayout)
        // Request camera permissions

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        surfaceView  = findViewById(R.id.overlay)
        surfaceView.setZOrderOnTop(true)
        mHolder = surfaceView.holder
        mHolder.setFormat(PixelFormat.TRANSPARENT)
        mHolder.addCallback(this)

    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken


/*
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })


*/

        //in memory buffer of the captured image
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),object :
            ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeOptInUsageError")
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                if(image.image?.format == ImageFormat.YUV_420_888)
                {
                  cropImageAndSaveImage(image.image!!)
                }
                image.close()

            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
            }


        })


    }



    fun Image.saveImage() {
        val yBuffer = planes[0].buffer // Y
        val vuBuffer = planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        val imageBytes = out.toByteArray()

        val bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()
            ).format(System.currentTimeMillis()) + ".jpg")

        if (bitmapImage != null)
        {
            try {
                val out1 = FileOutputStream(photoFile)
                bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, out1)
            } catch (e: IOException) {
                Log.e(TAG, "Error : ${e.message}")
            }
        }
        else
        {
            Log.i(TAG,"Bitmap Image is null")
        }


    }

    private fun cropImageAndSaveImage(image: Image) {


        val yBuffer = image.planes[0].buffer // Y
        val vuBuffer = image.planes[2].buffer // VU

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()

        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)


        val croppedArray = ByteArray(rectF.width().toInt() * rectF.height().toInt())
        //what else could be imageWidth ?
        val imageWidth = rectF.width()
        var i = 0

        nv21.forEachIndexed { index, byte ->
            val x = index % imageWidth
            val y = index / imageWidth
            if (rectF.left.toInt() <= x && x < rectF.right.toInt() && rectF.top.toInt() <= y && y < rectF.bottom.toInt()) {
                croppedArray[i] = byte
                i++
            }

        }
        val bitmapImage = BitmapFactory.decodeByteArray(croppedArray, 0, croppedArray.size)
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()
            ).format(System.currentTimeMillis()) + ".jpg")

        if (bitmapImage != null)
        {
            try {
                val out = FileOutputStream(photoFile)
                bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, out)
            } catch (e: IOException) {
                Log.e(TAG, "Error : ${e.message}")
            }
    }
        else
        {
            Log.i(TAG,"Bitmap Image is null")
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        //using camera provider class, used to bind the lifecycle of cameras to the lifecycle owner.
        //this eliminates the task of opening and closing the camera since CameraX is lifecycle aweare.


        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)



      cameraProviderFuture.addListener(Runnable {

        val bindLife = cameraProviderFuture.get()

          val metrics = DisplayMetrics().also {
              previewView.display.getRealMetrics(it)
          }
          val screenAspectRatio = aspectRatio(metrics.widthPixels,metrics.heightPixels)

          val rotation = previewView.display.rotation
            //preview object
            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .setTargetAspectRatio(screenAspectRatio)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }



            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }


            //camera selector
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val widthOfCroppedImage =rectF.width().toInt()
            val heightOfCroppedImage = rectF.height().toInt()
            Log.d(TAG,"RectF : ${rectF.height()} ${rectF.width()}")

            val rational = Rational(widthOfCroppedImage,heightOfCroppedImage)
            Log.d(TAG,"Rational : $rational")
            Log.d(TAG,"Rotation : ${display?.rotation}")


            val viewPort =
               display?.let { ViewPort.Builder(rational, it.rotation).build() }



            val useCaseGroup = viewPort?.let {
                UseCaseGroup.Builder()
                    .addUseCase(preview)
                    //    .addUseCase(imageAnalyzer)
                    .addUseCase(imageCapture!!)
                    .setViewPort(it)
                    .build()
            }

            try
            {
                bindLife.unbindAll()
                if (useCaseGroup != null) {
                    bindLife.bindToLifecycle(
                        this,cameraSelector,useCaseGroup)
                }
             //   bindLife.bindToLifecycle(this,
              //  cameraSelector,preview,imageCapture,imageAnalyzer)

            }catch (e : Exception){
                Log.e(TAG,"Use case binding failed")
            }

        },ContextCompat.getMainExecutor(this))


    }


    private fun aspectRatio(width: Int, height : Int) :Int{
        val previewRatio = ln(max(width,height).toDouble()/ min(width.toDouble(),height.toDouble()))
        if(abs(previewRatio-ln(4.0/3.0)) <= abs(previewRatio-ln(16.0/9.0)))
        {
            return 4/3
        }
        return 16/9
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {

        if(requestCode== REQUEST_CODE_PERMISSIONS)
        {
            if(allPermissionsGranted())
                startCamera()
            else
            {
                Snackbar.make(layout,getString(R.string.denied_permission_text),Snackbar.LENGTH_LONG).setAction(
                    getString(R.string.permissoin_grant_button)
                ) {
                    ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                        REQUEST_CODE_PERMISSIONS)
                }.show()

            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    }

    override fun shouldShowRequestPermissionRationale(permission: String): Boolean {

        Snackbar.make(layout,getString(R.string.permission_rationale),Snackbar.LENGTH_LONG).show()

        return super.shouldShowRequestPermissionRationale(permission)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun surfaceCreated(p0: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        drawOverlayRectangle()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }

    override fun surfaceRedrawNeeded(p0: SurfaceHolder) {

    }

    private fun drawOverlayRectangle(){

        val height = previewView.height
        val width = previewView.width
        val canvas = mHolder.lockCanvas()

        canvas.drawPaint(Paint().apply { alpha=140 })

        val cornerRadius = 25f
        val offset = 100f

        val left = width/3f
        val  top = height/3f
        val right = width*3f/4f -offset
        val  bottom = height/2f - offset

        rectF = RectF(left, top, right, bottom)

        val paint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            style=Paint.Style.FILL
            color = Color.WHITE
        }

        canvas.drawRoundRect(rectF,cornerRadius,cornerRadius,paint)
        mHolder.unlockCanvasAndPost(canvas)
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

}
