package com.codepath.academy.security;

import com.codepath.academy.entity.PasskeyCredential;
import com.codepath.academy.repository.*;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PasskeyCredentialStore implements CredentialRepository {
    private final PasskeyCredentialRepository credentials;
    private final UserRepository users;

    public static ByteArray bytes(String value) {
        try { return ByteArray.fromBase64Url(value); }
        catch (Exception e) { throw new IllegalStateException("Dữ liệu Passkey không hợp lệ", e); }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return users.findByUsername(username).map(user -> credentials.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(c -> PublicKeyCredentialDescriptor.builder()
                        .id(bytes(c.getCredentialId()))
                        .transports(c.getTransports().isEmpty() ? Set.of() : Arrays.stream(c.getTransports().split(","))
                                .map(AuthenticatorTransport::of).collect(Collectors.toSet()))
                        .build()).collect(Collectors.toSet())).orElseGet(Set::of);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return users.findByUsername(username).map(u -> u.getPasskeyHandle()).map(PasskeyCredentialStore::bytes);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray handle) {
        return users.findByPasskeyHandle(handle.getBase64Url()).map(u -> u.getUsername());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray id, ByteArray handle) {
        return credentials.findByCredentialId(id.getBase64Url()).flatMap(c -> users.findById(c.getUserId())
                .filter(u -> handle.getBase64Url().equals(u.getPasskeyHandle())).map(u -> registered(c, handle)));
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray id) {
        return credentials.findByCredentialId(id.getBase64Url()).flatMap(c -> users.findById(c.getUserId())
                .map(u -> registered(c, bytes(u.getPasskeyHandle())))).map(Set::of).orElseGet(Set::of);
    }

    private RegisteredCredential registered(PasskeyCredential c, ByteArray handle) {
        return RegisteredCredential.builder().credentialId(bytes(c.getCredentialId())).userHandle(handle)
                .publicKeyCose(bytes(c.getPublicKeyCose())).signatureCount(c.getSignatureCount())
                .backupEligible(c.isBackupEligible()).backupState(c.isBackedUp()).build();
    }
}
