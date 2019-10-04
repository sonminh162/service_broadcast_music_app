package com.lifetime.push_music_app.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class EqualizerUtils {

    public static void notifyNoSessionId(@NonNull final Context context) {
        Toast.makeText(context, "Play a Song first", Toast.LENGTH_SHORT).show();
    }
}
