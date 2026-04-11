package com.sashplatonov.earnit.kids.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDataServiceTest {

    @Test
    void loadsBundledBaseDataResource() {
        BaseDataService service = new BaseDataService(new ObjectMapper());

        Map<String, Object> data = service.getBaseData();

        assertThat(data).containsKeys("tasks", "products");
        assertThat((List<?>) data.get("tasks")).isNotEmpty();
        assertThat((List<?>) data.get("products")).isNotEmpty();
    }
}
