package com.example.regresoacasa

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONException
import org.json.JSONObject
import androidx.core.content.ContextCompat

import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.RequiresApi


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val FROM_REQUEST_CODE = 1
    private val TAG = "MIKE"
    private lateinit var mMap: GoogleMap
    private lateinit var mfromLarlng: LatLng

    //MARCADORES
    private var MarkerfromLarlng: Marker? = null
    private var MarkertoLartLng: Marker? = null

    private val queue: RequestQueue? = null
    private var requestMapRequest: JsonObjectRequest? = null

    //COORDENADAS
    private lateinit var latLngFrom: LatLng
    private lateinit var latLngTo: LatLng

    //Polylines
    private var mMapRutas:ArrayList<Polyline>? = ArrayList<Polyline>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val locationPermissionRequest = registerForActivityResult(RequestMultiplePermissions(),
            ActivityResultCallback<Map<String?, Boolean?>> { result: Map<String?, Boolean?> ->
                val fineLocationGranted = result.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION, false
                )
                val coarseLocationGranted = result.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION, false
                )
                if (fineLocationGranted != null && fineLocationGranted) {
                    ultimaUbicacionCOnocida()
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    ultimaUbicacionCOnocida()
                } else {
                    Log.d("UBIX", "Sin permisos")
                }
            }
        )


        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
            &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            ultimaUbicacionCOnocida()
        }

        setupMaps()
        setupPlaces()
    }
    private fun setupPlaces(){
        Places.initialize(applicationContext, getString(R.string.apikeyMike))
        btnFrom.setOnClickListener{
            autoFrom()

        }
    }
    private fun setupMaps(){
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    private fun autoFrom(){
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .build(this)
        startActivityForResult(intent, FROM_REQUEST_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FROM_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let {
                        val place = Autocomplete.getPlaceFromIntent(data)
                        Log.i(TAG, "Place: ${place.name}, ${place.id}")
                        txtDireccion.text = "Dirección de casa: ${place.name}"
                        mfromLarlng = place.latLng
                        setMarkerTo(place.latLng )
                        obtenerRouteFromMap()

                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    // TODO: Handle the error.
                    data?.let {
                        val status = Autocomplete.getStatusFromIntent(data)
                        status.statusMessage?.let { it1 -> Log.i(TAG, it1) }
                    }
                }

            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onMapReady(p0: GoogleMap?) {
        if (p0 != null) {
            mMap = p0
        }
        mMap.setMinZoomPreference(14f)
        mMap.setMaxZoomPreference(50f)

    }
    private fun addMarker(latLng: LatLng, title: String): Marker? {
        val markerOptions = MarkerOptions()
            .position(latLng)
            .title(title)
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))

        return mMap.addMarker(markerOptions)
    }
    private fun setMarkerFrom(latLng: LatLng){
        MarkerfromLarlng?.remove()
        MarkerfromLarlng = addMarker(latLng, "Desde")
        latLngFrom = latLng

    }
    private fun setMarkerTo(latLng: LatLng){
        MarkertoLartLng?.remove()
        MarkertoLartLng = addMarker(latLng, "Casa")
        latLngTo = latLng
    }

    private fun drawRoute(latLng1: LatLng, latLng2: LatLng){
        val polyline1: Polyline = mMap.addPolyline(
            PolylineOptions()
                .clickable(true)
                .add(
                    latLng1,
                    latLng2
                )
        )
        mMapRutas?.add(polyline1)

    }
    private fun obtenerRouteFromMap(){
        clearArray()
        val queue = Volley.newRequestQueue(this)
        val url = "https://maps.googleapis.com/maps/api/directions/json?origin="+latLngFrom.latitude+","+latLngFrom.longitude+"&destination="+latLngTo.latitude+","+latLngTo.longitude+"&key=AIzaSyDOLK58XMDBOYPKebL3fUcF6rPG-BQlWto"
        Log.d("url", latLngFrom.latitude.toString() +","+latLngFrom.longitude.toString());
        Log.d("url", latLngTo.latitude.toString() +","+latLngTo.longitude.toString());
        setMarkerTo(latLngTo)
        setMarkerFrom(latLngFrom)
        clearArray()
        val requestMapRequest = JsonObjectRequest(
            url,
            { response ->
                try {
                    val obj: JSONObject = response
                    val array = obj.getJSONArray("routes")
                    val leg: JSONObject = array.getJSONObject(0)
                    val gg = leg.getJSONArray("legs")
                    val steps: JSONObject = gg.getJSONObject(0)
                    val fin = steps.getJSONArray("steps")
                    for (i in 0 until  fin.length()){
                        val a : JSONObject = fin.getJSONObject(i)
                        val s = a.getString("end_location")
                        val strFin = s.split(",")
                        val latF = strFin[0].substring(7, strFin[0].length)
                        val lngF = strFin[1].substring(6, strFin[1].length-1)

                        val s2 = a.getString("start_location")
                        val strIni = s2.split(",")
                        val latI = strIni[0].substring(7, strIni[0].length)
                        val lngI = strIni[1].substring(6, strIni[1].length-1)


                        val ini = LatLng(latI.toDouble(), lngI.toDouble())
                        val fin = LatLng(latF.toDouble(), lngF.toDouble())
                        drawRoute(ini, fin)


                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

            }
        ) { Log.d("GIVO", "se ejecuto CON ERROR") }

        queue.add(requestMapRequest)
    }

    private fun clearArray() {
        if (mMapRutas?.isEmpty() == false) {
            for (line in mMapRutas!!) {
                line.remove()
            }
            mMapRutas?.clear()
        }
    }
    private fun ultimaUbicacionCOnocida() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener(
            this
        ) { location: Location? ->
            if (location != null) {
                val ubica = ("Lat: " + location.latitude
                        + ", lon: " + location.longitude)
                Log.d("UBIX", ubica)
                setMarkerFrom(LatLng(location.latitude, location.longitude))

            } else {
                Log.d("UBIX", "Location null")
            }
        }
    }
}





