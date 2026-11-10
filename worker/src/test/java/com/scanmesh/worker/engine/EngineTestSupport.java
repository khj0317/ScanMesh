package com.scanmesh.worker.engine;

import com.scanmesh.common.scan.Engine;
import com.scanmesh.worker.api.ClaimedTask;
import com.scanmesh.worker.engine.http.TargetFetcher;
import com.scanmesh.worker.engine.http.TargetFetcherFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** 엔진 테스트에서 함께 쓰는 설정과 가짜 객체 */
public final class EngineTestSupport {

    private EngineTestSupport() {
    }

    public static ClaimedTask task(Engine engine) {
        return new ClaimedTask(7L, 1L, engine, "https://example.com/", "example.com", 2, 90, 30);
    }

    public static EngineProperties properties(Path resultsDir, Duration probeInterval, int probeFailures) {
        return new EngineProperties(resultsDir, "", "nuclei:test", "templates-volume", Duration.ofMinutes(20), "zap:test", Duration.ofMinutes(15), probeInterval, probeFailures);
    }

    public static TargetFetcher.Response ok(String body) {
        return new TargetFetcher.Response(200, Map.of(), body, null);
    }

    /** 요청한 주소를 기록하고, 정해 둔 함수로 응답하는 팩토리 */
    public static final class FakeFetchers extends TargetFetcherFactory {

        public final List<String> created = new CopyOnWriteArrayList<>();
        public final List<String> requested = new CopyOnWriteArrayList<>();
        private final Function<URI, TargetFetcher.Response> responder;

        /** @param responder null을 돌려주면 연결 실패로 처리한다 */
        public FakeFetchers(Function<URI, TargetFetcher.Response> responder) {
            this.responder = responder;
        }

        @Override
        public TargetFetcher create(String host, String pinnedAddress) {
            created.add(host + "@" + pinnedAddress);
            return new TargetFetcher() {
                @Override
                public Response get(URI uri) throws IOException {
                    requested.add(uri.toString());
                    Response response = responder.apply(uri);
                    if (response == null) {
                        throw new IOException("연결 실패");
                    }
                    return response;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    public static final class FakeContext implements TaskContext {

        public volatile int progress;
        public volatile boolean cancelled;
        private final String pinned;

        public FakeContext(String pinned) {
            this.pinned = pinned;
        }

        @Override
        public void reportProgress(int value) {
            progress = value;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public Optional<String> pinnedAddress() {
            return Optional.ofNullable(pinned);
        }
    }
}
