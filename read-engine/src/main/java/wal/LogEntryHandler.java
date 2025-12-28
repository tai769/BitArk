package wal;

public interface LogEntryHandler {
    void handle(LogEntry data);

}
