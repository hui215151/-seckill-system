import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 秒杀接口压测器（JDK 11 单文件，无需额外依赖）。
 *
 * 用法：
 *   java benchmark/LoadTest.java [baseUrl] [goodsId] [threads] [seconds] [baseUserId]
 *
 * 示例：
 *   java benchmark/LoadTest.java http://localhost:8080 1 200 15
 *
 * 说明：
 * - 每个请求使用不同的 userId，避免被防重逻辑拦截；
 * - 统计 200 成功、业务拒绝（售罄/限流）、其它错误；
 * - 输出总请求数、TPS、P50/P95/P99 延迟；
 * - 压测前请把目标商品库存调大（见 benchmark/README.md），否则库存耗尽后测到的是失败路径。
 */
public class LoadTest {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        long goodsId = args.length > 1 ? Long.parseLong(args[1]) : 1L;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 200;
        int seconds = args.length > 3 ? Integer.parseInt(args[3]) : 15;
        long baseUserId = args.length > 4 ? Long.parseLong(args[4]) : System.currentTimeMillis() * 1000L;

        String url = baseUrl + "/api/seckill/do?goodsId=" + goodsId + "&userId=";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicLong ok = new AtomicLong();
        AtomicLong rejected = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        AtomicLong sequence = new AtomicLong();
        AtomicLong firstErrorLogged = new AtomicLong();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        List<List<Long>> latenciesByThread = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            List<Long> local = new ArrayList<>(4096);
            latenciesByThread.add(local);
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    while (System.nanoTime() < deadline) {
                        long userId = baseUserId + sequence.incrementAndGet();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url + userId))
                                .timeout(Duration.ofSeconds(10))
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                        long begin = System.nanoTime();
                        try {
                            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                            long costMs = (System.nanoTime() - begin) / 1_000_000L;
                            local.add(costMs);
                            String body = response.body();
                            boolean errorBody = body != null && body.contains("\"code\"");
                            if (response.statusCode() == 200 && !errorBody) {
                                ok.incrementAndGet();
                            } else if (response.statusCode() == 200 && errorBody) {
                                // 业务拒绝：售罄 / 限流 / 重复下单等，统一计入 rejected
                                rejected.incrementAndGet();
                            } else {
                                errors.incrementAndGet();
                                if (firstErrorLogged.compareAndSet(0, 1)) {
                                    System.out.println("[first error] HTTP " + response.statusCode() + " -> " + body);
                                }
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                            if (firstErrorLogged.compareAndSet(0, 1)) {
                                System.out.println("[first error] " + e);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long wallStart = System.nanoTime();
        start.countDown();
        done.await();
        double wallSeconds = (System.nanoTime() - wallStart) / 1_000_000_000.0;
        pool.shutdown();

        long[] all = latenciesByThread.stream().flatMapToLong(list -> list.stream().mapToLong(Long::longValue)).toArray();
        Arrays.sort(all);
        long total = all.length;

        System.out.println();
        System.out.printf("threads=%d seconds=%.2f%n", threads, wallSeconds);
        System.out.printf("total=%d success=%d rejected=%d error=%d%n",
                total, ok.get(), rejected.get(), errors.get());
        System.out.printf("tps=%.1f%n", total / wallSeconds);
        if (total > 0) {
            System.out.printf("latency_ms: p50=%d p95=%d p99=%d max=%d%n",
                    percentile(all, 50), percentile(all, 95), percentile(all, 99), all[all.length - 1]);
        }
        System.out.println();
        System.out.println("压测后请用 SQL 校验库存与订单数是否一致，见 benchmark/README.md");
    }

    private static long percentile(long[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(sorted.length * p / 100.0) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}

