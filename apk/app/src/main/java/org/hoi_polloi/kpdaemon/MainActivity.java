package org.hoi_polloi.kpdaemon;

import android.os.Bundle;
import android.os.FileObserver;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
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
    private FileObserver watch;
    private String logPath;

    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);

        // copy assets to local folder
        copyAssetToStorage("kpclientd", "kpclientd");
        prepareConfig();
        logPath = new File(getFilesDir(), "kpclientd.log").getAbsolutePath();

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

        ImageView btn = binding.btnLog;
        btn.setOnClickListener(v -> {
            View detail = binding.log;
            if (detail.getVisibility() == View.INVISIBLE) {
                detail.setVisibility(View.VISIBLE);
                btn.setRotation(180f);
            } else {
                detail.setVisibility(View.INVISIBLE);
                btn.setRotation(0f);
            }
        });

        startWatch();

        sinceLast = new Date().getTime()/1000;
        statusCheck = new Runnable() {
            @Override
            public void run() {
                boolean currState = checkDaemonStatus();
                if (currState != stateLast) {
                    stateLast = currState;
                    sinceLast = new Date().getTime()/1000;
                }
                long span = new Date().getTime() / 1000 - sinceLast;
                int hrs = (int) (span / 3600);
                int mins = (int) (span % 3600) / 60;
                binding.elapsed.setText(String.format(Locale.ENGLISH, "%d:%02d", hrs, mins));
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
        prepareConfig();
        executor.execute(() -> {
            try {
                String binaryPath = new File(getFilesDir(), "kpclientd").getAbsolutePath();
                String configPath = new File(getFilesDir(), "client.toml").getAbsolutePath();
                Runtime.getRuntime().exec("chmod 755 " + binaryPath).waitFor();
                String cmd = binaryPath + " -c " + configPath + " >" + logPath + " 2>&1 &";
                serverProcess = new ProcessBuilder("su", "-c", cmd).start();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Server started...", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to start server!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void stopServer() {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill kpclientd"});
            proc.waitFor();
            if (proc.exitValue() != 0) {
                throw new Exception("can't kill kpclientd");
            }
            if (serverProcess != null) {
                serverProcess.destroy();
                serverProcess = null;
            }
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to stop server", Toast.LENGTH_SHORT).show();
        }
    }

    private void startWatch() {
        File log = new File(logPath);
        String[] body = new String[10];
        Arrays.fill(body, "");
        final int[] pos = {0};

        watch = new FileObserver(log.getParent(), FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(log.getName())) {
                    executor.execute(() -> {
                        String line = readLastLine(log);
                        if (line == null) line = "";

                        synchronized (body) {
                            body[pos[0]] = line;
                            pos[0] = (pos[0] + 1) % body.length;
                            StringBuilder show = new StringBuilder();
                            for (int i = 0; i < body.length; i++) {
                                int idx = (pos[0] + i) % body.length;
                                show.append(body[idx]).append("\n");
                            }
                            handler.post(() -> binding.log.setText(show.toString()));
                            binding.log.post(() -> {
                                android.text.Layout layout = binding.log.getLayout();
                                if (layout != null) {
                                    int height = layout.getLineTop(binding.log.getLineCount());
                                    int viewHeight = binding.log.getHeight() - binding.log.getPaddingTop() - binding.log.getPaddingBottom();
                                    int scroll = height - viewHeight;
                                    if (scroll > 0) {
                                        binding.log.scrollTo(0, scroll);
                                    } else {
                                        binding.log.scrollTo(0, 0);
                                    }
                                }
                            });
                        }
                    });
                }
            }
        };
        watch.startWatching();
    }

    private String readLastLine(File file) {
        if (!file.exists() || file.length() == 0) {
            return "";
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long ptr = file.length() - 1;
            StringBuilder line = new StringBuilder();
            while (ptr >= 0) {
                raf.seek(ptr);
                int c = raf.read();
                if (c == '\n' && line.length() > 0) {
                    return line.reverse().toString().trim();
                } else if (c != '\r') {
                    line.append((char) c);
                }
                ptr--;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "<log read failed>";
    }

    private boolean checkDaemonStatus() {
        // check the real state of the daemon
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "pgrep kpclientd"});
            proc.waitFor();
            InputStream in = proc.getInputStream();
            isRunning = (in.available() > 0);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // switch buttons if required
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

    private void prepareConfig() {
        File outFile = new File("/data/local/tmp/client.toml");
        boolean done = false;
        if (outFile.exists()) {
            // if custom config exists, use that instead of the built-in
            done = copyAssetToStorage("/data/local/tmp/client.toml", "client.toml");
        }
        if (!done) {
            copyAssetToStorage("client.toml", "client.toml");
        }
    }

    private boolean copyAssetToStorage(String src, String tgt) {
        InputStream in = null;
        try {
            if (src.startsWith("/")) {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + src});
                in = process.getInputStream();
            } else {
                in = getAssets().open(src);
            }
            File fOut = new File(getFilesDir(), tgt);
            try (OutputStream out = new FileOutputStream(fOut)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return fOut.length() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
    }
}
