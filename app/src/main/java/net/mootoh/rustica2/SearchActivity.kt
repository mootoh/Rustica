package net.mootoh.rustica2

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class SearchActivity : AppCompatActivity() {
    private var venues = ArrayList<Venue>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.content_main)

        // Get the intent, verify the action and get the query
        val intent = intent
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            //            doMySearch(query);
            Log.d("Search", "query=$query")
            updateListView(query)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("Search", "onNewIntent")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Log.d("Search", "handleIntent")
        if (Intent.ACTION_SEARCH == intent.action) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            //use the query to search your data somehow
        }
    }

    fun updateListView(query: String?) {
        val listView = findViewById<View>(R.id.list_view) as ListView
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, venues)
        listView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            Log.d("xxx", "clicked: $position")
            val venue = venues[position]
            //                checkin(venue);
        }
    } /*
    private void getNearbyVenues(String query, String accessToken) {
        if (lastLocation == null)
            return;
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.foursquare.com/v2/venues/explore";
        url += "?ll=" + lastLocation.getLatitude() + "," + lastLocation.getLongitude();
        url += "&oauth_token=" + accessToken;
        url += "&v=20151006";
        url += "&query=" query+

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
*/
}