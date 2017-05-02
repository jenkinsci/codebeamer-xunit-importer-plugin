/*
 * Copyright (c) 2017 Intland Software (support@intland.com)
 *
 * Additional information can be found here: https://codebeamer.com/cb/project/1025
 * If you find any bugs please use the Tracker page to report them: https://codebeamer.com/cb/project/1025/tracker
 */
package com.intland.jenkins.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intland.jenkins.XUnitUtil;
import com.intland.jenkins.api.dto.*;
import com.intland.jenkins.api.dto.trackerschema.TrackerSchemaDto;
import com.intland.jenkins.dto.PluginConfiguration;
import hudson.model.BuildListener;
import jcifs.util.Base64;
import org.apache.commons.io.Charsets;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;

/**
 * @author mgansler
 */
public class RestAdapter {
    private static final String PATH_REST = "/rest";
    private static final String PATH_VERSION = "/version";

    public static final int PAGESIZE = 500;

    private String baseUrl;
    private BuildListener listener;
    private PluginConfiguration pluginConfiguration;

    private HttpClient client;
    private RequestConfig requestConfig;
    private ObjectMapper objectMapper;

    public RestAdapter(PluginConfiguration pluginConfiguration, int timeout, BuildListener listener) {
        this.baseUrl = pluginConfiguration.getUri() + "/rest";
        this.listener = listener;
        this.pluginConfiguration = pluginConfiguration;


        // http://stackoverflow.com/questions/9539141/httpclient-sends-out-two-requests-when-using-basic-auth
        final String username = pluginConfiguration.getUsername();
        final String password = pluginConfiguration.getPassword();
        final String authHeader = "Basic " + Base64.encode((username + ":" + password).getBytes(Charsets.UTF_8));

        HashSet<Header> defaultHeaders = new HashSet<Header>();
        defaultHeaders.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));

        this.client = HttpClientBuilder
                .create()
                .setDefaultHeaders(defaultHeaders)
                .build();
        this.requestConfig = RequestConfig
                .custom()
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout * 4)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String getVersion() throws IOException {
        return executeGet(baseUrl + "/version").replace("\"", "");
    }

    public TrackerSchemaDto getTestCaseTrackerSchema() throws IOException {
        String json = executeGet(baseUrl + String.format("/tracker/%s/schema", this.pluginConfiguration.getTestCaseTrackerId()));
        return objectMapper.readValue(json, TrackerSchemaDto.class);
    }

    public PagedTrackerItemsDto getPagedTrackerItemForName(String name) throws IOException {
        String cbQl = XUnitUtil.encodeParam(String.format("tracker.id IN ('%s') AND workItemStatus in ('Unset','InProgress') AND summary like '%s'", pluginConfiguration.getBugTrackerId(), name));
        String json = executeGet(baseUrl + String.format("/query/page/1?queryString=%s&pagesize=1", cbQl));
        return objectMapper.readValue(json, PagedTrackerItemsDto.class);
    }

    public PagedTrackerItemsDto getPagedTrackerItemsForName(Integer trackerId, String name) throws IOException {
        String json = executeGet(baseUrl + String.format("/tracker/%s/items/or/name=%s/page/1", trackerId, XUnitUtil.encodeParam(name)));
        return objectMapper.readValue(json, PagedTrackerItemsDto.class);
    }

    public PagedTrackerItemsDto getTrackerItems(Integer trackerId, int page) throws IOException {
        String json = executeGet(baseUrl + String.format("/tracker/%s/items/page/%s?pagesize=%s", trackerId, page, PAGESIZE));
        return objectMapper.readValue(json, PagedTrackerItemsDto.class);
    }

    public TrackerItemDto getTrackerItem(Integer itemId) throws IOException {
        String json = executeGet(baseUrl + String.format("/item/%s", itemId));
        return objectMapper.readValue(json, TrackerItemDto.class);
    }

    public TrackerDto getTrackerType(Integer trackerId) throws IOException {
        String json = executeGet(baseUrl + String.format("/tracker/%s", trackerId));
        return objectMapper.readValue(json, TrackerDto.class);
    }

    public RepositoryDto getRepositoryUrl(String name, String type) throws IOException {
        String json = executeGet(baseUrl + String.format("/%s/%s", type, name));
        return objectMapper.readValue(json, RepositoryDto.class);
    }

    private String executeGet(String uri) throws IOException {
        HttpGet get = new HttpGet(uri);
        get.setConfig(requestConfig);

        try {
            HttpResponse response = client.execute(get);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return new BasicResponseHandler().handleResponse(response);
            } else if (listener != null) { //listener is null when job is edited
                InputStream responseStream = response.getEntity().getContent();
                String warn = XUnitUtil.getStringFromInputStream(responseStream);
                XUnitUtil.log(listener, String.format("WARNING (GET): %s, statusCode: %s, url: %s", warn, statusCode, uri));
            }
            throw new IOException(String.format("get returned with statusCode: %s", statusCode));
        } finally {
            get.releaseConnection();
        }
    }

    public TrackerItemDto postTrackerItem(TestRunDto testRunDto) throws IOException {
        String content = objectMapper.writeValueAsString(testRunDto);
        String response = executePost(baseUrl + "/item", content);
        return objectMapper.readValue(response, TrackerItemDto.class);
    }

    public TrackerItemDto[] postTrackerItems(List<TestRunDto> testRunDtos) throws IOException {
        if (testRunDtos.size() > 1) {
            String content = objectMapper.writeValueAsString(testRunDtos);
            String response = executePost(baseUrl + "/items", content);
            return objectMapper.readValue(response, TrackerItemDto[].class);
        } else {
            return new TrackerItemDto[] {postTrackerItem(testRunDtos.get(0))};
        }
    }

    private String executePost(String uri, String content) throws IOException {
        StringEntity entity = new StringEntity(content, Charsets.UTF_8);
        entity.setContentType("application/json");

        HttpPost post = new HttpPost(uri);
        post.setConfig(requestConfig);
        post.setEntity(entity);

        try {
            HttpResponse response = client.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_CREATED) {
                return new BasicResponseHandler().handleResponse(response);
            } else if (listener != null) {
                InputStream responseStream = response.getEntity().getContent();
                String error = XUnitUtil.getStringFromInputStream(responseStream);
                XUnitUtil.log(listener, String.format("ERROR (POST): %s, content: %s", error, content));
            }
            throw new IOException(String.format("post returned with statusCode: %s", statusCode));
        } finally {
            post.releaseConnection();
        }
    }

    public TrackerItemDto updateTrackerItem(TrackerItemDto trackerItemDto) throws IOException {
        String content = objectMapper.writeValueAsString(trackerItemDto);
        String response = executePut(baseUrl + "/item", content);
        return objectMapper.readValue(response, TrackerItemDto.class);
    }

    public TrackerItemDto updateTestCaseItem(TestCaseDto testCaseDto) throws IOException {
        String content = objectMapper.writeValueAsString(testCaseDto);
        String response = executePut(baseUrl + "/item", content);
        return objectMapper.readValue(response, TrackerItemDto.class);
    }

    public TrackerItemDto updateTestCaseItems(List<TestCaseDto> testCaseDtos) throws IOException {
        String content = objectMapper.writeValueAsString(testCaseDtos);
        String response = executePut(baseUrl + "/item", content);
        return objectMapper.readValue(response, TrackerItemDto.class);
    }

    private String executePut(String uri, String content) throws IOException {
        StringEntity entity = new StringEntity(content, Charsets.UTF_8);
        entity.setContentType("application/json");

        HttpPut put = new HttpPut(uri);
        put.setConfig(requestConfig);
        put.setEntity(entity);

        try {
            HttpResponse response = client.execute(put);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return new BasicResponseHandler().handleResponse(response);
            } else if (listener != null) {
                InputStream responseStream = response.getEntity().getContent();
                String warning = XUnitUtil.getStringFromInputStream(responseStream);
                XUnitUtil.log(listener, String.format("WARNING (PUT): %s, statusCode: %s, content: %s", warning, statusCode, content));
            }
            throw new IOException(String.format("put returned with statusCode %s", statusCode));
        } finally {
            put.releaseConnection();
        }
    }
}
