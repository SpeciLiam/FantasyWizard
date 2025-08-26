package com.sleeper.advisor.web;

import com.sleeper.advisor.model.LeagueUser;
import com.sleeper.advisor.service.SleeperClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeagueController.class)
class LeagueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SleeperClient sleeperClient;

    @Test
    void league_members_maps_to_leagueuser_contract() throws Exception {
        String leagueId = "lid-42";
        String username = "SpeciLiam";
        Mockito.when(sleeperClient.getLeagueMembers(leagueId))
                .thenReturn(List.of(
                        Map.of("user_id", "u1", "display_name", "SpeciLiam", "avatar", "a1"),
                        Map.of("user_id", "u2", "display_name", "AnotherGuy", "avatar", "a2")
                ));

        mockMvc.perform(get("/api/league/{leagueId}/members", leagueId).param("username", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("u1"))
                .andExpect(jsonPath("$[0].displayName").value("SpeciLiam"))
                .andExpect(jsonPath("$[0].avatar").value("a1"))
                .andExpect(jsonPath("$[0].isMe").value(true))
                .andExpect(jsonPath("$[1].userId").value("u2"))
                .andExpect(jsonPath("$[1].isMe").value(false));
    }
}
