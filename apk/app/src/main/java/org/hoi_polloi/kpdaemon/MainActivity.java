//----------------------------------------------------------------------
// This file is part of 'kpclientd-android'.
// Copyright (C) 2026-present, Bernd Fix   >Y<
//
// 'kpclientd-android' is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License,
// or (at your option) any later version.
//
// 'kpclientd-android' is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// SPDX-License-Identifier: AGPL3.0-or-later
//----------------------------------------------------------------------

package org.hoi_polloi.kpdaemon;

import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.hoi_polloi.kpdaemon.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// MainActivity is the only activity in this app.
public class MainActivity extends AppCompatActivity {

    // binding to layout resource
    private ActivityMainBinding binding;

    // Attributes for running tasks in the background
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Runnable statusCheck;
    private Process serverProcess = null;
    private boolean isRunning;
    private boolean stateLast = false;
    private long sinceLast = -1L;

    // watch for changes in logfile
    private FileObserver watch;
    private String logPath;

    // (re-)create activity
    @Override
    protected void onCreate(Bundle BundleSavedInstanceState) {
        super.onCreate(BundleSavedInstanceState);

        // copy assets to local folder
        copyAssetToStorage("kpclientd", "kpclientd");
        prepareConfig();
        logPath = new File(getFilesDir(), "kpclientd.log").getAbsolutePath();

        // get layout binding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // switch off ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // handle margins at top and bottom (SystemBars)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            androidx.core.graphics.Insets systemBars = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
            );
            binding.appBar.setPadding(0, systemBars.top, 0, 0);
            binding.buttons.setPadding(0, 0, 0, systemBars.bottom);
            return windowInsets;
        });

        // handle button clicks (start / stop)
        binding.btnStart.setOnClickListener(v -> startServer());
        binding.btnStop.setOnClickListener(v -> stopServer());

        // toggle log window visibility
        ImageButton btn = binding.btnLog;
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

        // start watch for changes in logfile
        startWatch();

        // start periodic status check (every second)
        sinceLast = new Date().getTime()/1000;
        statusCheck = new Runnable() {
            @Override
            public void run() {
                // check current daemon state
                boolean currState = checkDaemonStatus();
                // handle state changes
                if (currState != stateLast) {
                    stateLast = currState;
                    sinceLast = new Date().getTime()/1000;
                }
                // display elapsed time.
                String elapsed = "";
                long span = new Date().getTime() / 1000 - sinceLast;
                int hrs = (int) (span / 3600);
                int mins = (int) (span % 3600) / 60;
                if (hrs < 24) {
                    elapsed = String.format(Locale.ENGLISH, "%d:%02d", hrs, mins);
                } else {
                    int days = hrs / 24;
                    hrs = hrs % 24;
                    elapsed = String.format(Locale.ENGLISH, "%dd %dh", hrs, mins);
                }
                binding.elapsed.setText(elapsed);

                // restart in 1 second
                handler.postDelayed(this, 1000);
            }
        };
    }

    // resume operation and handle status checks (again).
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statusCheck);
    }

    // pause operation and stop status checks.
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusCheck);
    }

    // destroy activity (stops server)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }

    // start kpclientd to run in background
    private void startServer() {
        // fail-safe: don't stop if it looks like we are running already
        if (serverProcess != null) {
            return;
        }
        binding.btnStart.setEnabled(false);
        // prepare configuration (use custom config file if available)
        prepareConfig();
        // start kpclientd in background
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

    // stop server
    private void stopServer() {
        binding.btnStop.setEnabled(false);
        try {
            // stop running process
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill kpclientd"});
            proc.waitFor();
            if (proc.exitValue() != 0) {
                throw new Exception("can't kill kpclientd");
            }
            // handle process variable
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

    // start watching for changes in log file.
    // keep a ring buffer of ten strings from the logfile (a single string
    // can hold multiple log lines) and display them in the log window.
    private void startWatch() {
        File log = new File(logPath);
        String[] body = new String[10];
        Arrays.fill(body, "");
        final long[] pos = {0, 0};

        // start watching
        watch = new FileObserver(new File(log.getParent()), FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(log.getName())) {
                    executor.execute(() -> {
                        // check for change in logfile length
                        long curr = log.length();
                        if (curr == pos[1]) {
                            return;
                        }
                        // log truncated: start from beginning
                        if (pos[1] > curr) {
                            pos[1] = 0L;
                        }
                        // read everything since the last read
                        String line = readLog(log, pos[1], curr);
                        if (line == null) line = "";
                        pos[1] = curr;

                        // show the last part of the logfile
                        synchronized (body) {
                            body[(int)pos[0]] = line;
                            pos[0] = (pos[0] + 1) % body.length;
                            StringBuilder show = new StringBuilder();
                            for (int i = 0; i < body.length; i++) {
                                int idx = (int)(pos[0] + i) % body.length;
                                show.append(body[idx]);
                            }
                            handler.post(() -> binding.log.setText(show.toString()));
                            // scroll to bottom
                            binding.log.post(() -> {
                                android.text.Layout layout = binding.log.getLayout();
                                if (layout != null) {
                                    int height = layout.getLineTop(binding.log.getLineCount());
                                    int viewHeight = binding.log.getHeight() - binding.log.getPaddingTop() - binding.log.getPaddingBottom();
                                    int scroll = height - viewHeight;
                                    binding.log.scrollTo(0, Math.max(scroll, 0));
                                }
                            });
                        }
                    });
                }
            }
        };
        watch.startWatching();
    }

    // read logfile from start to end position
    private String readLog(File file, long start, long end) {
        if (!file.exists() || file.length() == 0) {
            return "";
        }
        // limit span to 8k
        if (end - start > 8192L) {
            start = end - 8192L;
        }
        // read section of the log file as a string
        int n = (int) (end - start);
        byte[] buf = new byte[n];
        try (RandomAccessFile log = new RandomAccessFile(file, "r")) {
            // Direkt zur Startposition springen
            log.seek(start);
            log.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "<log read failed>";
    }

    // check the status of the daemon
    private boolean checkDaemonStatus() {
        // check the real state of the daemon
        try {
            // check if the process is running
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

    // prepare configuration: use custom config file /data/local/tmp/client.toml
    // if available otherwise use configuration from built-in assets folder.
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

    // copy file from the assets folder to the local directory
    private boolean copyAssetToStorage(String src, String tgt) {
        InputStream in = null;
        try {
            // check for absolute path (custom config)
            if (src.startsWith("/")) {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + src});
                in = process.getInputStream();
            } else {
                in = getAssets().open(src);
            }
            // copy content over to local file
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
            // close inputstream
            if (in != null) {
                try { in.close(); } catch (Exception ignored) {}
            }
        }
    }
}
