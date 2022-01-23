package com.vivek.learningproject.camerax

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.ExecutorService
typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback2{
    private var imageCapture: ImageCapture? = null
    private lateinit var holder : SurfaceHolder
    private lateinit var surfaceView : SurfaceView
    private lateinit var layout:ConstraintLayout
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.constraintLayout)
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

        surfaceView  = findViewById(R.id.overlay)
        surfaceView.setZOrderOnTop(true)
        holder = surfaceView.holder
        holder.setFormat(PixelFormat.TRANSPARENT)
        holder.addCallback(this)

    }

    private fun takePhoto() {



    }

    private fun startCamera() {
        //using camera provider class, used to bind the lifecycle of cameras to the lifecycle owner.
        //this eliminates the task of opening and closing the camera since CameraX is lifecycle aweare.


        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
        val bindLife = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)

                }



        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try
            {
                bindLife.unbindAll()
                bindLife.bindToLifecycle(
                    this,cameraSelector,preview
                )
            }catch (e : Exception){
                Log.e(TAG,"Use case binding fialed")
            }

        },ContextCompat.getMainExecutor(this))


    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


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
        val displayMetrics = DisplayMetrics()
        val defaultDisplay = getSystemService<DisplayManager>()?.getDisplay(Display.DEFAULT_DISPLAY)
        defaultDisplay?.getMetrics(displayMetrics)

        val height = previewView.height
        val width = previewView.width

        val canvas = holder.lockCanvas()


        val left = height/2 + 10f
        val top = height/2 + 10f
        val right = width/2 + 10f
        val bottom = width/2 + 10f

        val rectF = RectF(left, top, right, bottom)

        val paint = Paint().apply {
            Paint.ANTI_ALIAS_FLAG
            style=Paint.Style.STROKE
            color = Color.RED
            strokeWidth=5f
        }

        canvas.drawRect(rectF,paint)
        holder.unlockCanvasAndPost(canvas)
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