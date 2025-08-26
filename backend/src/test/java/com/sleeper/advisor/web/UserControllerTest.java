package com.sleeper.advisor.web;

import com.sleeper.advisor.service.SleeperClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SleeperClient sleeperClient;

    @Test
    void leagues_endpoint_returns_stripped_json() throws Exception {
        String username = "testuser";
        String userId = "uid123";
        Mockito.when(sleeperClient.getUserId(username))
                .thenReturn(Map.of("user_id", userId));
        Mockito.when(sleeperClient.getUserLeagues(Mockito.eq(userId), Mockito.anyString()))
                .thenReturn(List.of(
                        Map.of("league_id", "l1", "name", "League 1", "extra", "foo"),
                        Map.of("league_id", "l2", "name", "League 2", "extra", "bar")
                ));

        mockMvc.perform(get("/api/user/{username}/leagues", username).param("season", "2121"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagues[0].leagueId").value("l1"))
                .andExpect(jsonPath("$.leagues[0].name").value("League 1"))
                .andExpect(jsonPath("$.leagues[1].leagueId").value("l2"))
                .andExpect(jsonPath("$.leagues[1].name").value("League 2"));
    }
}
