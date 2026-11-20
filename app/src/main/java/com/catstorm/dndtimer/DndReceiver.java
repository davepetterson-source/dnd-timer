package com.catstorm.dndtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DndReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Dnd.onBoot(context);
        } else if (Dnd.ACTION_END.equals(intent.getAction())) {
            Dnd.stop(context);
        }
    }
}
