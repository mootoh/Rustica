package net.mootoh.rustica2

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.foursquare.android.nativeoauth.FoursquareOAuth
import com.google.android.gms.location.LocationServices
import com.squareup.okhttp.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*

class Venue {
    var id: String? = null
    var name: String? = null
    override fun toString(): String {
        return name!!
    }
}

class MainActivity : AppCompatActivity() {
    var venues = ArrayList<Venue>()
    fun checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val REQUEST_CODE_ASK_PERMISSIONS = 123
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_CODE_ASK_PERMISSIONS
            )
            return
        }
        requestLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionResult")
        requestLocation()
    }

    fun requestLocation() {
        Log.d(TAG, "requestLocation")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            Log.e(TAG, "no no self permission")
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener { location ->
                Log.d("Location", "changed to $location")
                lastLocation = location
                searchVenues()
            }.addOnFailureListener { e -> Log.e(TAG, "failed in locating: " + e.localizedMessage) }
    }

    private fun savedAuthToken(): String? {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        return sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        checkAndRequestPermission()
        val authToken = savedAuthToken()
        if (authToken == null) {
            tryLoginWith4sq()
        } else {
            if (lastLocation == null) {
                checkAndRequestPermission()
                return
            }
            searchVenues()
        }
    }

    private fun setupViews() {
        val listView = findViewById<View>(R.id.list_view) as ListView
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, venues)
        listView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            val venue = venues[position]
            checkin(venue)
        }
        val swipeRefresh = findViewById<View>(R.id.swipeRefresh) as SwipeRefreshLayout
        swipeRefresh.setOnRefreshListener { requestLocation() }
    }

    private fun showToastOnUiThread(message: String) {
        runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
    }

    private fun checkin(venue: Venue) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val accessToken = sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null) ?: return
        val client = OkHttpClient()
        var url = "https://api.foursquare.com/v2/checkins/add"
        url += "?oauth_token=$accessToken"
        url += "&v=20151006"
        url += "&venueId=" + venue.id
        val request = Request.Builder()
            .url(url)
            .method("POST", RequestBody.create(MediaType.parse("application/json"), ""))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request, e: IOException) {
                showToastOnUiThread("Failed in checkin: " + e.localizedMessage)
            }

            @Throws(IOException::class)
            override fun onResponse(response: Response) {
                if (response.code() / 100 != 2) {
                    showToastOnUiThread("something bad happen: " + response.message())
                    return
                }
                showToastOnUiThread("checked in!")
                runOnUiThread { finish() }
            }
        })
    }

    private fun searchVenues() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val accessToken = sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null)
        accessToken?.let { start(it) }
    }

    var lastLocation: Location? = null
    private fun start(accessToken: String) {
        searchNearbyVenues(accessToken)
    }

    private fun exploreNearbyVenues(accessToken: String) {
        if (lastLocation == null) return
        val client = OkHttpClient()
        var url = "https://api.foursquare.com/v2/venues/explore"
        url += "?ll=" + lastLocation!!.latitude + "," + lastLocation!!.longitude
        url += "&oauth_token=$accessToken"
        url += "&v=20151006"
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            url += "&query=$query"
        }
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request, e: IOException) {
                Log.e(TAG, "fail: " + e.localizedMessage)
                stopRefreshing()
            }

            @Throws(IOException::class)
            override fun onResponse(response: Response) {
                stopRefreshing()
                val resBody = response.body().string()
                Log.d(TAG, "venues: $resBody")
                try {
                    val top = JSONObject(resBody)
                    val res = top.getJSONObject("response")
                    val groups = res.getJSONArray("groups")
                    val firstGroup = groups.getJSONObject(0)
                    val items = firstGroup.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val venueObject = item.getJSONObject("venue")
                        val venue = Venue()
                        venue.id = venueObject.getString("id")
                        venue.name = venueObject.getString("name")
                        venues.add(venue)
                    }
                    runOnUiThread {
                        val listView = findViewById<View>(R.id.list_view) as ListView
                        val aa = listView.adapter as ArrayAdapter<String>
                        aa.notifyDataSetChanged()
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun searchNearbyVenues(accessToken: String) {
        if (lastLocation == null) return
        val client = OkHttpClient()
        var url = "https://api.foursquare.com/v2/venues/search"
        url += "?ll=" + lastLocation!!.latitude + "," + lastLocation!!.longitude
        url += "&oauth_token=$accessToken"
        url += "&v=20151006"
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            url += "&query=$query"
        }
        val request = Request.Builder()
            .url(url)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(request: Request, e: IOException) {
                Log.e(TAG, "fail: " + e.localizedMessage)
                stopRefreshing()
            }

            @Throws(IOException::class)
            override fun onResponse(response: Response) {
                stopRefreshing()
                val resBody = response.body().string()
                Log.d(TAG, "venues: $resBody")
                try {
                    val top = JSONObject(resBody)
                    val res = top.getJSONObject("response")
                    val vns = res.getJSONArray("venues")
                    for (i in 0 until vns.length()) {
                        val venueObject = vns.getJSONObject(i)
                        val venue = Venue()
                        venue.id = venueObject.getString("id")
                        venue.name = venueObject.getString("name")
                        venues.add(venue)
                    }
                    runOnUiThread {
                        val listView = findViewById<View>(R.id.list_view) as ListView
                        val aa = listView.adapter as ArrayAdapter<String>
                        aa.notifyDataSetChanged()
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun stopRefreshing() {
        runOnUiThread {
            val swipeRefresh = findViewById<View>(R.id.swipeRefresh) as SwipeRefreshLayout
            swipeRefresh.isRefreshing = false
        }
    }

    private fun tryLoginWith4sq() {
        val props = Properties()
        val intent =
            FoursquareOAuth.getConnectIntent(applicationContext, BuildConfig.foursquare_client_id)
        startActivityForResult(intent, REQUEST_CODE_FSQ_CONNECT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val edit = sp.edit()
        when (requestCode) {
            REQUEST_CODE_FSQ_CONNECT -> {
                val codeResponse = FoursquareOAuth.getAuthCodeFromResult(resultCode, data)
                Log.d(TAG, "CONNECT: got authCode " + codeResponse.code)
                edit.putString(KEY_FOURSQUARE_AUTH_CODE, codeResponse.code)
                edit.apply()
                getAccessToken(codeResponse.code)
            }
            REQUEST_CODE_FSQ_TOKEN_EXCHANGE -> {
                val tokenResponse = FoursquareOAuth.getTokenFromResult(resultCode, data)
                Log.d(TAG, "EXCHANGE: got accessToken " + tokenResponse.accessToken)
                if (tokenResponse.exception != null) {
                    Log.e(
                        TAG,
                        "EXCHANGE: got exception = " + tokenResponse.exception + ", cause = " + tokenResponse.exception.cause
                    )
                    return
                }
                edit.putString(KEY_FOURSQUARE_AUTH_TOKEN, tokenResponse.accessToken)
                edit.commit()
                if (lastLocation == null) {
                    checkAndRequestPermission()
                    return
                }
                searchVenues()
            }
            else -> {
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getAccessToken(authCode: String) {
        val intent = FoursquareOAuth.getTokenExchangeIntent(
            this,
            BuildConfig.foursquare_client_id,
            BuildConfig.foursquare_client_secret,
            authCode
        )
        startActivityForResult(intent, REQUEST_CODE_FSQ_TOKEN_EXCHANGE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // Associate searchable configuration with the SearchView
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        return true
    }

    companion object {
        private const val TAG = "Rustica"
        private const val REQUEST_CODE_FSQ_CONNECT = 3
        private const val REQUEST_CODE_FSQ_TOKEN_EXCHANGE = 5
        private const val KEY_FOURSQUARE_AUTH_CODE = "KEY_FOURSQUARE_AUTH_CODE"
        private const val KEY_FOURSQUARE_AUTH_TOKEN = "KEY_FOURSQUARE_AUTH_TOKEN"
    }
}