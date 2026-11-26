-- Задачи генерации отчётов (FR-REP-06). Файлы хранятся 7 суток (FR-REP-07), потом задача удаляется.
CREATE TABLE report_job (
    id            UUID PRIMARY KEY,
    report_type   VARCHAR(32)  NOT NULL,
    parameters    VARCHAR(1000) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    file_path     VARCHAR(512),
    error_message VARCHAR(1000),
    owner_email   VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    finished_at   TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ
);

CREATE INDEX idx_report_job_owner ON report_job (owner_email, created_at DESC);
CREATE INDEX idx_report_job_expires ON report_job (expires_at);
