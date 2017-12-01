package cn.icheny.download;

import android.app.Application;

/**
 * Created by vincent on 2017/12/1.
 */

public class MyApplication extends Application {

    public static Application sApp;

    @Override
    public void onCreate() {
        super.onCreate();
        sApp = this;
    }
}
