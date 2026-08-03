package org.hoi_polloi.kpdaemon;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.hoi_polloi.kpdaemon.databinding.ActivityMainBinding;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable statusCheck;
    private Process serverProcess = null;

    private boolean isRunning;

    private boolean stateLast = false;
    private long sinceLast = -1L;


    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);

        // copy assets to local folder
        copyAssetToStorage("kpclientd");
        File outFile = new File("/data/local/tmp/client.toml");
        if (outFile.exists()) {
            // if custom config exists, use that instead of the built-in
            copyAssetToStorage("/data/local/tmp/client.toml");
        } else {
            copyAssetToStorage("client.toml");
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets systemBars = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
            );
            binding.appBar.setPadding(0, systemBars.top, 0, 0);
            binding.buttons.setPadding(0, 0, 0, systemBars.bottom);
            return windowInsets;
        });

        binding.btnStart.setOnClickListener(v -> startServer());
        binding.btnStop.setOnClickListener(v -> stopServer());

        statusCheck = new Runnable() {
            @Override
            public void run() {
                boolean currState = checkDaemonStatus();
                if (currState != stateLast) {
                    stateLast = currState;
                    sinceLast = new Date().getTime()/1000;
                }
                if (sinceLast > 0) {
                    long span = new Date().getTime() / 1000 - sinceLast;
                    int hrs = (int) (span / 3600);
                    int mins = (int) (span % 3600) / 60;
                    binding.elapsed.setText(String.format(Locale.ENGLISH, "%d:%02d", hrs, mins));
                } else {
                    binding.elapsed.setText("");
                }
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statusCheck);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusCheck);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    private void startServer() {
        if (serverProcess != null) {
            return;
        }
        executor.execute(() -> {
            try {
                String binaryPath = new File(getFilesDir(), "kpclientd").getAbsolutePath();
                String configPath = new File(getFilesDir(), "client.toml").getAbsolutePath();
                Runtime.getRuntime().exec("chmod 755 " + binaryPath).waitFor();
                String cmd = binaryPath + " -c " + configPath + " >/data/local/tmp/kpclientd.log 2>&1";
                serverProcess = new ProcessBuilder("su", "-c", cmd).start();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Server started...", Toast.LENGTH_SHORT).show());
                isRunning = true;

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to start server!", Toast.LENGTH_SHORT).show());
                isRunning = false;
            }
        });
    }

    private void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            serverProcess = null;
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show();
        }
        isRunning = false;
    }

    private boolean checkDaemonStatus() {
        if (isRunning) {
            binding.textStatus.setText(R.string.daemon_on);
            binding.btnStart.setEnabled(false);
            binding.btnStop.setEnabled(true);
            return true;
        }
        binding.textStatus.setText(R.string.daemon_off);
        binding.btnStart.setEnabled(true);
        binding.btnStop.setEnabled(false);
        sinceLast = -1L;
        return false;
    }

    private void copyAssetToStorage(String name) {
        File fOut;
        if (name.startsWith("/")) {
            fOut = new File(name);
        } else {
            fOut = new File(getFilesDir(), name);
        }
        try (InputStream in = getAssets().open(name);
             OutputStream out = new FileOutputStream(fOut)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to prepare resources!", Toast.LENGTH_LONG).show();
        }
    }
}
