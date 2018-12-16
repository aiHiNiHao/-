package com.example.apple.recognizeer;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Display;
import android.view.WindowManager;

public class Utils {

    public static int[] getDisplay(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        return new int[]{defaultDisplay.getWidth(), defaultDisplay.getHeight()};
    }

    public static String getRandomString() {
        String[] strings = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        int len = (int) (Math.random() * 10);

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i< 5 + len; i++){
            stringBuilder.append(strings[(int) (Math.random()* strings.length)]);
        }

        return stringBuilder.toString();
    }
}
