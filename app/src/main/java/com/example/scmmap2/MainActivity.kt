package com.example.scmmap2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v7.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

import org.json.JSONObject

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.Dot
import com.google.maps.android.SphericalUtil
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity(), OnMapReadyCallback {


    private var line: String? = null
    private lateinit var mapFragment: SupportMapFragment
    internal lateinit var mMap: GoogleMap
    private var origin = LatLng(28.558840, 77.211609)
    private var dest = LatLng(28.643500,77.190040)
    private var lineOptions = PolylineOptions()
    private val dot: PatternItem = Dot()
    private val gap: PatternItem = Gap(10f)
    private val mPattern = Arrays.asList(dot,gap)!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapFragment = (supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?)!!
        mapFragment.getMapAsync(this)
        drawPolyLines()

        navigate.setOnClickListener {
            navigateToDestination()
        }


    }

    private fun navigateToDestination() {

        val intent = Intent(
            android.content.Intent.ACTION_VIEW,
            Uri.parse("http://maps.google.com/maps?saddr=${origin.latitude},${origin.longitude}&daddr=${dest.latitude},${dest.longitude}")
        )
        startActivity(intent)

    }

    private fun drawPolyLines() {

        // Checks, whether start and end locations are captured
        // Getting URL to the Google Directions API
        val url = getDirectionsUrl(origin, dest)
        Log.d("url", url + "")
        val downloadTask = DownloadTask()
        // Start downloading json data from Google Directions API
        downloadTask.execute(url)
    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = GoogleMap.MAP_TYPE_NORMAL

        googleMap.setMapStyle(MapStyleOptions(
            resources
            .getString(R.string.style_json)))

        googleMap.addMarker(MarkerOptions().position(dest).icon(BitmapDescriptorFactory.fromBitmap(
            createCustomMarker(this@MainActivity,R.drawable.photo))))

        val bearing  = calculateBearing(origin.latitude,origin.longitude,dest.latitude,dest.longitude)


        googleMap.addMarker(MarkerOptions().position(origin).rotation(bearing).icon(BitmapDescriptorFactory.fromBitmap(
            createCustomSourceMarker(this@MainActivity,R.drawable.ic_navigation))))


        val builder =  LatLngBounds.Builder()

        //the include method will calculate the min and max bound.
        builder.include(origin)
        builder.include(dest)

        val bounds = builder.build()

        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val padding = (width * 0.40).toInt()  // offset from edges of the map 10% of screen

        val cu = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding)

        googleMap.animateCamera(cu)


    }


    private inner class DownloadTask : AsyncTask<String, Void, String>() {

        override fun doInBackground(vararg url: String): String {

            var data = ""

            try {
                data = downloadUrl(url[0])
            } catch (e: Exception) {
                Log.d("Background Task", e.toString())
            }

            return data
        }

        override fun onPostExecute(result: String) {
            super.onPostExecute(result)

            val parserTask = ParserTask()

            parserTask.execute(result)

        }
    }


    /**
     * A class to parse the Google Places in JSON format
     */
    private inner class ParserTask : AsyncTask<String, Int, List<List<HashMap<String, String>>>>() {

        // Parsing the data in non-ui thread
        override fun doInBackground(vararg jsonData: String): List<List<HashMap<String, String>>>? {

            val jObject: JSONObject
            var routes: List<List<HashMap<String, String>>>? = null

            try {
                jObject = JSONObject(jsonData[0])
                val parser = DirectionsJSONParser()
                routes = parser.parse(jObject)

            } catch (e: Exception) {
                e.printStackTrace()
            }

            return routes
        }

        override fun onPostExecute(result: List<List<HashMap<String, String>>>) {

            Log.d("result", result.toString())
            var points: ArrayList<LatLng>?

            for (i in result.indices) {
                points = ArrayList()
                lineOptions = PolylineOptions()

                val path = result[i]

                for (j in path.indices) {
                    val point = path[j]

                    val lat = point["lat"]?.toDouble()!!
                    val lng = point["lng"]?.toDouble()!!


                    val position = LatLng(lat, lng)

                    points.add(position)
                }

                lineOptions.addAll(points)
                lineOptions.width(12f)
                lineOptions.pattern(mPattern)
                lineOptions.color(Color.parseColor("#888888"))
                lineOptions.geodesic(true)

            }

            // Drawing polyline in the Google Map for the i-th route
                mMap.addPolyline(lineOptions)
        }
    }

    private fun getDirectionsUrl(origin: LatLng, dest: LatLng): String {

        // Origin of route
        val strOrigin = "origin=" + origin.latitude + "," + origin.longitude

        // Destination of route
        val strDest = "destination=" + dest.latitude + "," + dest.longitude

        // Sensor enabled
        val sensor = "sensor=false"
        val mode = "mode=driving"
        // Building the parameters to the web service
        val parameters = "$strOrigin&$strDest&$sensor&$mode"

        // Output format
        val output = "json"

        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + getString(R.string.google_maps_key)
    }

    /**
     * A method to download json data from url
     */
    @Throws(IOException::class)
    private fun downloadUrl(strUrl: String): String {
        var data = ""
        var iStream: InputStream? = null
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(strUrl)

            urlConnection = url.openConnection() as HttpURLConnection

            urlConnection.connect()

            iStream = urlConnection.inputStream

            val br = BufferedReader(InputStreamReader(iStream))

            val sb = StringBuffer()

            while ({ line = br.readLine(); line }() != null) {
                sb.append(line)
            }

            data = sb.toString()

            br.close()
            Log.d("data", data)

        } catch (e: Exception) {
            Log.d("Exception", e.toString())
        } finally {
            iStream!!.close()
            urlConnection!!.disconnect()
        }
        return data
    }

 @SuppressLint("InflateParams")
 private fun createCustomMarker(context: Context, @DrawableRes resource : Int ) : Bitmap {

    val marker = LayoutInflater.from(context).inflate(R.layout.marker, null)

    val markerImage = marker.findViewById<CircleImageView>(R.id.circleimage)
    markerImage.setImageResource(resource)

    val displayMetrics =  DisplayMetrics()
    this@MainActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
    marker.layoutParams = ViewGroup.LayoutParams(36, 36)
    marker.measure(displayMetrics.widthPixels, displayMetrics.heightPixels)
    marker.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
    val bitmap = Bitmap.createBitmap(marker.measuredWidth, marker.measuredHeight, Bitmap.Config.ARGB_8888)
    val canvas =  Canvas(bitmap)
    marker.draw(canvas)

    return bitmap
}

    @SuppressLint("InflateParams")
    fun createCustomSourceMarker(context: Context, @DrawableRes resource : Int ) : Bitmap {

        val marker = LayoutInflater.from(context).inflate(R.layout.sourcemarker, null)

        val markerImage = marker.findViewById<ImageView>(R.id.icon)
        markerImage.setImageResource(resource)

        val displayMetrics =  DisplayMetrics()
        this@MainActivity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        marker.layoutParams = ViewGroup.LayoutParams(22, 22)
        marker.measure(displayMetrics.widthPixels, displayMetrics.heightPixels)
        marker.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        val bitmap = Bitmap.createBitmap(marker.measuredWidth, marker.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas =  Canvas(bitmap)
        marker.draw(canvas)

        return bitmap
    }

    private fun calculateBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val sourceLatLng = LatLng(lat1, lng1)
        val destinationLatLng = LatLng(lat2, lng2)
        return SphericalUtil.computeHeading(sourceLatLng, destinationLatLng).toFloat()
    }

}