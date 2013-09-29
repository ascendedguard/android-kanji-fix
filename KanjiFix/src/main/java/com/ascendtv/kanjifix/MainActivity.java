package com.ascendtv.kanjifix;

import android.app.Activity;
import android.content.ContextWrapper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;

import eu.chainfire.libsuperuser.*;

public class MainActivity extends Activity {

    private Button revertButton;
    private Button applyButton;
    private TextView textField;

    File originalFallback = new File("/system/etc/fallback_fonts.xml");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applyButton = (Button) findViewById(R.id.apply_fix_btn);
        applyButton.setOnClickListener(applyListener);

        revertButton = (Button) findViewById(R.id.revert_btn);
        revertButton.setOnClickListener(revertListener);

        textField = (TextView) findViewById(R.id.textField);

        checkUpdateStatus();
    }

    private void checkUpdateStatus() {
        String xmlFile;
        try {
            xmlFile = getFileText(originalFallback);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            RunToastOnUiThread("File failed to read font file.", Toast.LENGTH_SHORT);
            return;
        }

        File backupFile = getBackupFile();

        boolean canBeApplied = isFileValidToChange(xmlFile);
        boolean backupFileExists = backupFile.exists();

        if (canBeApplied) {
            applyButton.setEnabled(true);
        } else {
            applyButton.setEnabled(false);
        }

        if (backupFileExists && !canBeApplied) {
            revertButton.setEnabled(true);
        } else {
            revertButton.setEnabled(false);
        }

        if (canBeApplied) {
            textField.setText(R.string.fix_not_applied);
        } else if (!canBeApplied && backupFileExists) {
            textField.setText(R.string.fix_applied);
        } else {
            textField.setText(R.string.fix_applied_no_backup);
        }
    }

    private File getBackupFile() {
        return new File(MainActivity.this.getFilesDir() + File.separator + "fallback_fonts.xml.backup");
    }

    private File getChangedFile() {
        return new File(MainActivity.this.getFilesDir() + File.separator + "fallback_fonts_new.xml");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
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

            String xmlFile;
            // Check if the file is valid

            try {
                xmlFile = getFileText(originalFallback);
            }
            catch (Exception ex) {
                RunToastOnUiThread("File failed to read: " + ex, Toast.LENGTH_SHORT);
                return null;
            }

            if (!xmlFile.contains("<file>DroidSansFallback.ttf</file>") || !xmlFile.contains("<file lang=\"ja\">MTLmr3m.ttf</file>")) {
                RunToastOnUiThread("Couldn't find expected fonts in fallback_fonts.xml", Toast.LENGTH_LONG);
                return null;
            }

            if (!isFileValidToChange(xmlFile)) {
                RunToastOnUiThread("The fix is already applied. Can't apply it twice!", Toast.LENGTH_LONG);
                return null;
            }

            // There's probably a much better way of doing this than creating ~15KB of strings...
            xmlFile = xmlFile.replace("<file>DroidSansFallback.ttf</file>", "<file>DroidSansFallback-Backup.ttf</file>");
            xmlFile = xmlFile.replace("<file lang=\"ja\">MTLmr3m.ttf</file>", "<file>DroidSansFallback.ttf</file>");
            xmlFile = xmlFile.replace("<file>DroidSansFallback-Backup.ttf</file>", "<file lang=\"ja\">MTLmr3m.ttf</file>");


            // Write the changed file locally
            File changedFile = getChangedFile();
            try {
                writeFileText(changedFile, xmlFile);
            }
            catch (IOException ex) {
                ex.printStackTrace();
                RunToastOnUiThread("Failed to write changes to file.", Toast.LENGTH_LONG);
                return null;
            }

            // Make the backup before we copy
            File backupFile = getBackupFile();
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

            File originalFallback = new File("/system/etc/fallback_fonts.xml");

            // Check if the file is valid
            String xmlFile;
            try {
                xmlFile = getFileText(originalFallback);
            }
            catch (Exception ex) {
                RunToastOnUiThread("File failed to read: " + ex, Toast.LENGTH_SHORT);
                return null;
            }

            if (!xmlFile.contains("<file>DroidSansFallback.ttf</file>") || !xmlFile.contains("<file lang=\"ja\">MTLmr3m.ttf</file>")) {
                RunToastOnUiThread("Couldn't find expected fonts in fallback_fonts.xml", Toast.LENGTH_LONG);
                return null;
            }

            if (isFileValidToChange(xmlFile)) {
                RunToastOnUiThread("The fix is not applied. Can't undo what has not yet been done!", Toast.LENGTH_LONG);
                return null;
            }

            // Make sure the backup exists.
            File backupFile = getBackupFile();
            if (!backupFile.exists()) {
                RunToastOnUiThread("The backup file was not found! Can't restore!", Toast.LENGTH_LONG);
                return null;
            }

            // Create the commands that will be run as superuser:
            String[] commands = new String[] {
                    "mount -o remount,rw /system",                         // Make system writable
                    "yes | cp -f " + backupFile + " " + originalFallback,  // Copy the new file
                    "chmod 644 /system/etc/fallback_fonts.xml",            // Set rw-r-r permissions
                    "chown root:root /system/etc/fallback_fonts.xml",      // Set rw-r-r permissions
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

            MainActivity.this.checkUpdateStatus();
        }
    }

    private boolean isFileValidToChange(String xml) {
        int indexOfJapanese = xml.indexOf("<file lang=\"ja\">MTLmr3m.ttf</file>");
        int indexOfFallback = xml.indexOf("<file>DroidSansFallback.ttf</file>");

        return indexOfFallback < indexOfJapanese;
    }

    private void RunToastOnUiThread(final String text, final int length) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, length).show();
            }
        });
    }

    private void writeFileText(File outputFile, String text) throws IOException {
        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outputFile), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            out.write(text.toString());
        } finally {
            out.close();
        }
    }

    private String getFileText(File backupFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(backupFile));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                // This should only happen if another exception is being thrown...
                // No need to show 2 errors.
            }
        }
    }

    View.OnClickListener applyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            (new ApplyChangesBackgroundTask()).execute();
        }
    };

    View.OnClickListener revertListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
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
