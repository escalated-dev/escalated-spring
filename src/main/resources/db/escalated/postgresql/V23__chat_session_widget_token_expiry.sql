-- When the widget's chat token stops working (escalated.guest-access.ttl-minutes
-- after the chat starts, as in escalated-laravel). Chats already in progress
-- keep NULL; their next widget request gives the token a full lifetime.
ALTER TABLE escalated_chat_sessions ADD COLUMN widget_token_expires_at TIMESTAMP NULL;
