package com.voicelib.vox;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MediaControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        VoxAccessibilityService service = VoxAccessibilityService.getInstance();
        if (service != null && intent.getAction() != null) {
            service.handleMediaAction(intent.getAction());
        }
    }
}
