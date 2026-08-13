package com.sashplatonov.earnit.kids.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;

public interface TelegramBotService {
    void handleUpdate(JsonNode update);
}
