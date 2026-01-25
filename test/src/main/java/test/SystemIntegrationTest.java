package test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SystemIntegrationTest {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static boolean checkIsRead(long userId, long msgId)throws Exception{
        //1. 拼接地址
        String url = String.format("http://localhost:8080/api/check?userId=%d&msgId=%d", userId, msgId);
        //2. 发送请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return Boolean.parseBoolean(response.body());
    }

    public static String markRead(long userId, long msgId)throws Exception{
        //1. 拼接地址
        String url = String.format("http://localhost:8080/api/mark?userId=%d&msgId=%d", userId, msgId);
        //2. 发送请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
