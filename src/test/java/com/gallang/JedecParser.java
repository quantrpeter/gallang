package com.gallang;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a JEDEC (.jed) file into an integer fuse array.
 *
 * Handles:
 *   *F<default>  — sets default value for all fuses (0 or 1)
 *   *QF<n>       — total fuse count
 *   *L<addr> <bits> — overrides fuses starting at address
 *
 * UES (user electronic signature, fuses 5828-5891 for GAL22V10) is
 * intentionally excluded from functional comparison because galasm writes
 * a design-name string there while gallang leaves it at the *F0 default.
 */
public class JedecParser {

    /** Fuse index beyond which we do not compare (UES region for GAL22V10). */
    public static final int COMPARE_LIMIT = 5828;

    private static final Pattern QF_PATTERN  = Pattern.compile("\\*QF(\\d+)");
    private static final Pattern F_PATTERN   = Pattern.compile("\\*F([01])");
    private static final Pattern L_PATTERN   = Pattern.compile("\\*L(\\d+)\\s+([01]+)");

    /**
     * Parses the JEDEC file at {@code path} and returns a fuse array of
     * length {@code COMPARE_LIMIT}.  Only fuses 0 – COMPARE_LIMIT-1 are
     * included.
     */
    public static int[] parse(Path path) throws IOException {
        String text = Files.readString(path);

        // Determine fuse count (default 5892 for GAL22V10)
        int fuseCount = 5892;
        Matcher qfM = QF_PATTERN.matcher(text);
        if (qfM.find()) {
            fuseCount = Integer.parseInt(qfM.group(1));
        }

        // Default fuse value (*F0 or *F1)
        int defaultVal = 0;
        Matcher fM = F_PATTERN.matcher(text);
        if (fM.find()) {
            defaultVal = Integer.parseInt(fM.group(1));
        }

        int limit  = Math.min(fuseCount, COMPARE_LIMIT);
        int[] fuses = new int[limit];
        if (defaultVal == 1) {
            java.util.Arrays.fill(fuses, 1);
        }

        // Apply *L records
        Matcher lM = L_PATTERN.matcher(text);
        while (lM.find()) {
            int addr = Integer.parseInt(lM.group(1));
            String bits = lM.group(2);
            for (int i = 0; i < bits.length(); i++) {
                int idx = addr + i;
                if (idx < limit) {
                    fuses[idx] = bits.charAt(i) - '0';
                }
            }
        }

        return fuses;
    }
}
