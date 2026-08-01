package dev.putrenkov.pdfaudit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Captures parser recoveries by logger/category, without depending on message text. */
final class PdfBoxDiagnosticCapture implements AutoCloseable {
    private static final Logger PARSER_LOGGER = Logger.getLogger("org.apache.pdfbox.pdfparser");

    private final long ownerThreadId = Thread.currentThread().threadId();
    private final AtomicInteger warningCount = new AtomicInteger();
    private final Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record != null
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
        handler.setLevel(Level.WARNING);
        PARSER_LOGGER.addHandler(handler);
    }

    int warningCount() {
        return warningCount.get();
    }

    @Override
    public void close() {
        PARSER_LOGGER.removeHandler(handler);
    }
}
