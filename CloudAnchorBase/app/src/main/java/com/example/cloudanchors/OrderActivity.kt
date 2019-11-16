package com.example.cloudanchors

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import androidx.core.app.ComponentActivity
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.view.View
import kotlinx.android.synthetic.main.activity_order.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody




class OrderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val extras = intent.extras
        val cloudAnchorID = extras?.getString("CloudAnchorId")
        val x = extras?.getString("cameraPosition_x")
        val y = extras?.getString("cameraPosition_y")
        val z = extras?.getString("cameraPosition_z")
        val timestamp = StorageManager().getTime()


        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order)
        text_wurst.setVisibility(View.INVISIBLE)
        text_beer.setVisibility(View.INVISIBLE)
        btn_wurst.setOnClickListener{
            val url = "https://api.myjson.com/bins/x1ryy"
            var request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val JSON = MediaType.parse("application/json; charset=utf-8")

            var body = RequestBody.create(JSON, "{\"cloudAnchorId\":\"$cloudAnchorID\",\"orderItem\":\"wurst\",\"location\":{\"x\":\"$x\",\"y\":\"$y\",\"z\":\"$z\"},\"timestamp\":\"$timestamp\",\"userID\":\"666\"}")
            request = Request.Builder()
                .url(url)
                .put(body) // here we use put
                .build();
            client.newCall(request).execute()
            text_wurst.setVisibility(View.VISIBLE)
        }
        btn_beer.setOnClickListener{
            val url = "https://api.myjson.com/bins/x1ryy"
            var request = Request.Builder().url(url).build()
            val client = OkHttpClient()
            val JSON = MediaType.parse("application/json; charset=utf-8")

            var body = RequestBody.create(JSON, "{\"cloudAnchorId\":\"$cloudAnchorID\",\"orderItem\":\"beer\",\"location\":{\"x\":\"$x\",\"y\":\"$y\",\"z\":\"$z\"},\"timestamp\":\"$timestamp\",\"userID\":\"666\"}")
            request = Request.Builder()
                .url(url)
                .put(body) // here we use put
                .build();
            client.newCall(request).execute()
            text_beer.setVisibility(View.VISIBLE)

        }
    }
    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

}
