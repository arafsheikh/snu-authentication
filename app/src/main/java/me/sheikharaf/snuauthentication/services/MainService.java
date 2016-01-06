package me.sheikharaf.snuauthentication.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import me.sheikharaf.snuauthentication.R;
import me.sheikharaf.snuauthentication.activities.MainActivity;

public class MainService extends Service {
    public static final String TAG = "MainService";
    private BroadcastReceiver receiver;
    private String username;
    private String password;
    private int retry_time = 5000;  // Retry after a failed login attempt
    public MainService() {
    }

    @Override
    public void onCreate() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                ConnectivityManager conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo netInfo = conMan.getActiveNetworkInfo();
                if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.d("WifiReceiver", "Have Wifi Connection");
                    SharedPreferences sp = getSharedPreferences("userdata.xml", Context.MODE_PRIVATE);
                    username = sp.getString("username", null);
                    password = sp.getString("password", null);
                    if (username != null && password != null)
                        new check204andLogin().execute();

                }
                else
                    Log.d("WifiReceiver", "Don't have Wifi Connection");
            }
        };

        registerReceiver(receiver, filter);
    }


    private class check204andLogin extends AsyncTask<Void, Void, Boolean> {

        /**
         *  This method will detect a walled garden.
         *  If walled garden is detected then log-out and log-in the user.
         *
         *  @return true: Walled garden detected
         *          false: Clear connection
         */
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                URL url = new URL("http://clients3.google.com/generate_204");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                    Log.i(TAG, "Got 204 response. No further action will be taken.");
                    return false;
                }
            } catch (MalformedURLException e) {
                Log.e(TAG, "204 URL malformed. " + e.toString());
            } catch (IOException e) {
                Log.e(TAG, "IOException. " + e.toString());
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean walledGarden) {
            if (walledGarden)
                logoutAndLogin(username, password);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started.");

        int number_of_logins = getSharedPreferences("userdata.xml", Context.MODE_PRIVATE).getInt("number_of_login", 0);
        showForegroundNotification("Number of login attempts saved: " + number_of_logins);

        SharedPreferences.Editor editor = getSharedPreferences("userdata.xml", Context.MODE_PRIVATE).edit();
        editor.putBoolean("service_running", true);
        editor.apply();

        loginEveryThreeHours(true);

        return START_STICKY;
    }

    /**
     * Logs the user in.
     */
    private void login(String username, String password) {
        try {
            post("191", username, password, new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    Log.e(TAG, "Login failed with " + e.toString());
                    onLoginFailure();
                }
                @Override
                public void onResponse(Response response) throws IOException {
                    Log.i(TAG, "Login: " + response.toString());
                    onLoginSuccess();
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "HTTP POST IOError: " + e.toString());
        }
    }

    /**
     * If login is successful then:
     * * Increment login-counter
     */
    private void onLoginSuccess() {
        SharedPreferences sp = getSharedPreferences("userdata.xml", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        int number_of_logins = sp.getInt("number_of_login", 0);
        editor.putInt("number_of_login", ++number_of_logins);
        editor.apply();
        showForegroundNotification("Number of login attempts saved: " + number_of_logins);
    }

    /**
     * If login failed retry 5 times over a period of 5.25 minutes.
     */
    private void onLoginFailure() {
        if (retry_time > 160) {
            retry_time = 0;
            return;
        }
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                logoutAndLogin(username, password);
            }
        }, retry_time);
        retry_time *= 2;    // Increase retry time for a better chance of success
    }

    /**
     * Logs the user out and then logs them back in.
     * Logging-out and then logging-in forces the server to start a new session.
     */
    private void logoutAndLogin(final String username, final String password) {
        try {
            post("193", username, password, new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    Log.e(TAG, "Logout failed with " + e.toString());
                    login(username, password);
                }
                @Override
                public void onResponse(Response response) throws IOException {
                    Log.i(TAG, "Logout: " + response.toString());
                    login(username, password);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "HTTP POST IOError: " + e.toString());
        }
    }

    /**
     * Send POST request to the server to log the user in or out.
     *
     * @param mode  Mode is used by the server script to differentiate between login and logout.
     *              191: Login
     *              193: Logout
     * @param username The username of user
     * @param password The password of user
     * @param callback The Callback
     * @return Call object
     * @throws IOException
     */
    Call post(String mode, String username, String password, Callback callback) throws IOException {
        OkHttpClient client = new OkHttpClient();
        RequestBody formBody = new FormEncodingBuilder()
                .add("mode", mode)
                .add("username", username + "@snu.in")
                .add("password", password)
                .build();
        Request request = new Request.Builder()
                .url("http://192.168.50.1/24online/servlet/E24onlineHTTPClient")
                .post(formBody)
                .build();
        Call call = client.newCall(request);
        call.enqueue(callback);
        return call;
    }

    /**
     * Start/stop handler to login every 3 hours.
     *
     * @param b if true, make handler and call it recursively every 3 hours.
     *          if false, stop the handler.
     */
    private void loginEveryThreeHours(boolean b) {
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                login(username, password);
                loginEveryThreeHours(true); // Is this a good idea?
            }
        }, 3*60*60*1000);

        if (!b)
            handler.removeCallbacksAndMessages(null);
    }

    private void showForegroundNotification(String contentText) {
        // Create intent that will bring our app to the front, as if it was tapped in the app
        // launcher

        int NOTIFICATION_ID = 100;

        Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
        showTaskIntent.setAction(Intent.ACTION_MAIN);
        showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                showTaskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_donut_large_white_36dp)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .getNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service stopped.");
        SharedPreferences.Editor editor = getSharedPreferences("userdata.xml", Context.MODE_PRIVATE).edit();
        editor.putBoolean("service_running", false);
        editor.apply();
        unregisterReceiver(receiver);
    }
}
