package org.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bot.controller.ConsoleInterface;
import org.bot.db.ORM;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MainTest
{
    @Test
    void tokenIsPresentWhenNotEmpty()
    {
        assertTrue(Main.isTokenPresent("123456:ABC-DEF"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void tokenIsMissingWhenNullOrEmpty(String token)
    {
        assertFalse(Main.isTokenPresent(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1:a", "token-with-dashes", "0123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"})
    void tokenIsPresentForVariousValues(String token)
    {
        assertTrue(Main.isTokenPresent(token));
    }

    @Test
    void createControllerBuildsConsumerForAnyDataBase() throws Exception
    {
        try (ORM dataBase = new ORM("jdbc:sqlite::memory:"))
        {
            ConsoleInterface controller = Main.createController(dataBase, "123456:ABC-DEF");

            assertNotNull(controller);
            assertEquals(ConsoleInterface.class, controller.getClass());
        }
    }
}