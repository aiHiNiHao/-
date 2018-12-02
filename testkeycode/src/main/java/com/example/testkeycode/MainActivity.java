package com.example.testkeycode;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText editText = findViewById(R.id.edittext);
        editText.requestFocus();
        editText.setSelection(2);

    }



    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.i("lijing", "keyCode == "+event);
        return super.dispatchKeyEvent(event);
    }


}
