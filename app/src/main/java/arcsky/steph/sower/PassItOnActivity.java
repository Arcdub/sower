package arcsky.steph.sower;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;

/**
 * Shares this app's own APK so it can travel phone-to-phone with no internet:
 * the installed APK is copied into the cache dir and offered through the system
 * share sheet (Quick Share and Bluetooth both work fully offline).
 */
public class PassItOnActivity extends AppCompatActivity {

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".fileprovider";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pass_it_on);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        EdgeToEdge.apply(this, toolbar, findViewById(R.id.bottomBar));

        File source = new File(getApplicationInfo().sourceDir);
        TextView sizeLabel = findViewById(R.id.apkSize);
        sizeLabel.setText(getString(R.string.pass_it_on_size,
                Formatter.formatShortFileSize(this, source.length())));

        findViewById(R.id.shareAppButton).setOnClickListener(v -> shareApk());
    }

    private void shareApk() {
        final File source = new File(getApplicationInfo().sourceDir);
        final File shareDir = new File(getCacheDir(), "share");
        // Language-tagged so a phone carrying several editions sends an identifiable file.
        final File target = new File(shareDir, "Sower-" + BuildConfig.FLAVOR + ".apk");
        final Handler main = new Handler(Looper.getMainLooper());

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = true;
            try {
                if (!shareDir.isDirectory() && !shareDir.mkdirs()) {
                    throw new java.io.IOException("cannot create " + shareDir);
                }
                // Refresh the copy if it's missing or stale (e.g. after an app update).
                if (target.length() != source.length()
                        || target.lastModified() < source.lastModified()) {
                    try (InputStream in = new FileInputStream(source);
                         OutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            main.post(() -> {
                if (!success) {
                    Toast.makeText(this, R.string.pass_it_on_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                Uri uri = FileProvider.getUriForFile(this, AUTHORITY, target);
                Intent intent = new Intent(Intent.ACTION_SEND);
                // Generic type on purpose: Bluetooth's share target does not accept the
                // APK MIME type, and Bluetooth is our universal transport. The file still
                // arrives as Sower.apk and installs normally.
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, getString(R.string.pass_it_on_button)));
            });
        });
    }
}
