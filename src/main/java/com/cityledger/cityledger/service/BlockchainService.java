package com.cityledger.cityledger.service;

import com.cityledger.cityledger.model.Complaint;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;

@Service
@Slf4j
public class BlockchainService {

    @Value("${blockchain.contract.address}")
    private String contractAddress;

    @Value("${blockchain.wallet.private-key}")
    private String walletPrivateKey;

    @Value("${blockchain.rpc.url}")
    private String rpcUrl;

    private Web3j web3j;
    private Credentials credentials;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        try {
            if (walletPrivateKey != null
                    && !walletPrivateKey.isBlank()
                    && !walletPrivateKey.contains("YOUR_")) {
                web3j = Web3j.build(new HttpService(rpcUrl));
                credentials = Credentials.create(walletPrivateKey);
                enabled = true;
                log.info("BlockchainService initialized. Wallet: {}", credentials.getAddress());
            } else {
                log.warn("BlockchainService disabled — no wallet private key configured.");
            }
        } catch (Exception e) {
            log.error("BlockchainService init failed: {}", e.getMessage());
        }
    }

    /**
     * Generates a deterministic SHA-256 hash from complaint data.
     * This hash can be independently reproduced by anyone with the complaint data.
     */
    public byte[] generateComplaintHash(Complaint complaint) {
        try {
            String raw = complaint.getId()
                    + "|" + complaint.getTitle()
                    + "|" + complaint.getDescription()
                    + "|" + complaint.getLocation()
                    + "|" + complaint.getCreatedAt().toString();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate complaint hash", e);
        }
    }

    /**
     * Returns the SHA-256 hash as a hex string.
     */
    public String generateComplaintHashHex(Complaint complaint) {
        return Numeric.toHexStringNoPrefix(generateComplaintHash(complaint));
    }

    /**
     * Files a complaint hash on-chain by calling fileComplaint(uint256, bytes32)
     * on the deployed ComplaintRegistry contract.
     *
     * @return The Ethereum transaction hash, or a simulated hash if blockchain is disabled.
     */
    public String fileOnChain(Complaint complaint) {
        byte[] hashBytes = generateComplaintHash(complaint);

        if (!enabled) {
            log.warn("Blockchain disabled. Returning simulated TX hash for complaint #{}", complaint.getId());
            return "0xSIMULATED_" + Numeric.toHexStringNoPrefix(hashBytes).substring(0, 40);
        }

        try {
            // Build the fileComplaint(uint256, bytes32) function call
            byte[] padded = new byte[32];
            System.arraycopy(hashBytes, 0, padded, 0, Math.min(hashBytes.length, 32));

            Function function = new Function(
                    "fileComplaint",
                    Arrays.asList(
                            new Uint256(BigInteger.valueOf(complaint.getId())),
                            new Bytes32(padded)
                    ),
                    Collections.emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            // Get nonce
            EthGetTransactionCount txCount = web3j.ethGetTransactionCount(
                    credentials.getAddress(),
                    DefaultBlockParameterName.LATEST
            ).send();
            BigInteger nonce = txCount.getTransactionCount();

            // Build raw transaction (Sepolia chain ID = 11155111)
            BigInteger gasPrice = BigInteger.valueOf(20_000_000_000L); // 20 gwei
            BigInteger gasLimit = BigInteger.valueOf(150_000);

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    contractAddress,
                    BigInteger.ZERO,
                    encodedFunction
            );

            // Sign and send
            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, 11155111L, credentials);
            String hexValue = Numeric.toHexString(signedMessage);

            EthSendTransaction response = web3j.ethSendRawTransaction(hexValue).send();

            if (response.hasError()) {
                log.error("Blockchain TX failed: {}", response.getError().getMessage());
                return "0xFAILED_" + Numeric.toHexStringNoPrefix(hashBytes).substring(0, 40);
            }

            String txHash = response.getTransactionHash();
            log.info("Complaint #{} filed on-chain. TX: {}", complaint.getId(), txHash);
            return txHash;

        } catch (Exception e) {
            log.error("Blockchain TX exception for complaint #{}: {}", complaint.getId(), e.getMessage());
            return "0xERROR_" + Numeric.toHexStringNoPrefix(hashBytes).substring(0, 40);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Verifies if the complaint data has been tampered with by comparing
     * the current hash with the stored hash.
     * 
     * @param complaint The complaint to verify
     * @return true if the complaint is authentic (hash matches), false if tampered
     */
    public boolean verifyComplaintIntegrity(Complaint complaint) {
        if (complaint.getComplaintHash() == null || complaint.getComplaintHash().isEmpty()) {
            log.warn("Complaint #{} has no stored hash - cannot verify", complaint.getId());
            return false;
        }
        
        try {
            String currentHash = generateComplaintHashHex(complaint);
            boolean isValid = currentHash.equals(complaint.getComplaintHash());
            
            if (!isValid) {
                log.warn("INTEGRITY VIOLATION: Complaint #{} hash mismatch! Stored: {}, Current: {}", 
                    complaint.getId(), 
                    complaint.getComplaintHash().substring(0, 16) + "...", 
                    currentHash.substring(0, 16) + "...");
            } else {
                log.debug("Complaint #{} integrity verified ✓", complaint.getId());
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying complaint #{}: {}", complaint.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Verification result with detailed information
     */
    public static class VerificationResult {
        private final boolean isValid;
        private final String storedHash;
        private final String currentHash;
        private final String blockchainTxHash;
        private final String message;

        public VerificationResult(boolean isValid, String storedHash, String currentHash, 
                                String blockchainTxHash, String message) {
            this.isValid = isValid;
            this.storedHash = storedHash;
            this.currentHash = currentHash;
            this.blockchainTxHash = blockchainTxHash;
            this.message = message;
        }

        public boolean isValid() { return isValid; }
        public String getStoredHash() { return storedHash; }
        public String getCurrentHash() { return currentHash; }
        public String getBlockchainTxHash() { return blockchainTxHash; }
        public String getMessage() { return message; }
    }

    /**
     * Performs detailed verification and returns comprehensive result
     */
    public VerificationResult verifyComplaintDetailed(Complaint complaint) {
        if (complaint.getComplaintHash() == null || complaint.getComplaintHash().isEmpty()) {
            return new VerificationResult(false, null, null, complaint.getBlockchainHash(),
                "No stored hash found - complaint may predate blockchain integration");
        }

        String currentHash = generateComplaintHashHex(complaint);
        String storedHash = complaint.getComplaintHash();
        boolean isValid = currentHash.equals(storedHash);

        String message;
        if (isValid) {
            message = "✓ Verified - Complaint data is authentic and has not been tampered with";
        } else {
            message = "⚠ WARNING - Complaint data has been modified after blockchain submission";
        }

        return new VerificationResult(isValid, storedHash, currentHash, 
            complaint.getBlockchainHash(), message);
    }
}
