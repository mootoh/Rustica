package net.mootoh.rustica;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.foursquare.android.nativeoauth.FoursquareOAuth;
import com.foursquare.android.nativeoauth.model.AccessTokenResponse;
import com.foursquare.android.nativeoauth.model.AuthCodeResponse;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
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

    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;

    void checkAndRequestPermission() {
        int hasPermission = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {
                            Manifest.permission.ACCESS_COARSE_LOCATION},
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
        LocationRequest req = new LocationRequest();
        req.setInterval(10000);
        req.setFastestInterval(5000);

        LocationServices.FusedLocationApi.requestLocationUpdates(gaClient, req, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d("Location", "changed to " + location);
                searchVenues();
            }
        });
    }

    private String savedAuthCode() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        return sp.getString(KEY_FOURSQUARE_AUTH_CODE, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String authCode = savedAuthCode();
        if (authCode == null) {
            tryLoginWith4sq();
        } else {
            connectToGoogleApi();
            setupListView();
        }
    }

    private void connectToGoogleApi() {
        gaClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "onConnected");
                        lastLocation = LocationServices.FusedLocationApi.getLastLocation(gaClient);
                        Log.d(TAG, "last location " + lastLocation);
                        if (lastLocation == null) {
                            checkAndRequestPermission();
                            return;
                        }
                        searchVenues();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "onConnectionSuspended");

                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.e(TAG, "onConnectionFailed");
                    }
                })
                .addApi(LocationServices.API)
                .build();
        gaClient.connect();
    }

    private void setupListView() {
        ListView listView = (ListView) findViewById(R.id.list_view);
        listView.setAdapter(new ArrayAdapter<Venue>(this, android.R.layout.simple_list_item_1, venues));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d("xxx", "clicked: " + position);
                Venue venue = venues.get(position);
                checkin(venue);
            }
        });
//        updateWithAuthCode();
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
                Log.e(TAG, "fail: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.code() / 100 != 2) {
                    Log.e("Rustica", "something bad happen: " + response.message());
                    return;
                }
                String resBody = response.body().string();
                Log.d(TAG, "checkin: " + resBody);
                /*
                try {
                    JSONObject top = new JSONObject(resBody);
                    JSONObject res = top.getJSONObject("response");
                    JSONArray groups = res.getJSONArray("groups");
                    JSONObject firstGroup = groups.getJSONObject(0);
                    JSONArray items = firstGroup.getJSONArray("items");

                    for (int i=0; i<items.length(); i++) {
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
*/
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

    GoogleApiClient gaClient;
    Location lastLocation;

    private void start(String accessToken) {
        getNearbyVenues(accessToken);
    }

    private void getNearbyVenues(String accessToken) {
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
            }

            @Override
            public void onResponse(Response response) throws IOException {
                String resBody = response.body().string();
                Log.d(TAG, "venues: " + resBody);
                try {
                    JSONObject top = new JSONObject(resBody);
                    JSONObject res = top.getJSONObject("response");
                    JSONArray groups = res.getJSONArray("groups");
                    JSONObject firstGroup = groups.getJSONObject(0);
                    JSONArray items = firstGroup.getJSONArray("items");

                    for (int i=0; i<items.length(); i++) {
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
                Log.d(TAG, "got authCode " + codeResponse.getCode());
                edit.putString(KEY_FOURSQUARE_AUTH_CODE, codeResponse.getCode());
                edit.commit();

//                updateWithAuthCode();
                getAccessToken(codeResponse.getCode());

                break;
            case REQUEST_CODE_FSQ_TOKEN_EXCHANGE:
                AccessTokenResponse tokenResponse = FoursquareOAuth.getTokenFromResult(resultCode, data);
                Log.d(TAG, "got accessToken " + tokenResponse.getAccessToken());
                if (tokenResponse.getException() != null) {
                    Log.e(TAG, "got exception = " + tokenResponse.getException());
                    return;
                }

                edit.putString(KEY_FOURSQUARE_AUTH_TOKEN, tokenResponse.getAccessToken());
                edit.commit();
                break;
            default:
                break;
        }

    }

    private void getAccessToken(String authCode) {
        Intent intent = FoursquareOAuth.getTokenExchangeIntent(this, BuildConfig.foursquare_client_id, BuildConfig.foursquare_client_secret, authCode);
        startActivityForResult(intent, REQUEST_CODE_FSQ_TOKEN_EXCHANGE);
    }

    private void tryLogin() {
        String url = "https://foursquare.com/oauth2/authenticate\n" +
                "?client_id=" + BuildConfig.foursquare_client_id + "\n" +
                "&response_type=code\n" +
                "&redirect_uri=YOUR_REGISTERED_REDIRECT_URI";

        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url(url)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                Log.d(TAG, "fail: " + e.getLocalizedMessage());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                Log.d(TAG, "success: " + response.body().string());
            }
        });
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

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d("Search", "onNewIntent");
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Log.d("Search", "handleIntent");
        /*
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            //use the query to search your data somehow
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            final String accessToken = sp.getString(KEY_FOURSQUARE_AUTH_TOKEN, null);

            getNearbyVenues(accessToken, query);
        }
        */
    }
}
