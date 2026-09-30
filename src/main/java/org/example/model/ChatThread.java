package org.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatThread {
    private final String id;
    private final List<ChatMessage> messages;

    public ChatThread() {
        this.id = UUID.randomUUID().toString();
        this.messages = new ArrayList<>();
    }

    public void addMessage(String senderAlias, String text) {
        messages.add(new ChatMessage(senderAlias, text));
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getId() {
        return id;
    }

    public String getFormattedHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- История анонимного чата (Сообщений: ").append(messages.size()).append(") ---\n");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            sb.append(i + 1).append(". [").append(msg.senderAlias()).append("]: ").append(msg.text()).append("\n");
        }
        return sb.toString();
    }
}