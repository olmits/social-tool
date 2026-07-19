package com.omits.social_api.adapter.bluesky;

import com.omits.social_api.adapter.PostMetrics;
import com.omits.social_api.adapter.RemotePost;
import com.omits.social_api.adapter.exception.PlatformApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlueskyAdapterTest {

    private MockWebServer server;
    private BlueskyAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.create(server.url("/").toString());
        adapter = new BlueskyAdapter(webClient, new BlueskySession("did:plc:abc", "access-jwt", "refresh-jwt"));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Test
    void postCreatesFeedPostRecordAndReturnsUri() throws InterruptedException {
        server.enqueue(jsonResponse("""
                {"uri":"at://did:plc:abc/app.bsky.feed.post/xyz","cid":"bafycid"}"""));

        String uri = adapter.post("hello world");

        assertThat(uri).isEqualTo("at://did:plc:abc/app.bsky.feed.post/xyz");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/xrpc/com.atproto.repo.createRecord");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-jwt");
        String body = request.getBody().readUtf8();
        assertThat(body)
                .contains("\"repo\":\"did:plc:abc\"")
                .contains("\"collection\":\"app.bsky.feed.post\"")
                .contains("\"$type\":\"app.bsky.feed.post\"")
                .contains("\"text\":\"hello world\"")
                .doesNotContain("reply");
    }

    @Test
    void readParsesPostView() throws InterruptedException {
        server.enqueue(jsonResponse("""
                {"posts":[{
                  "uri":"at://did:plc:abc/app.bsky.feed.post/xyz",
                  "cid":"bafycid",
                  "author":{"did":"did:plc:abc","handle":"author.bsky.social"},
                  "record":{"text":"a post","createdAt":"2026-07-18T10:15:30.000Z"},
                  "likeCount":5,"repostCount":3,"replyCount":2,"quoteCount":1
                }]}"""));

        RemotePost post = adapter.read("at://did:plc:abc/app.bsky.feed.post/xyz");

        assertThat(post.remoteId()).isEqualTo("at://did:plc:abc/app.bsky.feed.post/xyz");
        assertThat(post.authorHandle()).isEqualTo("author.bsky.social");
        assertThat(post.text()).isEqualTo("a post");
        assertThat(post.createdAt()).isEqualTo(Instant.parse("2026-07-18T10:15:30.000Z"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/xrpc/app.bsky.feed.getPosts");
        assertThat(request.getRequestUrl().queryParameter("uris"))
                .isEqualTo("at://did:plc:abc/app.bsky.feed.post/xyz");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-jwt");
    }

    @Test
    void fetchMetricsMapsEngagementCounts() {
        server.enqueue(jsonResponse("""
                {"posts":[{
                  "uri":"at://did:plc:abc/app.bsky.feed.post/xyz",
                  "cid":"bafycid",
                  "author":{"handle":"author.bsky.social"},
                  "record":{"text":"a post","createdAt":"2026-07-18T10:15:30.000Z"},
                  "likeCount":5,"repostCount":3,"replyCount":2,"quoteCount":1
                }]}"""));

        PostMetrics metrics = adapter.fetchMetrics("at://did:plc:abc/app.bsky.feed.post/xyz");

        assertThat(metrics).isEqualTo(new PostMetrics(5, 3, 2, 1));
    }

    @Test
    void replyLooksUpParentThenCreatesReplyWithRootAndParentRefs() throws InterruptedException {
        // Parent lookup (parent is a top-level post, so it becomes the thread root).
        server.enqueue(jsonResponse("""
                {"posts":[{
                  "uri":"at://did:plc:parent/app.bsky.feed.post/p1",
                  "cid":"bafyparent",
                  "author":{"handle":"parent.bsky.social"},
                  "record":{"text":"parent post","createdAt":"2026-07-18T09:00:00.000Z"},
                  "likeCount":0,"repostCount":0,"replyCount":0,"quoteCount":0
                }]}"""));
        // createRecord for the reply.
        server.enqueue(jsonResponse("""
                {"uri":"at://did:plc:abc/app.bsky.feed.post/r1","cid":"bafyreply"}"""));

        String uri = adapter.reply("at://did:plc:parent/app.bsky.feed.post/p1", "a reply");

        assertThat(uri).isEqualTo("at://did:plc:abc/app.bsky.feed.post/r1");

        RecordedRequest lookup = server.takeRequest();
        assertThat(lookup.getRequestUrl().encodedPath()).isEqualTo("/xrpc/app.bsky.feed.getPosts");

        RecordedRequest create = server.takeRequest();
        assertThat(create.getPath()).isEqualTo("/xrpc/com.atproto.repo.createRecord");
        String body = create.getBody().readUtf8();
        assertThat(body)
                .contains("\"text\":\"a reply\"")
                .contains("\"reply\":")
                .contains("\"root\":{\"uri\":\"at://did:plc:parent/app.bsky.feed.post/p1\",\"cid\":\"bafyparent\"}")
                .contains("\"parent\":{\"uri\":\"at://did:plc:parent/app.bsky.feed.post/p1\",\"cid\":\"bafyparent\"}");
    }

    @Test
    void throwsPlatformApiExceptionOnErrorResponse() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"InvalidRequest\",\"message\":\"bad post\"}"));

        assertThatThrownBy(() -> adapter.post("boom"))
                .isInstanceOf(PlatformApiException.class)
                .hasMessageContaining("createRecord")
                .hasMessageContaining("400");
    }
}
