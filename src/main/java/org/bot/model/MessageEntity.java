package org.bot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA-сущность, маппится на таблицу messages.
 * Добавлено: замена raw JDBC на Hibernate ORM с JPA-аннотациями.
 */
@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private int chatId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "name_sender", nullable = false, length = 255)
    private String nameSender;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    public MessageEntity() {
    }

    public MessageEntity(int chatId, long userId, String nameSender, String text) {
        this.chatId = chatId;
        this.userId = userId;
        this.nameSender = nameSender;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public int getChatId() {
        return chatId;
    }

    public long getUserId() {
        return userId;
    }

    public String getNameSender() {
        return nameSender;
    }

    public String getText() {
        return text;
    }

    public String getFormattedMessage() {
        return getNameSender() + ": " + getText();
    }
}
