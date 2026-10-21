package org.bot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChatThread {
    private static final int MAX_MESSAGES = 10;
    private final String id;
    private final List<ChatMessage> messages;

    public ChatThread() {
        this(UUID.randomUUID().toString().substring(0, 8));
    }

    public ChatThread(String id) {
        this.id = id;
        this.messages = new ArrayList<>();
    }

    public static ChatThread fromMessages(String id, List<ChatMessage> messages) {
        ChatThread thread = new ChatThread(id);
        thread.messages.addAll(messages);
        return thread;
    }

    // Проверка: можно ли пользователю отправить новое сообщение в эту ветку
    public boolean canUserPost(String userId) {
        if (isFinished()) {
            return false;
        }
        if (messages.isEmpty()) {
            return true;
        }
        // Нельзя писать два раза подряд
        String lastSender = messages.get(messages.size() - 1).senderAlias();
        return !lastSender.equalsIgnoreCase(userId);
    }

    public boolean addMessage(String senderAlias, String text) {
        if (!canUserPost(senderAlias)) {
            return false;
        }
        messages.add(new ChatMessage(senderAlias, text));
        return true;
    }

    public boolean isFinished() {
        return messages.size() >= MAX_MESSAGES;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public String getId() {
        return id;
    }

    public String getFormattedHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- История анонимного чата #").append(id)
                .append(" (").append(messages.size()).append("/").append(MAX_MESSAGES).append(") ---\n");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            sb.append(i + 1).append(". [").append(msg.senderAlias()).append("]: ").append(msg.text()).append("\n");
        }
        return sb.toString();
    }
}