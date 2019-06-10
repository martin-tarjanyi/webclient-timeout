package com.example.webclient.timeout;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.util.function.Tuple2;

import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class WebClientTimeout
{
    public static void main(String[] args)
    {
        WebClient webClient = createWebClient();

        Flux.range(1, 5)
            .concatMap(ignored -> callService(webClient))
            .blockLast();
    }

    private static WebClient createWebClient()
    {
        Consumer<Connection> doOnConnectedConsumer = connection -> {
            connection.addHandlerLast(new ReadTimeoutHandler(10, MILLISECONDS))
                      .addHandlerLast(new WriteTimeoutHandler(10, MILLISECONDS));
        };

        TcpClient tcpClient = TcpClient.create()
                                       .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 300)
                                       .doOnConnected(doOnConnectedConsumer);

        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.from(tcpClient))).build();
    }

    private static Mono<Tuple2<Long, String>> callService(WebClient webClient)
    {
        return webClient.get()
                        .uri("https://httpstat.us")
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnError(Throwable::printStackTrace)
                        .onErrorResume(a -> Mono.just("add fallback to measure time with elapsed operator"))
                        .elapsed()
                        .doOnNext(t -> System.out.println("Duration: " + t.getT1()));
    }
}
