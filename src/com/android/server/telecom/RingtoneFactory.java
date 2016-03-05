/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.telecom;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Uses a Uri to obtain a {@link Ringtone} from the {@link RingtoneManager} that can be played
 * by the system during an incoming call.
 */
@VisibleForTesting
public class RingtoneFactory {

    private final Context mContext;

    public RingtoneFactory(Context context) {
        mContext = context;
    }

    public Ringtone getRingtone(Uri ringtoneUri) {
        UserHandle userHandle = UserHandle.of(ActivityManager.getCurrentUser());
        Context userContext = mContext;
        try {
            userContext = mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    userHandle);

        } catch (PackageManager.NameNotFoundException e) {
            Log.w("RingtoneFactory", "Package name not found: " + e.getMessage());
        }
        if (ringtoneUri == null) {
            String userRingtoneChoice = Settings.System.getStringForUser(
                    userContext.getContentResolver(), Settings.System.RINGTONE,
                    userHandle.getIdentifier());
            ringtoneUri = (userRingtoneChoice == null) ? null : Uri.parse(userRingtoneChoice);
        }

        Ringtone ringtone = RingtoneManager.getRingtone(userContext, ringtoneUri);
        if (ringtone != null) {
            ringtone.setStreamType(AudioManager.STREAM_RING);
        }
        return ringtone;
    }
}
