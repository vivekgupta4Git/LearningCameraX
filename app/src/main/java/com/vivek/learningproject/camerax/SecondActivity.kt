package com.vivek.learningproject.camerax

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_second.*


class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

       /* val intent1 = intent
        val bitmap = intent1.getParcelableExtra<Bitmap>("BitmapImage")
       */
        val bitmap = BitmapFactory.decodeStream(this.openFileInput("myImage"))
        imageView.setImageBitmap(bitmap)
    }
}