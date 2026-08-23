package com.sashplatonov.earnit.kids.platform.webpush;

import jakarta.enterprise.context.ApplicationScoped;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class WebPushJavaAdapter implements WebPushProtocolAdapter {
    private static final int TTL_SECONDS = 86_400;
    private final WebPushConfig config;

    public WebPushJavaAdapter(WebPushConfig config) {
        this.config = config;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public void send(WebPushSubscriptionEntity subscription, String payload) throws Exception {
        String publicKey = required(config.vapidPublicKey());
        String privateKey = required(config.vapidPrivateKey());
        String subject = required(config.vapidSubject());
        Notification notification = new Notification(subscription.getEndpoint(), subscription.getP256dhKey(),
            subscription.getAuthKey(), payload.getBytes(StandardCharsets.UTF_8), TTL_SECONDS);
        HttpResponse response = new PushService(publicKey, privateKey, subject).send(notification);
        int status = response.getStatusLine().getStatusCode();
        if (status < 200 || status >= 300) throw new WebPushTransportException(status);
    }

    private String required(java.util.Optional<String> value) {
        return value.filter(item -> !item.isBlank())
            .orElseThrow(() -> new IllegalStateException("VAPID configuration is incomplete"));
    }
}
