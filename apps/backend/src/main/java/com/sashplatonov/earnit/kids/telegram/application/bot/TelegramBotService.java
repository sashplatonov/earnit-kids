package com.sashplatonov.earnit.kids.telegram.application.bot;

import com.fasterxml.jackson.databind.JsonNode;

public interface TelegramBotService {
    void handleUpdate(JsonNode update);
}
