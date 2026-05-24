package de.caluga.morpheus;

/**
 * Marker interface for commands that require Morphium and Messaging to be initialized.
 * Commands implementing this interface will have Morphium connection established before execute() is called.
 * Commands NOT implementing this interface can run without MongoDB connectivity.
 */
public interface IRequiresMorphium extends ICommand {
    // Marker interface - no methods needed
}
