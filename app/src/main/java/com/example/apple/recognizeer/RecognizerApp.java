package com.example.apple.recognizeer;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Stack;

/**
 * Created by lijing on 2018/12/8.
 */

public class RecognizerApp extends Application {

    private static RecognizerApp recognizerApp;
    private int needDeleteReference;
    private String currUseragent;
    private List<Activity> activityStack = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        recognizerApp = this;
        resetUserAgent();
    }

    public void onActivityCreate(Activity activity){
        activityStack.add(activity);
    }

    public void onActivityDestroy(Activity activity){
        activityStack.remove(activity);
    }

    public List<Activity> getActivityStack(){
        return activityStack;
    }

    public static RecognizerApp getInstance() {
        return recognizerApp;
    }

    /**
     * 清除cookie
     */
    public void deleteCookie() {

        int random = (int) (Math.random() * 3);
        if (needDeleteReference > random) {
            needDeleteReference = 0;
            CookieSyncManager cookieSyncMngr = CookieSyncManager.createInstance(this);
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();

            resetUserAgent();
        } else {
            needDeleteReference++;
        }

       }

    //获取本地IP函数
    public String getLocalIPAddress(){

        try{

            for (Enumeration<NetworkInterface> mEnumeration = NetworkInterface.getNetworkInterfaces(); mEnumeration.hasMoreElements(); ){

                NetworkInterface intf = mEnumeration.nextElement();

                for (Enumeration<InetAddress> enumIPAddr = intf.getInetAddresses(); enumIPAddr.hasMoreElements(); ) {

                    InetAddress inetAddress = enumIPAddr.nextElement();

                    //如果不是回环地址

                    if (!inetAddress.isLoopbackAddress()) {

                        //直接返回本地IP地址

                        return inetAddress.getHostAddress().toString();

                    }

                }

            }

        } catch (SocketException ex) {

            Log.e("Error", ex.toString());

        }

        return null;

    }
    /**
     * 获取ip地址
     * @return
     */
    public String getHostIP() {

        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;

    }


    public void resetUserAgent() {
        currUseragent = Constants.useragents[(int) (Math.random() * Constants.useragents.length)];
    }

    public String getCurrUseragent() {
        return currUseragent;
    }
}
