package org.hoi_polloi.kpdaemon;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private Button btnStart;
    private Button btnStop;
    private TextView textTitle;
    private TextView textStatus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable statusCheckRunnable;
    private static final int CHECK_INTERVAL_MS = 10000; // 10 Sekunden

    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        textStatus = findViewById(R.id.textStatus);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Führt den Start-Befehl aus
                executeRootCommand("cd /data/apk/modules/kpclientd && ./kpclientd -c client.toml > /data/local/tmp/kpclientd.log 2>&1 &");
                checkDaemonStatus();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Beendet den Daemon-Prozess
                executeRootCommand("pkill -f kpclientd");
                checkDaemonStatus();
            }
        });

        statusCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkDaemonStatus();
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statusCheckRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusCheckRunnable);
    }

    /**
     * (1) Prüft den Status des Prozesses über eine Root-Shell.
     */
    private void checkDaemonStatus() {
        boolean isRunning = isProcessRunningRoot("kpclientd");

        if (isRunning) {
            textStatus.setText(R.string.daemon_on);
            btnStart.setEnabled(false);  // (2)
            btnStop.setEnabled(true);    // (2)
        } else {
            textStatus.setText(R.string.daemon_off);
            btnStart.setEnabled(true);   // (3)
            btnStop.setEnabled(false);  // (3)
        }
    }

    /**
     * Führt eine Prozessprüfung mit Root-Rechten durch.
     */
    private boolean isProcessRunningRoot(String processName) {
        boolean running = false;
        try {
            // Öffnet eine interaktive Root-Shell
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // ps -A listet alle Prozesse im System auf
            os.writeBytes("ps -A\n");
            os.writeBytes("exit\n");
            os.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(processName)) {
                    running = true;
                    break;
                }
            }

            reader.close();
            os.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return running;
    }

    /**
     * Universelle Hilfsmethode, um einen einzelnen Befehl als Root auszuführen.
     */
    private void executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            os.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
