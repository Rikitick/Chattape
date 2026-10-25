package org.bot.model;

public class Message
{
    private int id;
    private int chat_id;
    private long user_id;
    private String name_sender;
    private String text;

    public Message()
    {
    }

    public Message(int id, int chat_id, long user_id, String name_sender, String text)
    {
        this.id = id;
        this.chat_id = chat_id;
        this.user_id = user_id;
        this.name_sender = name_sender;
        this.text = text;
    }

    public int getId()
    {
        return id;
    }

    public int getChatId()
    {
        return chat_id;
    }

    public long getUserId()
    {
        return user_id;
    }

    public String getNameSender()
    {
        return name_sender;
    }

    public String getText()
    {
        return text;
    }

    public String getMessage()
    {
        return getNameSender() + ": " + getText();
    }
}