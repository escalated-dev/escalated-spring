-- SHA-256 (hex) of the random token the widget addresses a chat by. The token
-- itself is only ever returned once, by POST /widget/chat/start.
ALTER TABLE escalated_chat_sessions ADD COLUMN widget_token_hash VARCHAR(64) NULL;
CREATE UNIQUE INDEX idx_escalated_chat_sessions_widget_token ON escalated_chat_sessions (widget_token_hash);
