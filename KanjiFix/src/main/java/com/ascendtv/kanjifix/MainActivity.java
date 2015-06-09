package com.ascendtv.kanjifix;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends AppCompatActivity {

    private Button revertButton;
    private Button applyButton;
    private TextView textField;
    private MenuItem installBackupScriptItem;
    private MenuItem removeBackupScriptItem;

    //private File originalFallback = new File("/system/etc/fonts.xml");
    //private File originalFallback = new File("/system/etc/fallback_fonts.xml");
    private File originalFallback;
    private File backupFile;
    private File changedFile;
    private File ipaGothicFontLocalFile;
    private File backupScriptLocalFile;
    private File ipaGothicFontOutputFile = new File("/system/fonts/IPAGothic.ttf");
    private File addonFolder = new File("/system/addon.d");
    private File backupScriptOutputFile = new File("/system/addon.d/51-kanji.sh");

    private FallbackXmlFile xmlFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applyButton = (Button) findViewById(R.id.apply_fix_btn);
        applyButton.setOnClickListener(applyListener);

        revertButton = (Button) findViewById(R.id.revert_btn);
        revertButton.setOnClickListener(revertListener);

        textField = (TextView) findViewById(R.id.textField);

        String dir = MainActivity.this.getFilesDir() + File.separator;
        backupFile = new File(dir + FallbackXmlFile.getBackupFontFile());
        changedFile = new File(dir + "fallback_fonts_new.xml");
        ipaGothicFontLocalFile = new File(dir + "IPAGothic.tff");
        backupScriptLocalFile = new File(dir + "51-kanji.sh");

        originalFallback = FallbackXmlFile.getFontFile();

        checkUpdateStatus();
    }

    private void checkUpdateStatus() {
        try {
            xmlFile = new FallbackXmlFile();
        }
        catch (Exception ex) {
            ex.printStackTrace();
            RunToastOnUiThread("File failed to read font file.", Toast.LENGTH_SHORT);
            return;
        }

        boolean canBeApplied = xmlFile.canApplyFix();
        boolean backupFileExists = backupFile.exists();

        if (canBeApplied) {
            applyButton.setEnabled(true);
            revertButton.setEnabled(false);
        } else {
            applyButton.setEnabled(false);
            revertButton.setEnabled(true);
        }

        if (canBeApplied) {
            textField.setText(R.string.fix_not_applied);
        } else if (backupFileExists) {
            textField.setText(R.string.fix_applied);
        } else {
            textField.setText(R.string.fix_applied_no_backup);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        installBackupScriptItem = menu.findItem(R.id.menu_install_backup_script);
        removeBackupScriptItem = menu.findItem(R.id.menu_remove_backup_script);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        boolean scriptExists = backupScriptOutputFile.exists();
        boolean addondExists = addonFolder.exists() && addonFolder.isDirectory();

        installBackupScriptItem.setVisible(!scriptExists && addondExists);
        removeBackupScriptItem.setVisible(scriptExists && addondExists);

        return true;
    }

    private boolean applyingPatch = false;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_more_info:
                SendAnalyticsClick("More Info");
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ascendedguard/android-kanji-fix/wiki"));
                startActivity(browserIntent);
                return true;
            case R.id.menu_install_alternative:
                if (applyingPatch) {
                    return true;
                }

                SendAnalyticsClick("Install Alternative");
                applyingPatch = true;
                applyButton.setEnabled(false);
                (new ApplyAlternativeChangesBackgroundTask()).execute();
                return true;
            case R.id.menu_install_backup_script:
                if (applyingPatch) {
                    return true;
                }

                SendAnalyticsClick("Install Backup Script");
                applyingPatch = true;
                (new CreateBackupScriptBackgroundTask()).execute();
                return true;
            case R.id.menu_remove_backup_script:
                if (applyingPatch) {
                    return true;
                }

                SendAnalyticsClick("Remove Backup Script");
                applyingPatch = true;
                (new RemoveScriptBackgroundTask()).execute();
                return true;
        }

        return false;
    }

    private class ApplyChangesBackgroundTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // running on the main thread
                RunToastOnUiThread("Running on main thread. Cannot proceed.", Toast.LENGTH_SHORT);
                return null;
            } else {
                // not running on the main thread
            }

            boolean rooted = Shell.SU.available();
            if (!rooted) {
                // This can also happen if ADB Shell is open with root access.
                RunToastOnUiThread("Root was not detected. Root is required to continue.", Toast.LENGTH_LONG);
                return null;
            }


            File outputDirectory = MainActivity.this.getFilesDir();

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            if (!xmlFile.hasExpectedFonts()) {
                RunToastOnUiThread("Couldn't find expected fonts in fallback_fonts.xml", Toast.LENGTH_LONG);
                return null;
            }

            if (!xmlFile.canApplyFix()) {
                RunToastOnUiThread("The fix is already applied. Can't apply it twice!", Toast.LENGTH_LONG);
                return null;
            }

            xmlFile.performFontSwap();

            // Write the changed file locally
            try {
                xmlFile.save(changedFile);
                //writeFileText(changedFile, xmlFile);
            }
            catch (IOException ex) {
                ex.printStackTrace();
                RunToastOnUiThread("Failed to write changes to file.", Toast.LENGTH_LONG);
                return null;
            }


            // Make the backup before we copy
            try
            {
                MainActivity.copy(originalFallback, backupFile);
            }
            catch (IOException ex) {
                RunToastOnUiThread("File failed to copy. " + ex, Toast.LENGTH_SHORT);
                return null;
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | cp -f " + changedFile + " " + originalFallback, // Copy the new file
                    "chmod 644 /system/etc/fallback_fonts.xml",            // Set rw-r-r permissions
                    "chown root:root /system/etc/fallback_fonts.xml",      // Set correct owner/group
                    "mount -o remount,ro /system",                         // Make it read-only again.
            };

            // Superuser execute:
            Shell.SU.run(commands);

            RunToastOnUiThread("Fonts successfully fixed. A reboot is required.", Toast.LENGTH_LONG);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            applyingPatch = false;
            MainActivity.this.checkUpdateStatus();
        }
    }

    private class ApplyAlternativeChangesBackgroundTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // running on the main thread
                RunToastOnUiThread("Running on main thread. Cannot proceed.", Toast.LENGTH_SHORT);
                return null;
            } else {
                // not running on the main thread
            }

            boolean rooted = Shell.SU.available();
            if (!rooted) {
                // This can also happen if ADB Shell is open with root access.
                RunToastOnUiThread("Root was not detected. Root is required to continue.", Toast.LENGTH_LONG);
                return null;
            }


            File outputDirectory = MainActivity.this.getFilesDir();

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            if (!xmlFile.hasExpectedFonts()) {
                RunToastOnUiThread("Couldn't find expected fonts in fallback_fonts.xml", Toast.LENGTH_LONG);
                return null;
            }

            if (!xmlFile.canApplyFix()) {
                RunToastOnUiThread("The fix is already applied. Can't apply it twice!", Toast.LENGTH_LONG);
                return null;
            }

            xmlFile.performAlternativeFontSwap();

            try{
                copyAlternativeFontToLocalStorage();
            }
            catch (IOException ex) {
                ex.printStackTrace();
                RunToastOnUiThread("Failed to copy IPAGothic font to local storage.", Toast.LENGTH_LONG);
                return null;
            }

            // Write the changed file locally
            try {
                xmlFile.save(changedFile);
                //writeFileText(changedFile, xmlFile);
            }
            catch (IOException ex) {
                ex.printStackTrace();
                RunToastOnUiThread("Failed to write changes to file.", Toast.LENGTH_LONG);
                return null;
            }

            // Make the backup before we copy
            try
            {
                MainActivity.copy(originalFallback, backupFile);
            }
            catch (IOException ex) {
                RunToastOnUiThread("File failed to copy. " + ex, Toast.LENGTH_SHORT);
                return null;
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | cp -f " + ipaGothicFontLocalFile + " " + ipaGothicFontOutputFile, // Write new font
                    "chmod 644 " + ipaGothicFontOutputFile,                                  // Set rw-r-r on font
                    "chown root:root " + ipaGothicFontOutputFile,                            // Set font owner
                    "yes | cp -f " + changedFile + " " + originalFallback, // Copy the new file
                    "chmod 644 /system/etc/fallback_fonts.xml",            // Set rw-r-r permissions
                    "chown root:root /system/etc/fallback_fonts.xml",      // Set correct owner/group
                    "mount -o remount,ro /system",                         // Make it read-only again.
            };

            // Superuser execute:
            Shell.SU.run(commands);

            RunToastOnUiThread("Fonts successfully fixed. A reboot is required.", Toast.LENGTH_LONG);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            applyingPatch = false;
            MainActivity.this.checkUpdateStatus();
        }
    }

    private class RevertChangesBackgroundTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // running on the main thread
                RunToastOnUiThread("Running on main thread. Cannot proceed.", Toast.LENGTH_SHORT);
                return null;
            } else {
                // not running on the main thread
            }

            boolean rooted = Shell.SU.available();
            if (!rooted) {
                // This can also happen if ADB Shell is open with root access.
                RunToastOnUiThread("Root was not detected. Root is required to continue.", Toast.LENGTH_LONG);
                return null;
            }


            if (!xmlFile.hasExpectedFonts() || !backupFile.exists()) {
                try {
                    copyOriginalFallbackToLocalStorage();
                }
                catch (IOException ex) {
                    RunToastOnUiThread("Backup file could not be created! Can't restore!", Toast.LENGTH_LONG);
                    return null;
                }
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | cp -f " + backupFile + " " + originalFallback,  // Copy the new file
                    "chmod 644 /system/etc/fallback_fonts.xml",            // Set rw-r-r permissions
                    "chown root:root /system/etc/fallback_fonts.xml",      // Set correct owner/group
                    "mount -o remount,ro /system",                         // Make it read-only again.
            };

            // Superuser execute:
            Shell.SU.run(commands);

            RunToastOnUiThread("Fonts successfully reverted. A reboot is required.", Toast.LENGTH_LONG);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            applyingPatch = false;
            MainActivity.this.checkUpdateStatus();
        }
    }

    private class CreateBackupScriptBackgroundTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // running on the main thread
                RunToastOnUiThread("Running on main thread. Cannot proceed.", Toast.LENGTH_SHORT);
                return null;
            } else {
                // not running on the main thread
            }

            boolean rooted = Shell.SU.available();
            if (!rooted) {
                // This can also happen if ADB Shell is open with root access.
                RunToastOnUiThread("Root was not detected. Root is required to continue.", Toast.LENGTH_LONG);
                return null;
            }


            File outputDirectory = MainActivity.this.getFilesDir();

            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            if (xmlFile.canApplyFix()) {
                RunToastOnUiThread("The fix must be applied first!", Toast.LENGTH_LONG);
                return null;
            }

            try{
                copyBackupScriptToLocalStorage();
            }
            catch (IOException ex) {
                ex.printStackTrace();
                RunToastOnUiThread("Failed to copy backup script to local storage.", Toast.LENGTH_LONG);
                return null;
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | cp -f " + backupScriptLocalFile + " " + backupScriptOutputFile, // Write addon.d file
                    "chmod 755 " + backupScriptOutputFile,                                  // Set rwxr-xr-x on script
                    "chown root:root " + backupScriptOutputFile,                            // Set script owner
                    "mount -o remount,ro /system",                         // Make it read-only again.
            };

            // Superuser execute:
            Shell.SU.run(commands);

            RunToastOnUiThread("Backup addon.d script installed. Fix should stay between flashes.", Toast.LENGTH_LONG);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            applyingPatch = false;
        }
    }

    private class RemoveScriptBackgroundTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // running on the main thread
                RunToastOnUiThread("Running on main thread. Cannot proceed.", Toast.LENGTH_SHORT);
                return null;
            } else {
                // not running on the main thread
            }

            boolean rooted = Shell.SU.available();
            if (!rooted) {
                // This can also happen if ADB Shell is open with root access.
                RunToastOnUiThread("Root was not detected. Root is required to continue.", Toast.LENGTH_LONG);
                return null;
            }


            if (!backupScriptOutputFile.exists()) {
                // File already removed, nothing to do.
                return null;
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | rm -f " + backupScriptLocalFile + " " + backupScriptOutputFile, // Write addon.d file
                    "mount -o remount,ro /system",                         // Make it read-only again.
            };

            // Superuser execute:
            Shell.SU.run(commands);

            RunToastOnUiThread("Backup addon.d script removed.", Toast.LENGTH_LONG);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            applyingPatch = false;
        }
    }

    private void copyRawResourceToFile(int resource, File outputFile) throws IOException {
        InputStream stream = getResources().openRawResource(resource);
        FileOutputStream output = null;
        byte[] buffer = new byte[1024];
        int read = 0;
        try {
            output = new FileOutputStream(outputFile);

            while ((read = stream.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }

            output.flush();
        }
        finally {
            stream.close();

            if (output != null) {
                output.close();
            }
        }
    }

    private void copyOriginalFallbackToLocalStorage() throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            copyRawResourceToFile(R.raw.fonts, backupFile);
        } else {
            copyRawResourceToFile(R.raw.fallback_fonts, backupFile);
        }
    }

    private void copyAlternativeFontToLocalStorage() throws IOException {
        copyRawResourceToFile(R.raw.ipa_gothic, ipaGothicFontLocalFile);
    }

    private void copyBackupScriptToLocalStorage() throws IOException {
        copyRawResourceToFile(R.raw.backup, backupScriptLocalFile);
    }

    private void RunToastOnUiThread(final String text, final int length) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, length).show();
            }
        });
    }

    View.OnClickListener applyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (applyingPatch) {
                return;
            }

            applyingPatch = true;

            SendAnalyticsClick("Apply Fix");
            applyButton.setEnabled(false);
            (new ApplyChangesBackgroundTask()).execute();
        }
    };

    View.OnClickListener revertListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (applyingPatch) {
                return;
            }

            applyingPatch = true;

            SendAnalyticsClick("Revert");
            revertButton.setEnabled(false);
            (new RevertChangesBackgroundTask()).execute();
        }
    };

    private static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private static void SendAnalyticsClick(String label) {
        if (KanjiFix.tracker == null) {
            return;
        }

        KanjiFix.tracker.send(new HitBuilders.EventBuilder()
               .setCategory("UX")
                .setAction("click")
                .setLabel(label)
               .build());
    }
}
