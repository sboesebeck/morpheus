package de.caluga.morpheus;

import de.caluga.morpheus.cli.MorpheusCli;

/**
 * Entry point (kept so pom.xml mainClass and run.sh stay unchanged).
 * All CLI handling lives in de.caluga.morpheus.cli.
 */
public class Morpheus {
    public static void main(String[] args) {
        MorpheusCli.main(args);
    }
}
