package com.ascendtv.kanjifix;

import android.content.res.Resources;

import java.io.*;

/**
 * FallbackXmlFile
 */
public class FallbackXmlFile {
    private StringBuilder contents;

    private final String DROID_SANS_FONT = "<file>DroidSansFallback.ttf</file>";
    private final String DROID_JAPANESE_FONT = "<file lang=\"ja\">MTLmr3m.ttf</file>";
    private final String TEMP_SWAP_STRING = "<file>DroidSansFallback-Backup.ttf</file>";
    private final String ALT_JAPANESE_FONT = "<file lang=\"ja\">IPAGothic.ttf</file>";

    public FallbackXmlFile(File file) throws IOException {
        contents = readFileContents(file);
    }

    private StringBuilder readFileContents(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb;
        } finally {
            try {
                br.close();
            } catch (IOException ex) {
                // This should only happen if another exception is being thrown...
                // No need to show 2 errors.
            }
        }
    }

    public boolean hasExpectedFonts() {
        return (contents.indexOf(DROID_SANS_FONT) > -1 && contents.indexOf(DROID_JAPANESE_FONT) > -1);
    }

    public boolean canApplyFix() {
        final String JA_LANGUAGE_POSITION = "lang=\"ja\"";

        int indexOfJapanese = contents.indexOf(JA_LANGUAGE_POSITION);
        int indexOfFallback = contents.indexOf(DROID_SANS_FONT);

        return indexOfFallback < indexOfJapanese;
    }

    public void performFontSwap() {
        replaceString(DROID_SANS_FONT, TEMP_SWAP_STRING);
        replaceString(DROID_JAPANESE_FONT, DROID_SANS_FONT);
        replaceString(TEMP_SWAP_STRING, DROID_JAPANESE_FONT);
    }

    public void performAlternativeFontSwap() {
        replaceString(DROID_SANS_FONT, TEMP_SWAP_STRING);
        replaceString(DROID_JAPANESE_FONT, DROID_SANS_FONT);
        replaceString(TEMP_SWAP_STRING, ALT_JAPANESE_FONT);
    }

    private void replaceString(String from, String to) {
        int index = contents.indexOf(from);

        if (index == -1) {
            // Nothing to replace
            return;
        }

        contents.replace(index, index + from.length(), to);
    }

    public void save(File file) throws IOException {
        writeFileText(file, contents.toString());
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
}
