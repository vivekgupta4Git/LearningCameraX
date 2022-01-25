package com.vivek.learningproject.camerax

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.Image
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toRect
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback2{

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
    }


    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        //using camera provider class, used to bind the lifecycle of cameras to the lifecycle owner.
        //this eliminates the task of opening and closing the camera since CameraX is lifecycle aweare.


        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        cameraProviderFuture.addListener(Runnable {





        },ContextCompat.getMainExecutor(this))

        cameraProviderFuture.addListener(Runnable {
        val bindLife = cameraProviderFuture.get()

            //preview object
            val preview = Preview.Builder()
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
            Log.i(TAG,"RectF : ${rectF.height()} ${rectF.width()}")
            val rational = Rational(widthOfCroppedImage,heightOfCroppedImage)
            Log.i(TAG,"Rational : $rational")
            Log.i(TAG,"Rotation : ${display?.rotation}")

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

      /*  val displayMetrics = DisplayMetrics()
       val defaultDisplay= getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        defaultDisplay?.getMetrics(displayMetrics)
*/
        val height = previewView.height
        val width = previewView.width


        val canvas = mHolder.lockCanvas()


        val left = width/4f
        val top = height/4f
        val right = width*3f/4f
        val bottom = height/2f
        Log.i(TAG,"in OnDraw, Surface View height x widht = $height x $width")
        Log.i(TAG,"RectF, in OnDraw  Height X width =(Bottom-Top)x(Right-Left) : $bottom - $top x  $right - $left")
         rectF = RectF(left, top, right, bottom)
        val paint = Paint().apply {
            Paint.ANTI_ALIAS_FLAG
            style=Paint.Style.STROKE
            color = Color.RED
            strokeWidth=5f
        }

        canvas.drawRect(rectF,paint)
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

object ScreenSizeCompat {
    private val api: Api =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ApiLevel30()
        else Api()

    /**
     * Returns screen size in pixels.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun getScreenSize(context: Context): Size = api.getScreenSize(context)

    @Suppress("DEPRECATION")
    private open class Api {
        @RequiresApi(Build.VERSION_CODES.M)
        open fun getScreenSize(context: Context): Size {
            val display = context.getSystemService(WindowManager::class.java).defaultDisplay
            val metrics = if (display != null) {
                DisplayMetrics().also { display.getRealMetrics(it) }
            } else {
                Resources.getSystem().displayMetrics
            }
            return Size(metrics.widthPixels, metrics.heightPixels)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private class ApiLevel30 : Api() {
        override fun getScreenSize(context: Context): Size {
            val metrics: WindowMetrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
            return Size(metrics.bounds.width(), metrics.bounds.height())
        }
    }
}