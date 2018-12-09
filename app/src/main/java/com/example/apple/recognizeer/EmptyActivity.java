package com.example.apple.recognizeer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.WindowManager;

import java.util.List;

/**
 * Created by lijing on 2018/12/9.
 */

public class EmptyActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Activity> activityStack = RecognizerApp.getInstance().getActivityStack();
                Log.d("lijing", "--****************************"+activityStack.size());
                while (activityStack.size() > 1) {
                    for (int i = 1; i < activityStack.size(); i++) {
                        final Activity activity = activityStack.get(i);
                        if (activity.getClass() == MainActivity.class) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((MainActivity) activity).nip();
                                }
                            });

                        }
                        Log.d("lijing", "--onNewIntent: activitys == " + activity.getClass().getName());

                    }


                    SystemClock.sleep(50);
                }

                Log.d("lijing", "---------------------------"+activityStack.size());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        RecognizerApp.getInstance().resetUserAgent();

                        String localIPAddress = RecognizerApp.getInstance().getLocalIPAddress();
                        String hostIP = RecognizerApp.getInstance().getHostIP();
                        Log.d("lijing", "deleteCookie: IP == " + localIPAddress+"\n IP2 =="+hostIP);

                        startActivity(new Intent(EmptyActivity.this, MainActivity.class));
                    }
                });
            }
        }).start();


    }


}
