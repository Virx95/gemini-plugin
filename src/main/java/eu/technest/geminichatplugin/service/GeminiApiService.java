package eu.technest.geminichatplugin.service; // Adjust package

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger; // IntelliJ Logger
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor; // OkHttp logging interceptor
import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static eu.technest.geminichatplugin.settings.GeminiSettingsService.DEFAULT_MODEL_ID;

@Service
public final class GeminiApiService {
    private static final Logger LOG = Logger.getInstance(GeminiApiService.class); // Logger instance

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();
    private static final String GEMINI_MODELS_API_URL = "https://generativelanguage.googleapis.com/v1beta/models?key=%s";
    private static final String GEMINI_GENERATE_CONTENT_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    public GeminiApiService() {
        // Setup HttpLoggingInterceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message -> LOG.debug(message)); // Route OkHttp logs to IntelliJ LOG.debug
        // Set logging level (BODY will log request and response bodies - useful for debugging,
        // but can be verbose and might expose sensitive data in logs if not careful)
        // For production, you might use HEADERS or BASIC.
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY); // Or .HEADERS for less verbosity

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor) // Add the logging interceptor
                .build();
        LOG.info("GeminiApiService initialized with HTTP logging.");
    }

    public interface GeminiApiResponseCallback {
        void onSuccess(String geminiResponse, JsonObject modelContent);
        void onFailure(String errorMessage, String detailedError);
    }

    public interface ListModelsCallback {
        void onSuccess(List<String> modelIds);
        void onFailure(String errorMessage);
    }

    public void listModels(String apiKey, ListModelsCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            LOG.warn("listModels called without API key.");
            callback.onFailure("API Key is missing.");
            return;
        }
        LOG.info("Attempting to list models from Gemini API.");

        Request request = new Request.Builder()
                .url(String.format(GEMINI_MODELS_API_URL, apiKey))
                .get()
                .build();

        // Asynchronous execution for network calls from UI context (like settings dialog)
        // However, listModels is called from a Task.Backgroundable, so direct execution is fine here.
        // If it were called directly from an EDT event handler without Task.Backgroundable,
        // you'd use httpClient.newCall(request).enqueue(...)
        try (Response response = httpClient.newCall(request).execute()) { // Blocking call (OK inside Task.Backgroundable)
            String responseBody = response.body() != null ? response.body().string() : null;

            if (!response.isSuccessful() || responseBody == null) {
                String errorMsg = "Error fetching models: " + response.code() + (responseBody != null ? " - " + responseBody : "");
                LOG.warn(errorMsg);
                callback.onFailure(errorMsg);
                return;
            }

            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray modelsArray = jsonResponse.getAsJsonArray("models");
            List<String> modelIds = new ArrayList<>();
            if (modelsArray != null) {
                for (JsonElement modelElement : modelsArray) {
                    JsonObject modelObject = modelElement.getAsJsonObject();
                    JsonArray methods = modelObject.getAsJsonArray("supportedGenerationMethods");
                    boolean supportsGenerateContent = false;
                    if (methods != null) {
                        for (JsonElement method : methods) {
                            if ("generateContent".equals(method.getAsString())) {
                                supportsGenerateContent = true;
                                break;
                            }
                        }
                    }
                    String modelName = modelObject.get("name").getAsString();
                    if (supportsGenerateContent && modelName.startsWith("models/")) {
                        modelIds.add(modelName.substring("models/".length()));
                    }
                }
            }
            Collections.sort(modelIds);
            LOG.info("Found " + modelIds.size() + " usable models: " + modelIds);
            callback.onSuccess(modelIds);

        } catch (IOException e) {
            LOG.error("Network error while fetching models: ", e);
            callback.onFailure("Network error while fetching models: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error parsing models response: ", e);
            callback.onFailure("Error parsing models response: " + e.getMessage());
        }
    }

    public void generateContent(String apiKey, String modelId, String userInput, List<JsonObject> conversationHistory, GeminiApiResponseCallback callback) {
        LOG.info("Generating content with model: " + modelId);
        // ... (payload creation)
        JsonArray contentsArray = new JsonArray();
        conversationHistory.forEach(contentsArray::add);
        JsonObject userMessagePart = new JsonObject();
        userMessagePart.addProperty("text", userInput);
        JsonArray userPartsArray = new JsonArray();
        userPartsArray.add(userMessagePart);
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        userContent.add("parts", userPartsArray);
        contentsArray.add(userContent);
        JsonObject payload = new JsonObject();
        payload.add("contents", contentsArray);

        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.get("application/json; charset=utf-8")
        );

        String effectiveModelId = (modelId == null || modelId.trim().isEmpty()) ? DEFAULT_MODEL_ID : modelId;
        Request request = new Request.Builder()
                .url(String.format(GEMINI_GENERATE_CONTENT_URL_TEMPLATE, effectiveModelId, apiKey))
                .post(body)
                .build();

        // This method is called from a background thread in GeminiChatPanel, so direct execute is fine.
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : null;

            if (!response.isSuccessful() || responseBody == null) {
                String errorMsg = "Error generating content: " + response.code();
                String detailedError = responseBody;
                LOG.warn(errorMsg + (detailedError != null ? " - Body: " + detailedError : " - No response body"));
                try {
                    if (responseBody != null) {
                        JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                        if (errorJson.has("error") && errorJson.getAsJsonObject("error").has("message")) {
                            detailedError = errorJson.getAsJsonObject("error").get("message").getAsString();
                        }
                    }
                } catch (Exception parseEx) { LOG.debug("Could not parse error response body as JSON.", parseEx); }
                callback.onFailure(errorMsg, detailedError);
                return;
            }
            LOG.debug("Successfully received content generation response.");
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");

            if (candidates != null && !candidates.isEmpty()) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                if (content != null && content.has("parts")) {
                    JsonArray parts = content.getAsJsonArray("parts");
                    if (parts != null && !parts.isEmpty() && parts.get(0).getAsJsonObject().has("text")) {
                        String geminiText = parts.get(0).getAsJsonObject().get("text").getAsString();
                        callback.onSuccess(geminiText, content);
                    } else {
                        LOG.warn("API Error: No text part in response content. Body: " + responseBody);
                        callback.onFailure("API Error: No text part in response content.", responseBody);
                    }
                } else {
                    LOG.warn("API Error: No content or parts in candidate. Body: " + responseBody);
                    callback.onFailure("API Error: No content or parts in candidate.", responseBody);
                }
            } else if (jsonResponse.has("promptFeedback")) {
                JsonObject feedback = jsonResponse.getAsJsonObject("promptFeedback");
                String blockReason = feedback.has("blockReason") ? feedback.get("blockReason").getAsString() : "Unknown reason";
                LOG.warn("Request Blocked by API: " + blockReason + ". Body: " + responseBody);
                callback.onFailure("Request Blocked by API: " + blockReason, responseBody);
            }
            else {
                LOG.warn("API Error: No candidates in response. Body: " + responseBody);
                callback.onFailure("API Error: No candidates in response.", responseBody);
            }

        } catch (IOException e) {
            LOG.error("Network error during content generation: ", e);
            callback.onFailure("Network Error: " + e.getMessage(), e.toString());
        } catch (Exception e) {
            LOG.error("Internal error during content generation: ", e);
            callback.onFailure("Internal Processing Error: " + e.getMessage(), e.toString());
        }
    }
}