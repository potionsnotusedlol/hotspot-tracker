package ru.moscow.hotspot_tracker.main.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.moscow.hotspot_tracker.main.web.port.Role;
import ru.moscow.hotspot_tracker.main.web.port.UserAccountService.UserAccount;

/**
 * Registration, profile and chat widget (FR-AUTH-01 on the web, FR-WEB-02, FR-WEB-05).
 *
 * <p>Contract: registration checks the email, a password of 8 to 128 characters and the repeat before calling
 * {@link ru.moscow.hotspot_tracker.main.web.port.UserAccountService}; the password is never sent back; the chat
 * accepts 1 to 500 characters and shows the question and the answer escaped.
 */
class AccountPagesTest extends WebLayerTestSupport {

    private static final String PASSWORD = "correct-horse-1";

    @Test
    void validRegistrationRedirectsToLogin() throws Exception {
        when(users.register("new@example.com", PASSWORD)).thenReturn(new UserAccount(9, "new@example.com", Role.USER));

        mvc.perform(register("  new@example.com ", PASSWORD, PASSWORD))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attributeExists("message"));

        verify(users).register("new@example.com", PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "no-domain@", "@example.com", "two@@example.com", "a b@example.com"})
    void invalidEmailIsRejectedBeforeTheService(String email) throws Exception {
        mvc.perform(register(email, PASSWORD, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("error"));

        verify(users, never()).register(anyString(), anyString());
    }

    @Test
    void tooLongEmailIsRejected() throws Exception {
        String email = "a".repeat(250) + "@example.com";

        mvc.perform(register(email, PASSWORD, PASSWORD)).andExpect(model().attributeExists("error"));

        verify(users, never()).register(anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"7, false", "8, true", "128, true", "129, false"})
    void passwordLengthMustBeEightToOneHundredTwentyEight(int length, boolean accepted) throws Exception {
        String password = "p".repeat(length);

        mvc.perform(register("new@example.com", password, password));

        if (accepted) {
            verify(users).register("new@example.com", password);
        } else {
            verify(users, never()).register(anyString(), anyString());
        }
    }

    @Test
    void passwordsMustMatch() throws Exception {
        mvc.perform(register("new@example.com", PASSWORD, PASSWORD + "x"))
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeExists("error"))
                .andExpect(model().attribute("email", "new@example.com"));

        verify(users, never()).register(anyString(), anyString());
    }

    @Test
    void passwordIsNotSentBack() throws Exception {
        mvc.perform(register("new@example.com", PASSWORD, "Another-Secret-42"))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("Another-Secret-42"))));
    }

    @Test
    void serviceRefusalIsShownOnTheForm() throws Exception {
        when(users.register("taken@example.com", PASSWORD)).thenThrow(new IllegalArgumentException("email is taken"));

        mvc.perform(register("taken@example.com", PASSWORD, PASSWORD))
                .andExpect(view().name("auth/register"))
                .andExpect(model().attribute("error", "email is taken"))
                .andExpect(content().string(containsString("email is taken")));
    }

    @Test
    void enteredEmailIsEscapedOnTheForm() throws Exception {
        mvc.perform(register("\"><script>x()</script>@a.b", PASSWORD, PASSWORD + "x"))
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(not(containsString("<script>x()</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;x()&lt;/script&gt;")));
    }

    @Test
    void profileShowsEmailAndRoles() throws Exception {
        mvc.perform(get("/profile").with(user("analyst@example.com").roles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("email", "analyst@example.com"))
                .andExpect(model().attribute("roles", "ANALYST"));
    }

    @Test
    void chatPassesTrimmedQuestionInTheNameOfTheUser() throws Exception {
        when(chat.reply("user@example.com", "how many points?")).thenReturn("42");

        mvc.perform(chat("  how many points?  "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("question", "how many points?"))
                .andExpect(model().attribute("answer", "42"))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void chatAcceptsFiveHundredCharacters() throws Exception {
        String message = "q".repeat(500);
        when(chat.reply("user@example.com", message)).thenReturn("ok");

        mvc.perform(chat(message)).andExpect(status().isOk());
    }

    @Test
    void chatRejectsEmptyOrTooLongMessage() throws Exception {
        mvc.perform(chat("   ")).andExpect(status().isBadRequest());
        mvc.perform(chat("q".repeat(501))).andExpect(status().isBadRequest());
        mvc.perform(post("/chat/widget")
                        .with(csrf())
                        .with(user("user@example.com").roles("USER")))
                .andExpect(status().isBadRequest());

        verify(chat, never()).reply(anyString(), anyString());
    }

    @Test
    void chatEscapesQuestionAndAnswer() throws Exception {
        when(chat.reply(anyString(), anyString())).thenReturn("<b onclick=x()>answer</b>");

        mvc.perform(chat("<img src=x onerror=alert(1)>"))
                .andExpect(content().string(not(containsString("<img src=x"))))
                .andExpect(content().string(not(containsString("<b onclick"))))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")));
    }

    private static MockHttpServletRequestBuilder register(String email, String password, String repeat) {
        return post("/register")
                .param("email", email)
                .param("password", password)
                .param("passwordRepeat", repeat)
                .with(csrf());
    }

    private static MockHttpServletRequestBuilder chat(String message) {
        return post("/chat/widget")
                .param("message", message)
                .with(csrf())
                .with(user("user@example.com").roles("USER"));
    }
}
