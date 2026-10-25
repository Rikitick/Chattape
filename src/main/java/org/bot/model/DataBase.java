package org.bot.model;

import org.bot.db.ORM;
import java.util.List;

public class DataBase extends ORM
{
	public List<Message> getRandomChat(long user_id)
	{
		return super.getRandomChat(user_id);
	}

	public void saveNewMessage(Message message)
	{
		super.addMessage(message);
	}
}