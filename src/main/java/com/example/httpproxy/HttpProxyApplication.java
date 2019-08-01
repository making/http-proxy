package com.example.httpproxy;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.ProxyProvider;

import java.security.cert.X509Certificate;

import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.DISABLED;
import static org.springframework.cloud.gateway.config.HttpClientProperties.Pool.PoolType.FIXED;

@SpringBootApplication
public class HttpProxyApplication {

    public static void main(String[] args) {
        if (System.getProperty("reactor.netty.http.server.accessLogEnabled") == null) {
            System.setProperty("reactor.netty.http.server.accessLogEnabled", "true");
        }
        SpringApplication.run(HttpProxyApplication.class, args);
    }

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> customizer() {
        return factory -> factory.addServerCustomizers(httpServer -> httpServer.wiretap(true));
    }

    @Bean
    public HttpClient httpClient(HttpClientProperties properties) {

        // configure pool resources
        HttpClientProperties.Pool pool = properties.getPool();

        ConnectionProvider connectionProvider;
        if (pool.getType() == DISABLED) {
            connectionProvider = ConnectionProvider.newConnection();
        } else if (pool.getType() == FIXED) {
            connectionProvider = ConnectionProvider.fixed(pool.getName(),
                pool.getMaxConnections(), pool.getAcquireTimeout());
        } else {
            connectionProvider = ConnectionProvider.elastic(pool.getName());
        }

        HttpClient httpClient = HttpClient.create(connectionProvider)
            .tcpConfiguration(tcpClient -> {

                if (properties.getConnectTimeout() != null) {
                    tcpClient = tcpClient.option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        properties.getConnectTimeout());
                }

                // configure proxy if proxy host is set.
                HttpClientProperties.Proxy proxy = properties.getProxy();

                if (StringUtils.hasText(proxy.getHost())) {

                    tcpClient = tcpClient.proxy(proxySpec -> {
                        ProxyProvider.Builder builder = proxySpec
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(proxy.getHost());

                        PropertyMapper map = PropertyMapper.get();

                        map.from(proxy::getPort).whenNonNull().to(builder::port);
                        map.from(proxy::getUsername).whenHasText()
                            .to(builder::username);
                        map.from(proxy::getPassword).whenHasText()
                            .to(password -> builder.password(s -> password));
                        map.from(proxy::getNonProxyHostsPattern).whenHasText()
                            .to(builder::nonProxyHosts);
                    });
                }
                return tcpClient;
            });

        HttpClientProperties.Ssl ssl = properties.getSsl();
        if (ssl.getTrustedX509CertificatesForTrustManager().length > 0
            || ssl.isUseInsecureTrustManager()) {
            httpClient = httpClient.secure(sslContextSpec -> {
                // configure ssl
                SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();

                X509Certificate[] trustedX509Certificates = ssl
                    .getTrustedX509CertificatesForTrustManager();
                if (trustedX509Certificates.length > 0) {
                    sslContextBuilder.trustManager(trustedX509Certificates);
                } else if (ssl.isUseInsecureTrustManager()) {
                    sslContextBuilder
                        .trustManager(InsecureTrustManagerFactory.INSTANCE);
                }

                sslContextSpec.sslContext(sslContextBuilder)
                    .defaultConfiguration(ssl.getDefaultConfigurationType())
                    .handshakeTimeout(ssl.getHandshakeTimeout())
                    .closeNotifyFlushTimeout(ssl.getCloseNotifyFlushTimeout())
                    .closeNotifyReadTimeout(ssl.getCloseNotifyReadTimeout());
            });
        }

        httpClient = httpClient.wiretap(true);

        return httpClient;
    }
}
