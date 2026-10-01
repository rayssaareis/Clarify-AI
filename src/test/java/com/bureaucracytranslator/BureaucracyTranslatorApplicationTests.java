package com.bureaucracytranslator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest
@TestPropertySource(properties = {
        "GEMINI_API_KEY=test-dummy-gemini-key",
        "OCR_SPACE_API_KEY=test-dummy-ocr-key"
})
class BureaucracyTranslatorApplicationTests {

    @Test
    void contextLoads() {
    }

}