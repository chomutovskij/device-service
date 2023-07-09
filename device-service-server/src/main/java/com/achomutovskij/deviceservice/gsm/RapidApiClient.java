/*
 * (c) Copyright 2023 Andrej Chomutovskij. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.achomutovskij.deviceservice.gsm;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

// Uses API from https://rapidapi.com/makingdatameaningful/api/mobile-phone-specs-database
public final class RapidApiClient {

    private static final SafeLogger log = SafeLoggerFactory.get(RapidApiClient.class);

    private final OkHttpClient okHttpClient;
    private final String rapidApiKey;
    private final LoadingCache<String, Optional<GsmNetworkDetails>> deviceToApiLookupResultCache;

    public RapidApiClient(OkHttpClient okHttpClient, String rapidApiKey) {
        this.okHttpClient = Preconditions.checkNotNull(okHttpClient, "OkHttpClient needs to be non-null");
        this.rapidApiKey = Preconditions.checkNotNull(rapidApiKey, "API key must be non-null");
        this.deviceToApiLookupResultCache =
                Caffeine.newBuilder().maximumSize(1_000).build(this::performNetworkRequest);
    }

    public Optional<GsmNetworkDetails> lookup(String deviceName) {
        return deviceToApiLookupResultCache.get(deviceName);
    }

    private Optional<GsmNetworkDetails> performNetworkRequest(String deviceName) {

        String[] words = deviceName.split("\\s+", 2); // split the string at the first whitespace
        if (words.length < 2) {
            return Optional.empty();
        }
        String brand = words[0];
        String model = words[1];
        String encodedModel = model.replace(" ", "%20");

        String url = String.format(
                "https://mobile-phone-specs-database.p.rapidapi"
                        + ".com/gsm/get-specifications-by-brandname-modelname/%s/%s",
                brand, encodedModel);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-RapidAPI-Key", rapidApiKey)
                .addHeader("X-RapidAPI-Host", "mobile-phone-specs-database.p.rapidapi.com")
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();

            if (responseBody == null) {
                return Optional.empty();
            }

            String json = responseBody.string();
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

            Optional<JsonObject> gsmNetworkDetailsOptional =
                    Optional.ofNullable(jsonObject.get("gsmNetworkDetails")).map(JsonElement::getAsJsonObject);

            return gsmNetworkDetailsOptional.map(gsmNetworkDetails -> ImmutableGsmNetworkDetails.builder()
                    .technology(getStringWithKey(gsmNetworkDetails, "networkTechnology"))
                    .twoGBands(getStringWithKey(gsmNetworkDetails, "network2GBands"))
                    .threeGBands(getStringWithKey(gsmNetworkDetails, "network3GBands"))
                    .fourGBands(getStringWithKey(gsmNetworkDetails, "network4GBands"))
                    .build());
        } catch (IOException | RuntimeException e) {
            log.error("Failed to get or parse the response from Rapid API", e);
            return Optional.empty();
        }
    }

    private static String getStringWithKey(JsonObject gsmNetworkDetails, String key) {
        return Optional.ofNullable(gsmNetworkDetails.get(key))
                .map(JsonElement::getAsString)
                .orElse("");
    }
}
