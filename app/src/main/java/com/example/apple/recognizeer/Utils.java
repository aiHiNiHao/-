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
        String[] strings = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
        int len = (int) (Math.random() * 10);

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i< 15 + len; i++){
            stringBuilder.append(strings[(int) (Math.random()* strings.length)]);
        }

        return stringBuilder.toString();
    }
}
