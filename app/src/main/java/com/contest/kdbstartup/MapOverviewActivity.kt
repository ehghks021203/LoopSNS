package com.contest.kdbstartup

import android.R.attr.text
import android.R.id.text2
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.contest.kdbstartup.network.ArticleMarkersResponse
import com.contest.kdbstartup.network.ArticleTimelineResponse
import com.contest.kdbstartup.network.KakaoResponse
import com.contest.kdbstartup.network.NetworkManager
import com.contest.kdbstartup.timeline.HotArticleSheetFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors


class MapOverviewActivity : AppCompatActivity() {

    lateinit var mapView: MapView
    lateinit var kakaoMap: KakaoMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    private val executor = Executors.newSingleThreadScheduledExecutor()

    private var currentLocationMarker: Label? = null

    private var markers: ArrayList<Label>? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_map_overview)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        mapView = findViewById<MapView>(R.id.kakaoMap)
        mapView.start(object : MapLifeCycleCallback() {
            override fun onMapDestroy() {
                // 지도 API 가 정상적으로 종료될 때 호출됨
            }

            override fun onMapError(error: Exception) {
                // 인증 실패 및 지도 사용 중 에러가 발생할 때 호출됨
            }
        }, object : KakaoMapReadyCallback() {
            override fun onMapReady(kakaoMap: KakaoMap) {
                this@MapOverviewActivity.kakaoMap = kakaoMap
                initGPS()
                initMap()
            }
        })
    }

    fun initGPS(){
        locationRequest = LocationRequest.Builder(5000)
            .setIntervalMillis(5000)
            .setMinUpdateIntervalMillis(1000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.let {
                    val lastLocation = locationResult.lastLocation
                    lastLocation?.let {
                        currentLocationMarker?.moveTo(LatLng.from(it.latitude, it.longitude))
                        return
                    }
                }

                Snackbar.make(findViewById(R.id.main), "현재 위치를 받아올 수 없습니다.", Snackbar.LENGTH_LONG).show();
                initMap()
            }
        }

        permissionCheck()
    }

    fun permissionCheck(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission is already granted, do something with the location
            getCurrentLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                getCurrentLocation()
                return
            }
        }
    }

    private fun getCurrentLocation() {
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                    Snackbar.make(findViewById(R.id.main), "정확한 위치 정보를 받아올 수 없어 정확성이 떨어집니다.", Snackbar.LENGTH_LONG).show()
                }

                //fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                fusedLocationClient.requestLocationUpdates(locationRequest, executor, locationCallback)

                fusedLocationClient.lastLocation.addOnSuccessListener { location : Location? ->
                    location?.let {
                        val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                            LatLng.from(it.latitude, it.longitude)
                        )


                        val styles = kakaoMap.labelManager!!.addLabelStyles(LabelStyles.from(LabelStyle.from(R.drawable.group_318)))
                        val layer = kakaoMap.labelManager!!.layer

                        val options = LabelOptions.from(LatLng.from(it.latitude, it.longitude)).setStyles(styles)

                        currentLocationMarker = layer!!.addLabel(options)

                        kakaoMap.moveCamera(cameraUpdate, CameraAnimation.from(500, true, true));
                        return@addOnSuccessListener
                    }

                    Snackbar.make(findViewById(R.id.main), "현재 위치를 받아올 수 없습니다.", Snackbar.LENGTH_LONG).show();
                }
            }
        } catch (e: SecurityException) {
            Log.e("GPS Error", "Security Exception: ${e.message}")
            Snackbar.make(findViewById(R.id.main), "현재 위치를 받아올 수 없습니다.", Snackbar.LENGTH_LONG).show();
            initMap()
        }
    }

    fun initMap(){
        kakaoMap.setOnCameraMoveEndListener { _, _, _ ->
            requestMaker()
        }

        kakaoMap.setOnLabelClickListener { _: KakaoMap, _: LabelLayer, label: Label ->
            Log.e("Click", "Click Invoked")
            val tag = label.tag ?: return@setOnLabelClickListener
            Log.e("Click", label.tag.toString())
            if(!(tag is List<*> && tag.all { it is String })) {
                return@setOnLabelClickListener
            }
            NetworkManager.apiService.retrieveArticleTimeline(tag as List<String>).enqueue(object : Callback<ArticleTimelineResponse> {
                override fun onResponse(call: Call<ArticleTimelineResponse>, response: Response<ArticleTimelineResponse>) {
                    if(!response.isSuccessful) {
                        Snackbar.make(findViewById(R.id.main), "타임라인 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
                        return
                    }

                    if(!response.body()!!.success) {
                        Log.e("Marker Request Error", response.body()!!.msg)
                        Snackbar.make(findViewById(R.id.main), "타임라인 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
                        return
                    }

                    val list = response.body()!!.articles

                    val bottomSheetFragment = HotArticleSheetFragment(list[0], label.position.latitude, label.position.longitude)
                    bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
//                    list.forEach {
//                        Log.e("Article", it.title)
//                    }
                }

                override fun onFailure(call: Call<ArticleTimelineResponse>, err: Throwable) {
                    Log.e("Marker Request Error", err.toString())
                    Snackbar.make(findViewById(R.id.main), "타임라인 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
                }

            })
        }
    }

    private fun requestMaker(){
        val rightTop = kakaoMap.fromScreenPoint(mapView.width, 0)!!    //제일 높음
        val leftBottom = kakaoMap.fromScreenPoint(0, mapView.height)!! //제일 낮음

        NetworkManager.apiService.retrieveArticleMarker(leftBottom.latitude, leftBottom.longitude, rightTop.latitude, rightTop.longitude).enqueue(object : Callback<ArticleMarkersResponse> {
            override fun onResponse(call: Call<ArticleMarkersResponse>, response: Response<ArticleMarkersResponse>) {
                if(!response.isSuccessful) {
                    Snackbar.make(findViewById(R.id.main), "마커 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
                    return
                }

                if(!response.body()!!.success) {
                    Log.e("Marker Request Error", response.body()!!.msg)
                    Snackbar.make(findViewById(R.id.main), "마커 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
                    return
                }

                val list = response.body()!!.markers

                val style = LabelStyle.from(R.drawable.location_without_shadow)
                val styles = kakaoMap.labelManager!!.addLabelStyles(LabelStyles.from(style))
                val layer = kakaoMap.labelManager!!.layer

                markers?.forEach {
                    it.remove()
                }
                //TODO 7월 9일 기존 마커 중 중복 마커는 제거 & 생성 방지
                //val tmp: ArrayList<Label>? = markers?.clone() as ArrayList<Label>?

                markers = ArrayList()

//                list.forEach {
//                    Log.e("VALUE 1", it.lat.toString() + " , " + it.lng.toString())
//                }
//                tmp?.forEach {
//                    Log.e("VALUE 2", it.position.latitude.toString() + " , " + it.position.longitude.toString())
//                }
                var count = 0

                list.forEach { item ->
                    val options = LabelOptions.from(LatLng.from(item.lat, item.lng)).setStyles(styles)

                    val marker = layer!!.addLabel(options)
                    marker.tag = item.articles
                    marker.isClickable = true

                    markers!!.add(marker)
                    count += item.articles.size
                }


                val currentPos = kakaoMap.cameraPosition!!.position!!

                val baseUrl = "https://dapi.kakao.com/v2/local/geo/coord2address"
                val url = baseUrl.toHttpUrlOrNull()?.newBuilder()?.apply {
                    addQueryParameter("x", currentPos.longitude.toString())
                    addQueryParameter("y", currentPos.latitude.toString())
                }?.build()

                // 요청 생성
                val request = Request.Builder()
                    .url(url!!)
                    .header("Authorization", "KakaoAK ecac7e74ea9e428b92e9edaa3786e729")
                    .build()

                // 요청 실행
                val client = OkHttpClient()

                var location = "알 수 없음"

                client.newCall(request).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Log.e("Kakao Local Error", e.toString())
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()?.trimIndent() ?: return

                            val gson = Gson()
                            val kakaoResponse = gson.fromJson(responseBody, KakaoResponse::class.java)

                            Log.e("DD", kakaoResponse.toString())
                            if(kakaoResponse.documents.isNotEmpty()) {
                                val address = kakaoResponse.documents[0].address

                                if(address != null) {
                                    if(kakaoMap.zoomLevel > 13) {
                                        location = address.region3depthName
                                    } else if (kakaoMap.zoomLevel > 10) {
                                        location = address.region2depthName
                                    } else if (kakaoMap.zoomLevel > 8){
                                        location = address.region1depthName
                                    } else {
                                        location = "대한민국"
                                    }
                                }
                            } else {
                                location = "알 수 없는 지역"
                            }
                           




                            val locationString = buildString {
                                append("지금 ")
                                append(location)
                                append(" 인근에서는\n")
                            }
                            val endString = " 개의 제보가 올라왔어요."

                            val spannable: Spannable = SpannableString(buildString {
                                append(locationString)
                                append(count)
                                append(endString)
                            })

                            spannable.setSpan(
                                ForegroundColorSpan(Color.rgb(0, 156, 255)), locationString.length,
                                (locationString.length + count.toString().length), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )

                            findViewById<TextView>(R.id.overview_text).text = spannable


                            val currentDateTime = LocalDateTime.now()
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            findViewById<TextView>(R.id.overview_time).text = buildString {
                                append(currentDateTime.format(formatter))
                                append(" 기준")
                            }








                        } else {
                            Log.e("Kakao Local Error", "Request failed: ${response.code}")
                        }
                    }
                })














            }

            override fun onFailure(call: Call<ArticleMarkersResponse>, err: Throwable) {
                Log.e("Marker Request Error", err.toString())
                Snackbar.make(findViewById(R.id.main), "마커 요청에 실패했습니다.", Snackbar.LENGTH_LONG).show();
            }
        })
    }
    public override fun onResume() {
        super.onResume()
        if (this::locationRequest.isInitialized && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, executor, locationCallback)
        }

        if(this::mapView.isInitialized) {
            mapView.resume() // MapView 의 resume 호출
        }
    }

    public override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        if(this::mapView.isInitialized) {
            mapView.pause() // MapView 의 pause 호출
        }
    }
}