package com.example.apple.recognizeer;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;

/**
 * Created by lijing on 2018/12/9.
 */

public class BaseActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecognizerApp.getInstance().onActivityCreate(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RecognizerApp.getInstance().onActivityDestroy(this);
    }
}
