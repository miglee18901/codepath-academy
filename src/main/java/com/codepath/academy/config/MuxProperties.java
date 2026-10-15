package com.codepath.academy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mux")
@Getter
@Setter
public class MuxProperties {
    private String tokenId = "";
    private String tokenSecret = "";
    private String signingKeyId = "";
    private String signingPrivateKey = "";

    public boolean configured() {
        return !tokenId.isBlank() && !tokenSecret.isBlank()
                && !signingKeyId.isBlank() && !signingPrivateKey.isBlank();
    }
}
