// SPDX-License-Identifier: Apache-2.0
// Copyright 2025 XPerience Project

package mx.xperience.optimizer.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class OptimizerService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Lógica en segundo plano (si es necesaria)
        return START_NOT_STICKY;
    }
}