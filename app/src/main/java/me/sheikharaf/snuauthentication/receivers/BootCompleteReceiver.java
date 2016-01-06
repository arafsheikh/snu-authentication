package me.sheikharaf.snuauthentication.receivers;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import me.sheikharaf.snuauthentication.services.MainService;

public class BootCompleteReceiver extends BroadcastReceiver{
    private static final String TAG = "BroadcastReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            if (context.getSharedPreferences("userdata.xml", Context.MODE_PRIVATE).getBoolean("service_running", false)) {
                Log.v(TAG, "BOOT_COMPLETE received. Starting MainService.");
                context.startService(new Intent(context, MainService.class));
            }
        }

    }
}
