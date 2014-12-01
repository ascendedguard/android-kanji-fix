package com.ascendtv.kanjifix;

import android.content.res.Resources;
import android.os.Build;

import java.io.*;

/**
 * FallbackXmlFile
 */
public class FallbackXmlFile {
    private StringBuilder contents;

    // Doesn't really matter, as long as its unique to both files.
    private final String TEMP_SWAP_STRING = "DroidSansFallback-Backup.ttf";

    private String droidReplaceFont;
    private String droidJapaneseFont;
    private String droidAlternateFont;

    private File fontFile;

    public FallbackXmlFile() throws IOException {

        int SDK_INT = Build.VERSION.SDK_INT;

        if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // 5.0+

            droidReplaceFont = "    <family lang=\"zh-Hans\">\n" +
                    "        <font weight=\"400\" style=\"normal\">NotoSansHans-Regular.otf</font>\n" +
                    "    </family>";
            droidJapaneseFont = "    <family lang=\"ja\">\n" +
                    "        <font weight=\"400\" style=\"normal\">MTLmr3m.ttf</font>\n" +
                    "    </family>";
            droidAlternateFont = "    <family lang=\"ja\">\n" +
                    "        <font weight=\"400\" style=\"normal\">IPAGothic.ttf</font>\n" +
                    "    </family>";
        } else { // 4.1 - 4.4
            droidReplaceFont = "<file>DroidSansFallback.ttf</file>";
            droidJapaneseFont = "<file lang=\"ja\">MTLmr3m.ttf</file>";
            droidAlternateFont = "<file lang=\"ja\">IPAGothic.ttf</file>";
        }

        fontFile = FallbackXmlFile.getFontFile();
        contents = readFileContents(fontFile);
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
        return (contents.indexOf(droidReplaceFont) > -1 && contents.indexOf(droidJapaneseFont) > -1);
    }

    public boolean canApplyFix() {
        int indexOfJapanese = contents.indexOf(droidJapaneseFont);
        int indexOfFallback = contents.indexOf(droidReplaceFont);

        return indexOfFallback < indexOfJapanese;
    }

    public void performFontSwap() {
        replaceString(droidReplaceFont, TEMP_SWAP_STRING);
        replaceString(droidJapaneseFont, droidReplaceFont);
        replaceString(TEMP_SWAP_STRING, droidJapaneseFont);
    }

    public static File getFontFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // 5.0+
            return new File("/system/etc/fonts.xml");
        } else {
            return new File("/system/etc/fallback_fonts.xml");
        }
    }

    public static String getBackupFontFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // 5.0+
            return "fonts.xml.backup";
        } else {
            return "fallback_fonts.xml.backup";
        }
    }


    public void performAlternativeFontSwap() {
        replaceString(droidReplaceFont, TEMP_SWAP_STRING);
        replaceString(droidJapaneseFont, droidReplaceFont);
        replaceString(TEMP_SWAP_STRING, droidAlternateFont);
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
