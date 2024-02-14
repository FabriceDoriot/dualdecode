package fr.bytel.dualdecode;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;

public class DualActivity extends Activity {
    private AdReplacer dualDecodeTest;

    static DualActivity instance;
    public static Context getAppContext() {
        return instance.getApplicationContext();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tv = findViewById(R.id.timer_text);
        tv.setText("press OK to start");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && (dualDecodeTest == null || !dualDecodeTest.testStarted)) {
            TextView tv = findViewById(R.id.timer_text);
            tv.setText("");
            dualDecodeTest = new AdReplacer(findViewById(R.id.ad_surface_container));
            return true;
        }
        return false;
    }

}