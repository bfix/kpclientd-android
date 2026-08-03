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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private boolean isRunning = false;

    private ActivityMainBinding binding;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable statusCheck;
    private Process serverProcess = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();


    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);

        copyAssetToStorage("kpclientd");
        copyAssetToStorage("client.toml");

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
                isRunning = checkDaemonStatus();
                handler.postDelayed(this, 10000);
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

    private void startServer() {
        if (serverProcess != null) {
            return;
        }
        executor.execute(() -> {
            try {
                String binaryPath = new File(getFilesDir(), "kpclientd").getAbsolutePath();
                String configPath = new File(getFilesDir(), "client.toml").getAbsolutePath();
                Runtime.getRuntime().exec("chmod 755 " + binaryPath).waitFor();
                serverProcess = new ProcessBuilder(binaryPath, "-c", configPath).start();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Server started...", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to start server!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            serverProcess = null;
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show();
        }
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
        return false;
    }

    private void copyAssetToStorage(String name) {
        File outFile = new File(getFilesDir(), name);
        if (outFile.exists()) {
            return;
        }
        try (InputStream in = getAssets().open(name);
             OutputStream out = new FileOutputStream(outFile)) {
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
