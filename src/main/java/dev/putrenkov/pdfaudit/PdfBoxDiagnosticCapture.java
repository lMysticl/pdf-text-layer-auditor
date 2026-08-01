package dev.putrenkov.pdfaudit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Captures parser recoveries by logger/category, without depending on message text. */
final class PdfBoxDiagnosticCapture implements AutoCloseable {
    private static final Logger PDFBOX_LOGGER = Logger.getLogger("org.apache.pdfbox");
    private static final Logger FONTBOX_LOGGER = Logger.getLogger("org.apache.fontbox");
    private static final Logger PARSER_LOGGER = Logger.getLogger("org.apache.pdfbox.pdfparser");
    private static final ReentrantLock LOGGING_SCOPE = new ReentrantLock();

    private final long ownerThreadId = Thread.currentThread().threadId();
    private final AtomicInteger warningCount = new AtomicInteger();
    private final boolean pdfBoxParentHandlers;
    private final boolean fontBoxParentHandlers;
    private boolean closed;
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record != null
                    && record.getLoggerName() != null
                    && record.getLoggerName().startsWith(PARSER_LOGGER.getName())
                    && record.getLongThreadID() == ownerThreadId
                    && record.getLevel().intValue() >= Level.WARNING.intValue()) {
                warningCount.incrementAndGet();
            }
        }

        @Override
        public void flush() {
            // No output is buffered.
        }

        @Override
        public void close() {
            // The enclosing capture removes this handler from the logger.
        }
    };

    PdfBoxDiagnosticCapture() {
        LOGGING_SCOPE.lock();
        pdfBoxParentHandlers = PDFBOX_LOGGER.getUseParentHandlers();
        fontBoxParentHandlers = FONTBOX_LOGGER.getUseParentHandlers();
        handler.setLevel(Level.WARNING);
        PDFBOX_LOGGER.setUseParentHandlers(false);
        FONTBOX_LOGGER.setUseParentHandlers(false);
        PDFBOX_LOGGER.addHandler(handler);
        FONTBOX_LOGGER.addHandler(handler);
    }

    int warningCount() {
        return warningCount.get();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        PDFBOX_LOGGER.removeHandler(handler);
        FONTBOX_LOGGER.removeHandler(handler);
        PDFBOX_LOGGER.setUseParentHandlers(pdfBoxParentHandlers);
        FONTBOX_LOGGER.setUseParentHandlers(fontBoxParentHandlers);
        LOGGING_SCOPE.unlock();
    }
}
