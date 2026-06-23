package org.cloudburstmc.protocol.bedrock.util;

import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.JsonWebSignature;

import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;

public final class NetEaseEncryptionUtils {
    private NetEaseEncryptionUtils() {}
    private static final ECPublicKey NETEASE_PUBLIC_KEY;
    private static final AlgorithmConstraints ALGORITHM_CONSTRAINTS;

    static {
        ALGORITHM_CONSTRAINTS = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, EncryptionUtils.ALGORITHM_TYPE);
        try {
            NETEASE_PUBLIC_KEY = EncryptionUtils.parseKey(
                    "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEEsmU+IF/XeAF3yiqJ7Ko36btx6JtdB26wV9Eyw4AYR/nmesznkfXxwQ4B0NkSnGIZccbb2f3nFUYughKSoAcNHx+lQm8F9h9RwhrNgeN907z06LUA2AqWcwqasxyaU0E"
            );
        } catch (Exception e) {
            throw new AssertionError("Unable to initialize NetEase public key", e);
        }
    }

    public static ChainValidationResult validateChain(CertificateChainPayload chainPayload) throws Exception {
        List<String> chain = chainPayload.getChain();
        if (chain == null || chain.isEmpty()) {
            throw new IllegalStateException("Certificate chain is empty");
        }

        return switch (chain.size()) {
            case 1 -> {
                JsonWebSignature identity = new JsonWebSignature();
                identity.setCompactSerialization(chain.get(0));
                yield new ChainValidationResult(false, identity.getUnverifiedPayload());
            }
            case 3 -> {
                ECPublicKey currentKey = null;
                Map<String, Object> parsedPayload = null;

                for (int i = 0; i < chain.size(); i++) {
                    JsonWebSignature signature = new JsonWebSignature();
                    signature.setCompactSerialization(chain.get(i));
                    ECPublicKey expectedKey = EncryptionUtils.parseKey(signature.getHeader("x5u"));

                    if (currentKey == null) {
                        currentKey = expectedKey;
                    } else if (!currentKey.equals(expectedKey)) {
                        throw new IllegalStateException("Received broken chain");
                    }

                    signature.setAlgorithmConstraints(ALGORITHM_CONSTRAINTS);
                    signature.setKey(currentKey);
                    if (!signature.verifySignature()) {
                        throw new IllegalStateException("Chain signature doesn't match content");
                    }

                    if (i == 1 && !currentKey.equals(NETEASE_PUBLIC_KEY)) {
                        throw new IllegalStateException("The chain isn't signed by NetEase!");
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = JsonUtil.parseJson(signature.getUnverifiedPayload());
                    parsedPayload = payload;
                    String identityPublicKey = (String) payload.get("identityPublicKey");
                    currentKey = EncryptionUtils.parseKey(identityPublicKey);
                }

                yield new ChainValidationResult(true, parsedPayload);
            }
            default -> throw new IllegalStateException("Unexpected login chain length: " + chain.size());
        };
    }
}
