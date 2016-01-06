package me.sheikharaf.snuauthentication.fragments;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import me.sheikharaf.snuauthentication.MyApplication;
import me.sheikharaf.snuauthentication.R;
import me.sheikharaf.snuauthentication.services.MainService;

/**
 * This fragment implements the main screen(control center) of the app.
 */
public class ControlCenterFragment extends Fragment {
    private ValueAnimator animator = null;
    private TextView tvStatus;
    private TextView tv1; // Slide across to turn on/off (change off to on and the other way around)
    private Tracker mTracker;

    public ControlCenterFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //final Context contextThemeWrapper = new ContextThemeWrapper(getActivity(), R.style.SystemOnlineTheme);
        //LayoutInflater layoutInflater = inflater.cloneInContext(contextThemeWrapper);
        View v = inflater.inflate(R.layout.fragment_control_center, container, false);

        Switch mainSwitch = (Switch) v.findViewById(R.id.main_switch);
        tvStatus = (TextView) v.findViewById(R.id.tv_status);
        tv1 = (TextView) v.findViewById(R.id.tv1);
        tvStatus.setTypeface(Typeface.MONOSPACE);

        ((AppCompatActivity) getActivity())
                .getSupportActionBar()
                .setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ff6600")));

        if (isMyServiceRunning(MainService.class)) {
            mainSwitch.setChecked(true);
            preServiceInitiation();
        }

        mainSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    preServiceInitiation();
                    getActivity().startService(new Intent(getActivity(), MainService.class));
                } else {
                    preServiceTermination();
                    getActivity().stopService(new Intent(getActivity(), MainService.class));
                }
            }
        });

        TextView tvCredit = (TextView) v.findViewById(R.id.tvCredit);
        tvCredit.setText(Html.fromHtml(getString(R.string.credit)));
        tvCredit.setMovementMethod(LinkMovementMethod.getInstance());

        return v;
    }

    private ValueAnimator blink(TextView tv, ValueAnimator visibilityAnim, boolean toggle) {
        if (toggle) {
            visibilityAnim = ObjectAnimator.ofInt(tv, "Visibility", View.VISIBLE, View.GONE);
            visibilityAnim.setDuration(1800);
            visibilityAnim.setEvaluator(new ArgbEvaluator());
            visibilityAnim.setRepeatCount(ValueAnimator.INFINITE);
            visibilityAnim.start();
            return visibilityAnim;
        }
        else if (visibilityAnim != null && !toggle) {
            visibilityAnim.end();
            tv.setVisibility(View.VISIBLE);
        }
        return visibilityAnim;
    }

    private void preServiceTermination() {
        tvStatus.setTextColor(Color.parseColor("#ff6600"));
        tvStatus.setText("SYSTEM OFFLINE");
        animator = blink(tvStatus, animator, false);
        ((AppCompatActivity) getActivity())
                .getSupportActionBar()
                .setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ff6600")));
        tv1.setText("Slide across to turn on the");
    }

    private void preServiceInitiation() {
        tvStatus.setTextColor(Color.parseColor("#00FF00"));
        tvStatus.setText("SYSTEM ONLINE");
        animator = blink(tvStatus, animator, true);
        ((AppCompatActivity) getActivity())
                .getSupportActionBar()
                    .setBackgroundDrawable(new ColorDrawable(Color.parseColor("#005EF0")));
        tv1.setText("Slide across to turn off the");
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Obtain the shared Tracker instance.
        MyApplication application = (MyApplication) getActivity().getApplication();
        mTracker = application.getDefaultTracker();
        if(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("pref_advertising", true)) {
            mTracker.enableAdvertisingIdCollection(true);
        }
        mTracker.enableAutoActivityTracking(true);
        mTracker.setScreenName("ControlCenterFragment");
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    @Override
    public void onStop() {
        super.onStop();
        mTracker.enableAutoActivityTracking(false);
    }
}
