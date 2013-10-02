package com.ascendtv.kanjifix;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.util.ArrayList;

import eu.chainfire.libsuperuser.*;

public class MainActivity extends Activity {

    private Button revertButton;
    private Button applyButton;
    private TextView textField;

    private File originalFallback = new File("/system/etc/fallback_fonts.xml");
    private File backupFile;
    private File changedFile;
    private File ipaGothicFontLocalFile;
    private File ipaGothicFontOutputFile = new File("/system/fonts/IPAGothic.tff");

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

        backupFile = new File(MainActivity.this.getFilesDir() + File.separator + "fallback_fonts.xml.backup");
        changedFile = new File(MainActivity.this.getFilesDir() + File.separator + "fallback_fonts_new.xml");
        ipaGothicFontLocalFile = new File(MainActivity.this.getFilesDir() + File.separator + "IPAGothic.tff");

        checkUpdateStatus();
    }

    private void checkUpdateStatus() {
        try {
            xmlFile = new FallbackXmlFile(originalFallback);
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
        } else if (!canBeApplied && backupFileExists) {
            textField.setText(R.string.fix_applied);
        } else {
            textField.setText(R.string.fix_applied_no_backup);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private boolean applyingPatch = false;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_more_info:
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ascendedguard/android-kanji-fix/wiki"));
                startActivity(browserIntent);
                return true;
            case R.id.menu_install_alternative:
                if (applyingPatch) {
                    return true;
                }

                applyingPatch = true;
                applyButton.setEnabled(false);
                (new ApplyAlternativeChangesBackgroundTask()).execute();
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

    private void copyRawResourceToFile(int resource, File outputFile) throws IOException {
        InputStream stream = getResources().openRawResource(resource);
        FileOutputStream output = null;
        byte[] buffer = new byte[1024];
        try {
            output = new FileOutputStream(outputFile);

            while (stream.read(buffer) > 0) {
                output.write(buffer);
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
        copyRawResourceToFile(R.raw.fallback_fonts, backupFile);
    }

    private void copyAlternativeFontToLocalStorage() throws IOException {
        copyRawResourceToFile(R.raw.ipa_gothic, ipaGothicFontLocalFile);
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


}
