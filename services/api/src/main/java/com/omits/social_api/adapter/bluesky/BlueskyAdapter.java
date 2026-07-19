package com.omits.social_api.adapter.bluesky;

import com.omits.social_api.adapter.PlatformAdapter;
import com.omits.social_api.adapter.PostMetrics;
import com.omits.social_api.adapter.RemotePost;
import com.omits.social_api.adapter.bluesky.dto.CreateRecordRequest;
import com.omits.social_api.adapter.bluesky.dto.CreateRecordResponse;
import com.omits.social_api.adapter.bluesky.dto.FeedPost;
import com.omits.social_api.adapter.bluesky.dto.GetPostsResponse;
import com.omits.social_api.adapter.bluesky.dto.PostRecord;
import com.omits.social_api.adapter.bluesky.dto.PostView;
import com.omits.social_api.adapter.bluesky.dto.ReplyRef;
import com.omits.social_api.adapter.bluesky.dto.StrongRef;
import com.omits.social_api.adapter.exception.PlatformApiException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

/**
 * AT Protocol / XRPC adapter bound to a single Bluesky account's {@link BlueskySession}.
 * Obtain instances from {@link BlueskyAdapterFactory#forAccount}. Calls block — the server is
 * MVC/Tomcat, so blocking on the outbound WebClient is expected here.
 */
public class BlueskyAdapter implements PlatformAdapter {

    private static final String CREATE_RECORD_PATH = "/xrpc/com.atproto.repo.createRecord";
    private static final String GET_POSTS_PATH = "/xrpc/app.bsky.feed.getPosts";

    private final WebClient webClient;
    private final BlueskySession session;

    BlueskyAdapter(WebClient webClient, BlueskySession session) {
        this.webClient = webClient;
        this.session = session;
    }

    @Override
    public String post(String text) {
        FeedPost record = FeedPost.of(text, Instant.now().toString(), null);
        return createRecord(record).uri();
    }

    @Override
    public String reply(String parentRemoteId, String text) {
        PostView parent = getPost(parentRemoteId);
        StrongRef parentRef = new StrongRef(parent.uri(), parent.cid());
        StrongRef root = threadRoot(parent, parentRef);
        FeedPost record = FeedPost.of(text, Instant.now().toString(), new ReplyRef(root, parentRef));
        return createRecord(record).uri();
    }

    @Override
    public RemotePost read(String remoteId) {
        PostView view = getPost(remoteId);
        PostRecord record = view.record();
        String authorHandle = view.author() != null ? view.author().handle() : null;
        String text = record != null ? record.text() : null;
        Instant createdAt = record != null && record.createdAt() != null
                ? Instant.parse(record.createdAt())
                : null;
        return new RemotePost(view.uri(), authorHandle, text, createdAt);
    }

    @Override
    public PostMetrics fetchMetrics(String remoteId) {
        PostView view = getPost(remoteId);
        return new PostMetrics(view.likeCount(), view.repostCount(), view.replyCount(), view.quoteCount());
    }

    /**
     * The thread root for a reply: if the parent is itself a reply, reuse its root; otherwise the
     * parent is the root.
     */
    private static StrongRef threadRoot(PostView parent, StrongRef parentRef) {
        if (parent.record() != null && parent.record().reply() != null) {
            return parent.record().reply().root();
        }
        return parentRef;
    }

    private CreateRecordResponse createRecord(FeedPost record) {
        CreateRecordRequest request = new CreateRecordRequest(session.did(), FeedPost.TYPE, record);
        CreateRecordResponse response = webClient.post()
                .uri(CREATE_RECORD_PATH)
                .headers(headers -> headers.setBearerAuth(session.accessJwt()))
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> BlueskyErrors.toException("createRecord", resp))
                .bodyToMono(CreateRecordResponse.class)
                .block();
        if (response == null) {
            throw new PlatformApiException("Bluesky createRecord returned an empty response");
        }
        return response;
    }

    private PostView getPost(String remoteId) {
        GetPostsResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path(GET_POSTS_PATH).queryParam("uris", remoteId).build())
                .headers(headers -> headers.setBearerAuth(session.accessJwt()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> BlueskyErrors.toException("getPosts", resp))
                .bodyToMono(GetPostsResponse.class)
                .block();
        if (response == null || response.posts() == null || response.posts().isEmpty()) {
            throw new PlatformApiException("Bluesky getPosts returned no post for " + remoteId);
        }
        return response.posts().get(0);
    }
}
