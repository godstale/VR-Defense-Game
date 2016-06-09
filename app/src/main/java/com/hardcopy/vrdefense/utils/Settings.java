package com.hardcopy.vrdefense.utils;

/**
 * Created by hardcopyworld.com on 2016-06-08.
 */
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public class Settings {
    private static final String PREFERENCE_NAME = "VRDefensePref";
    private static final String PREFERENCE_KEY_LAST_SCORE = "LastScore";
    private static final String PREFERENCE_KEY_TOP_SCORE = "TopScore";

    private static Settings mSettings = null;

    private Context mContext;
    private int mResultType = -1;



    public synchronized static Settings getInstance(Context c) {
        if(mSettings == null) {
            mSettings = new Settings(c);
        }
        return mSettings;
    }

    public Settings(Context c) {
        if(mContext == null) {
            mContext = c;
            initialize();
        }
    }


    private void initialize() {
    }


    public synchronized void finalize() {
        mContext = null;
        mSettings = null;
    }

    public synchronized void setLastScore(int score) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFERENCE_KEY_LAST_SCORE, score);
        editor.commit();
    }

    public synchronized int getLastScore() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREFERENCE_KEY_LAST_SCORE, 0);
    }

    public synchronized void setTopScore(int score) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREFERENCE_KEY_TOP_SCORE, score);
        editor.commit();
    }

    public synchronized int getTopScore() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(PREFERENCE_KEY_TOP_SCORE, 0);
    }
}
