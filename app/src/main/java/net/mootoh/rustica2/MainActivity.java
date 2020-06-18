package net.mootoh.rustica2;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.foursquare.android.nativeoauth.FoursquareOAuth;
import com.foursquare.android.nativeoauth.model.AccessTokenResponse;
import com.foursquare.android.nativeoauth.model.AuthCodeResponse;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

class Venue {
    String id;
    String name;

    @Override
    public String toString() {
        return name;
    }
}

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Rustica";
    private static final int REQUEST_CODE_FSQ_CONNECT = 3;
    private static final int REQUEST_CODE_FSQ_TOKEN_EXCHANGE = 5;
    private static final String KEY_FOURSQUARE_AUTH_CODE = "KEY_FOURSQUARE_AUTH_CODE";
    private static final String KEY_FOURSQUARE_AUTH_TOKEN = "KEY_FOURSQUARE_AUTH_TOKEN";

    ArrayList<Venue> venues = new ArrayList<>();

    void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            int REQUEST_CODE_ASK_PERMISSIONS = 123;
            requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_ASK_PERMISSIONS);
            return;
        }
        requestLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionResult");
        requestLocation();
    }

    void requestLocation() {
        Log.d(TAG, "requestLocation");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            Log.e(TAG, "no no self permission");
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.getFusedLocationProviderClient(this)
                .getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        Log.d("Location", "changed to " + location);
                        lastLocation = location;
                        searchVenues();
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "failed in locating: " + e.getLocalizedMessage());
            }
        });
    }

    private String savedAuthToken() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        return sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupViews();
        checkAndRequestPermission();

        String authToken = savedAuthToken();
        if (authToken == null) {
            tryLoginWith4sq();
        } else {
            if (lastLocation == null) {
                checkAndRequestPermission();
                return;
            }
            searchVenues();
        }
    }

    private void setupViews() {
        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<Venue>(this, android.R.layout.simple_list_item_1, venues));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Venue venue = venues.get(position);
                checkin(venue);
            }
        });

        SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                requestLocation();
            }
        });
    }

    private void showToastOnUiThread(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkin(Venue venue) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final String accessToken = sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null);
        if (accessToken == null) {
            return;
        }

        OkHttpClient client = new OkHttpClient();

        String url = "https://api.foursquare.com/v2/checkins/add";
        url += "?oauth_token=" + accessToken;
        url += "&v=20151006";
        url += "&venueId=" + venue.id;

        Request request = new Request.Builder()
                .url(url)
                .method("POST", RequestBody.create(MediaType.parse("application/json"), ""))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                showToastOnUiThread("Failed in checkin: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.code() / 100 != 2) {
                    showToastOnUiThread("something bad happen: " + response.message());
                    return;
                }
                showToastOnUiThread("checked in!");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });
    }

    private void searchVenues() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        final String accessToken = sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null);
        if (accessToken != null) {
            start(accessToken);
        }
    }

    Location lastLocation;

    private void start(String accessToken) {
        searchNearbyVenues(accessToken);
    }

    private void exploreNearbyVenues(String accessToken) {
        if (lastLocation == null)
            return;
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.foursquare.com/v2/venues/explore";
        url += "?ll=" + lastLocation.getLatitude() + "," + lastLocation.getLongitude();
        url += "&oauth_token=" + accessToken;
        url += "&v=20151006";

        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            String query = getIntent().getStringExtra(SearchManager.QUERY);
            url += "&query=" + query;
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.e(TAG, "fail: " + e.getLocalizedMessage());
                stopRefreshing();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                stopRefreshing();
                String resBody = response.body().string();
                Log.d(TAG, "venues: " + resBody);
                try {
                    JSONObject top = new JSONObject(resBody);
                    JSONObject res = top.getJSONObject("response");
                    JSONArray groups = res.getJSONArray("groups");
                    JSONObject firstGroup = groups.getJSONObject(0);
                    JSONArray items = firstGroup.getJSONArray("items");

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject venueObject = item.getJSONObject("venue");
                        Venue venue = new Venue();
                        venue.id = venueObject.getString("id");
                        venue.name = venueObject.getString("name");
                        venues.add(venue);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ListView listView = (ListView) findViewById(R.id.list_view);
                            ArrayAdapter<String> aa = (ArrayAdapter<String>) listView.getAdapter();
                            aa.notifyDataSetChanged();
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void searchNearbyVenues(String accessToken) {
        if (lastLocation == null)
            return;
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.foursquare.com/v2/venues/search";
        url += "?ll=" + lastLocation.getLatitude() + "," + lastLocation.getLongitude();
        url += "&oauth_token=" + accessToken;
        url += "&v=20151006";

        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            String query = getIntent().getStringExtra(SearchManager.QUERY);
            url += "&query=" + query;
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.e(TAG, "fail: " + e.getLocalizedMessage());
                stopRefreshing();
            }

            @Override
            public void onResponse(Response response) throws IOException {
                stopRefreshing();
                String resBody = response.body().string();
                Log.d(TAG, "venues: " + resBody);
                try {
                    JSONObject top = new JSONObject(resBody);
                    JSONObject res = top.getJSONObject("response");
                    JSONArray vns = res.getJSONArray("venues");

                    for (int i = 0; i < vns.length(); i++) {
                        JSONObject venueObject = vns.getJSONObject(i);
                        Venue venue = new Venue();
                        venue.id = venueObject.getString("id");
                        venue.name = venueObject.getString("name");
                        venues.add(venue);
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ListView listView = (ListView) findViewById(R.id.list_view);
                            ArrayAdapter<String> aa = (ArrayAdapter<String>) listView.getAdapter();
                            aa.notifyDataSetChanged();
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void stopRefreshing() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final SwipeRefreshLayout swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
                swipeRefresh.setRefreshing(false);
            }
        });
    }
    private void tryLoginWith4sq() {
        Properties props = new Properties();
        Intent intent = FoursquareOAuth.getConnectIntent(getApplicationContext(), BuildConfig.foursquare_client_id);
        startActivityForResult(intent, REQUEST_CODE_FSQ_CONNECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = sp.edit();

        switch (requestCode) {
            case REQUEST_CODE_FSQ_CONNECT:
                AuthCodeResponse codeResponse = FoursquareOAuth.getAuthCodeFromResult(resultCode, data);
                Log.d(TAG, "CONNECT: got authCode " + codeResponse.getCode());
                edit.putString(KEY_FOURSQUARE_AUTH_CODE, codeResponse.getCode());
                edit.commit();

                getAccessToken(codeResponse.getCode());
                break;
            case REQUEST_CODE_FSQ_TOKEN_EXCHANGE:
                AccessTokenResponse tokenResponse = FoursquareOAuth.getTokenFromResult(resultCode, data);
                Log.d(TAG, "EXCHANGE: got accessToken " + tokenResponse.getAccessToken());
                if (tokenResponse.getException() != null) {
                    Log.e(TAG, "EXCHANGE: got exception = " + tokenResponse.getException() + ", cause = " + tokenResponse.getException().getCause());
                    return;
                }

                edit.putString(KEY_FOURSQUARE_AUTH_TOKEN, tokenResponse.getAccessToken());
                edit.commit();

                if (lastLocation == null) {
                    checkAndRequestPermission();
                    return;
                }
                searchVenues();
                break;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void getAccessToken(String authCode) {
        Intent intent = FoursquareOAuth.getTokenExchangeIntent(this, BuildConfig.foursquare_client_id, BuildConfig.foursquare_client_secret, authCode);
        startActivityForResult(intent, REQUEST_CODE_FSQ_TOKEN_EXCHANGE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        return true;
    }
}
