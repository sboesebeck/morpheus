package de.caluga.morpheus.cli;

import de.caluga.morpheus.Version;
import de.caluga.morpheus.config.ConfigurationManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
    name = "morpheus",
    description = "Morphium toolbox and messaging monitor.",
    mixinStandardHelpOptions = true,
    versionProvider = RootCommand.VersionProvider.class
)
public class RootCommand implements Callable<Integer> {

    @Option(names = {"-c", "--connection"},
            description = "Connection name from the config (default: configured default connection).")
    String connection;

    @Option(names = {"--theme"}, description = "Theme name (default: 'default').")
    String theme;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output.")
    boolean verbose;

    @Option(names = {"--messaging"},
            description = "Messaging implementation override: ${COMPLETION-CANDIDATES}.")
    MessagingImpl messaging;

    public enum MessagingImpl { single, multi }

    @Spec
    CommandSpec spec;

    private MorpheusContext context;

    /** Lazily builds the shared context with global options applied. */
    public synchronized MorpheusContext context() {
        if (context == null) {
            ConfigurationManager cfg = new ConfigurationManager();
            if (theme != null) cfg.setThemeOverride(theme);
            if (connection != null) cfg.setConnectionOverride(connection);
            if (messaging != null) cfg.setMessagingOverride(messaging.name());
            cfg.setVerbose(verbose);
            context = new MorpheusContext(cfg);
        }
        return context;
    }

    public synchronized void closeContext() {
        if (context != null) context.close();
    }

    @Override
    public Integer call() {
        de.caluga.morpheus.tui.MorpheusTui.launch(context());
        return 0;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] {"morpheus " + Version.VERSION};
        }
    }
}
