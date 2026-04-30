package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testDownloadMedia() throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/media/"))
            .andExpect(MockMvcResultMatchers.request().asyncStarted())
            .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
            .andExpect(MockMvcResultMatchers.status().isPartialContent())
            .andExpect(MockMvcResultMatchers.header().exists("Content-Disposition"))
            .andExpect(MockMvcResultMatchers.header().exists("Content-Range"));
    }
}
