package com.example.apple.recognizeer;

import android.app.Application;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

/**
 * Created by lijing on 2018/12/8.
 */

public class RecognizerApp extends Application {

    private static RecognizerApp recognizerApp;
    private int needDeleteReference;

    @Override
    public void onCreate() {
        super.onCreate();
        recognizerApp = this;
    }

    public static RecognizerApp getInstance() {
        return recognizerApp;
    }

    public void deleteCookie() {

        int random = (int) (Math.random() * 3);
        if (needDeleteReference > random) {
            needDeleteReference = 0;
            CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(this);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
        }else{
            needDeleteReference++;
        }
    }
}
