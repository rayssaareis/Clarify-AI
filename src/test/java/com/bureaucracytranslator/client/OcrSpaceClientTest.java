package com.bureaucracytranslator.client;

import com.bureaucracytranslator.config.OcrSpaceProperties;
import com.bureaucracytranslator.exception.OcrServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the real internal logic of OcrSpaceClient (request building, multipart
 * file part, response parsing, error branches) with no network access.
 * The RestClient.Builder is bound to a MockRestServiceServer, so no production
 * code change is needed. No environment variables are required.
 */
class OcrSpaceClientTest {

    private static final String OCR_URL = "https://api.ocr.space/parse/image";
    private static final String API_KEY = "test-ocr-key";

    private static final String VALID_OCR_RESPONSE =
            "{\"ParsedResults\":[{\"ParsedText\":\"Comparecer a audiencia no dia 15/10.\","
                    + "\"ErrorMessage\":\"\",\"ErrorDetails\":\"\"}],"
                    + "\"OCRExitCode\":1,\"IsErroredOnProcessing\":false}";

    private MockRestServiceServer server;
    private OcrSpaceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OcrSpaceClient(
                builder,
                new OcrSpaceProperties(API_KEY),
                Jackson2ObjectMapperBuilder.json().build());
    }

    // ---------- helpers ----------

    private static MockMultipartFile image(String filename, String contentType) {
        return new MockMultipartFile(
                "image", filename, contentType, "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
    }

    private static String bodyOf(ClientHttpRequest request) {
        return ((MockClientHttpRequest) request).getBodyAsString();
    }

    // ---------- valid response ----------

    @Test
    void extractText_validResponse_returnsParsedText() {
        server.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(VALID_OCR_RESPONSE, MediaType.APPLICATION_JSON));

        String text = client.extractText(image("doc.jpg", "image/jpeg"), "req-1");

        assertThat(text).isEqualTo("Comparecer a audiencia no dia 15/10.");
        server.verify();
    }

    @Test
    void extractText_emptyParsedText_isReturnedAsIs_serviceLayerDecidesWhatToDo() {
        server.expect(requestTo(OCR_URL))
                .andRespond(withSuccess(
                        "{\"ParsedResults\":[{\"ParsedText\":\"\"}],\"IsErroredOnProcessing\":false}",
                        MediaType.APPLICATION_JSON));

        String text = client.extractText(image("doc.jpg", "image/jpeg"), "req-2");

        assertThat(text).isEmpty();
    }

    // ---------- request shape / multipart ----------

    @Test
    void extractText_sendsMultipartWithExpectedFields() {
        server.expect(requestTo(OCR_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = bodyOf(request);
                    assertThat(body).contains("name=\"apikey\"").contains(API_KEY);
                    assertThat(body).contains("name=\"language\"").contains("por");
                    assertThat(body).contains("name=\"isOverlayRequired\"").contains("false");
                    assertThat(body).contains("name=\"file\"")
                            .contains("filename=\"doc.jpg\"")
                            .contains("fake-image-bytes");
                })
                .andRespond(withSuccess(VALID_OCR_RESPONSE, MediaType.APPLICATION_JSON));

        client.extractText(image("doc.jpg", "image/jpeg"), "req-3");

        server.verify();
    }

    /**
     * Regression test for the E501 "Invalid file signature" investigation:
     * the file part must carry the ORIGINAL Content-Type of the upload, not one
     * guessed from the filename. The filename extension here deliberately
     * disagrees with the declared type (".dat" would be guessed as octet-stream).
     */
    @Test
    void extractText_filePartKeepsOriginalContentType_notInferredFromFilename() {
        server.expect(requestTo(OCR_URL))
                .andExpect(request -> {
                    String body = bodyOf(request);
                    String filePart = body.substring(body.indexOf("name=\"file\""));
                    assertThat(filePart).containsPattern("(?i)content-type:\\s*image/png");
                    assertThat(filePart).doesNotContainIgnoringCase("application/octet-stream");
                })
                .andRespond(withSuccess(VALID_OCR_RESPONSE, MediaType.APPLICATION_JSON));

        client.extractText(image("scan.dat", "image/png"), "req-4");

        server.verify();
    }

    @Test
    void extractText_blankOriginalFilename_fallsBackToDocument() {
        server.expect(requestTo(OCR_URL))
                .andExpect(request ->
                        assertThat(bodyOf(request)).contains("filename=\"document\""))
                .andRespond(withSuccess(VALID_OCR_RESPONSE, MediaType.APPLICATION_JSON));

        client.extractText(image("", "image/png"), "req-5");

        server.verify();
    }

    @Test
    void extractText_whenUploadCannotBeRead_throwsOcrServiceException() throws Exception {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getContentType()).thenReturn("image/jpeg");
        when(broken.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> client.extractText(broken, "req-6"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("read uploaded image");
    }

    // ---------- malformed / unusable OCR.space responses ----------

    @Test
    void extractText_whenOcrReportsProcessingError_throwsOcrServiceException() {
        server.expect(requestTo(OCR_URL))
                .andRespond(withSuccess(
                        "{\"IsErroredOnProcessing\":true,\"ErrorMessage\":[\"File failed validation\"]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-7"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("processing error");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"IsErroredOnProcessing\":false,\"OCRExitCode\":1}",
            "{\"ParsedResults\":[]}",
            "{\"ParsedResults\":[{\"ParsedText\":null}]}",
            "{\"ParsedResults\":[{\"ParsedText\":123}]}",
            "{\"ParsedResults\":[{}]}"
    })
    void extractText_whenParsedTextIsMissingOrNotText_throwsOcrServiceException(String responseBody) {
        server.expect(requestTo(OCR_URL))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-8"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("missing expected content");
    }

    @Test
    void extractText_whenBodyIsNotJson_throwsOcrServiceException() {
        server.expect(requestTo(OCR_URL))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-9"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void extractText_whenSuccessResponseHasEmptyBody_throwsOcrServiceException() {
        server.expect(requestTo(OCR_URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-10"))
                .isInstanceOf(OcrServiceException.class);
    }

    // ---------- HTTP / network failures ----------

    @Test
    void extractText_whenOcrReturnsHttp400_throwsOcrServiceExceptionWithResponseCause() {
        // Exact body observed in the real E501 failure.
        server.expect(requestTo(OCR_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"E501: Not an image or PDF\",\"details\":\"Invalid file signature.\"}"));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-11"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("call failed")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void extractText_whenOcrReturnsHttp500_throwsOcrServiceException() {
        server.expect(requestTo(OCR_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-12"))
                .isInstanceOf(OcrServiceException.class)
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void extractText_whenNetworkFails_throwsOcrServiceExceptionWithResourceAccessCause() {
        server.expect(requestTo(OCR_URL))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.extractText(image("doc.jpg", "image/jpeg"), "req-13"))
                .isInstanceOf(OcrServiceException.class)
                .hasMessageContaining("call failed")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }
}
