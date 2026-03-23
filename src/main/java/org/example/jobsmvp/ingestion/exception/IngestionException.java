package org.example.jobsmvp.ingestion.exception;

/**
 * Base exception for all ingestion pipeline failures.
 * Sub-classes let the orchestrator make fine-grained retry decisions.
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }

    // ── Sub-types ─────────────────────────────────────────────────────────────

    /** Thrown when the external API returns an error or non-200 response. */
    public static class ApiException extends IngestionException {
        private final int statusCode;

        public ApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }

    /** Thrown when LLM extraction returns malformed or empty JSON. */
    public static class ExtractionException extends IngestionException {
        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when a graph write (MERGE) fails after exhausting retries. */
    public static class GraphPersistenceException extends IngestionException {
        public GraphPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Thrown when an entity cannot be normalised to any known or candidate node. */
    public static class NormalizationException extends IngestionException {
        public NormalizationException(String message) {
            super(message);
        }
    }
}