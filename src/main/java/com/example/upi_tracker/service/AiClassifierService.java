package com.example.upi_tracker.service;

import com.example.upi_tracker.entity.FailedTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AiClassifierService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Autowired
    private RestTemplate restTemplate;
    private String originalSms;

    public FullAnalysisResult analyzeCompletely(String rawSms) {

        String prompt = buildPrompt(rawSms);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        String url = apiUrl + "?key=" + apiKey;

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        url, requestBody, Map.class
                );
                String rawText = extractText(response.getBody());
                System.out.println("========== GEMINI RAW RESPONSE ==========");
                System.out.println(rawText);
                System.out.println("=========================================");
                return parseResponse(rawText, rawSms);

            } catch (Exception e) {
                if (attempt == 1 && e.getMessage() != null
                        && e.getMessage().contains("429")) {
                    try {
                        System.out.println("Rate limited — waiting 25s...");
                        Thread.sleep(25000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.out.println("Gemini failed: " + e.getMessage());
                    return buildFallback(rawSms);
                }
            }
        }
        return buildFallback(rawSms);
    }

    private String buildPrompt(String sms) {
        return """
        You are a senior Indian banking expert and consumer rights lawyer
        with 20 years of experience in UPI disputes and RBI regulations.

        Analyze this SMS and return ONLY raw JSON. No markdown. No text.

        SMS: "%s"

        EXAMPLE 1 — High recovery (technical failure with ref):
        SMS: "Rs.2000 debited from XX1234. UPI Ref 320411952228. Transaction failed due to technical error. -HDFCBK"
        Response: {"is_failure":true,"is_reversal":false,"amount":2000,"upi_ref":"320411952228","merchant_name":"HDFC","bank_name":"HDFC","failure_type":"BANK_ERROR","recovery_probability":91,"recovery_reasoning":"Bank acknowledged technical error making them fully liable under RBI Circular 2019-20/142. UPI reference number exists for precise tracing. Auto-refund expected within 24-48 hours. Strong case for dispute if not received.","recommended_action":"Wait 48 hours for auto-refund. If not received, use the UPI reference number to raise dispute with HDFC.","can_dispute":true,"rbi_applies":true,"expected_resolution_days":2,"escalation_needed":false}

        EXAMPLE 2 — Low recovery (user error):
        SMS: "Txn Failed: Security verification failed for SBI Card XX9012 at FLIPKART. Incorrect OTP entered."
        Response: {"is_failure":true,"is_reversal":false,"amount":0,"upi_ref":"","merchant_name":"FLIPKART","bank_name":"SBI","failure_type":"USER_ERROR","recovery_probability":12,"recovery_reasoning":"Authentication failed due to incorrect OTP entered by user. No money was debited since transaction was rejected before processing. Bank bears no liability as per RBI guidelines on user-initiated authentication failures. No dispute path available.","recommended_action":"Retry the transaction with correct OTP. If money was debited despite failed authentication, contact SBI immediately with transaction timestamp.","can_dispute":false,"rbi_applies":false,"expected_resolution_days":0,"escalation_needed":false}

        EXAMPLE 3 — Reversal/refund:
        SMS: "UPI Reversal: Your account XX7788 credited with Rs.500 towards reversal of failed UPI txn. UPI Ref: 3341908212. -SBIINB"
        Response: {"is_failure":false,"is_reversal":true,"amount":500,"upi_ref":"3341908212","merchant_name":"Unknown","bank_name":"SBI","failure_type":"TIMEOUT","recovery_probability":100,"recovery_reasoning":"Money has already been credited back to your account. The bank has processed the reversal automatically within the RBI mandated timeframe.","recommended_action":"Check your account balance to confirm the credit. No further action needed.","can_dispute":false,"rbi_applies":false,"expected_resolution_days":0,"escalation_needed":false}

        EXAMPLE 4 — Medium recovery (limit exceeded):
        SMS: "ALERT: Transaction of INR 15,000 at Apple Store via ICICI Bank failed. Daily online spending limit exceeded."
        Response: {"is_failure":true,"is_reversal":false,"amount":15000,"upi_ref":"","merchant_name":"Apple Store","bank_name":"ICICI","failure_type":"LIMIT_EXCEEDED","recovery_probability":68,"recovery_reasoning":"Transaction was blocked by bank's own spending limit controls. Money was NOT debited — the transaction was prevented before processing. User needs to increase their daily limit via iMobile or contact ICICI. No money to recover but user can retry after limit modification.","recommended_action":"Login to iMobile app, go to Manage Cards, increase daily online spending limit, then retry the Apple Store transaction.","can_dispute":false,"rbi_applies":false,"expected_resolution_days":1,"escalation_needed":false}

        EXAMPLE 5 — Duplicate charge:
        SMS: "Rs.5000 debited from your account twice for same order at Zomato. UPI Ref: 441209384756. -AXISBK"
        Response: {"is_failure":true,"is_reversal":false,"amount":5000,"upi_ref":"441209384756","merchant_name":"Zomato","bank_name":"AXIS","failure_type":"DUPLICATE","recovery_probability":97,"recovery_reasoning":"Duplicate debit is one of the strongest cases for recovery under RBI guidelines. Bank is fully liable. UPI reference number enables precise tracing of both transactions. Zomato merchant can also confirm single order. Recovery almost certain within 2-3 days.","recommended_action":"Immediately call AXIS Bank helpline with UPI Ref 441209384756 and report duplicate debit. Also report to Zomato support with order ID.","can_dispute":true,"rbi_applies":true,"expected_resolution_days":3,"escalation_needed":false}

        NOW analyze the actual SMS above and return JSON with these EXACT fields.
        Think deeply about the specific details in THIS SMS:
        - Is there a reference number? (+recovery)
        - Did bank explicitly admit fault? (+recovery)
        - Was it user's mistake? (-recovery)
        - Was money actually debited? (critical)
        - Is merchant known/legitimate? (+recovery)
        - Does SMS mention auto-refund? (+recovery)
        - What is the amount? (higher = more scrutiny)

                IMPORTANT RULES
                
                                                                                          Return ONLY valid JSON.
                
                                                                                          Never return a default score.
                
                                                                                          Never return 50 unless the evidence truly supports a medium recovery chance.
                
                                                                                          Choose recovery_probability using these RBI/NPCI guidelines:
                
                                                                                          Duplicate debit: 97-99
                
                                                                                          Technical bank failure after debit: 90-96
                
                                                                                          Timeout after debit: 88-94
                
                                                                                          Bank server error: 85-95
                
                                                                                          NPCI failure: 82-92
                
                                                                                          Merchant failure after debit: 75-88
                
                                                                                          Pending transaction: 60-75
                
                                                                                          Limit exceeded: 20-35
                
                                                                                          Wrong UPI PIN: 1-10
                
                                                                                          Wrong OTP: 5-15
                
                                                                                          User cancelled: 1-5
                
                                                                                          Refund already credited: 100
                
                                                                                          Base your score ONLY on THIS SMS.
                                                                                          Do not average scores.
                                                                                          Do not use 50 as a default.
        Do NOT default to 50. Scores should range widely: 5-15 for clear user errors,
        80-97 for clear bank errors with reference numbers, 40-70 for unclear cases.
                VERY IMPORTANT
                
                Do NOT reuse previous scores.
                
                Every SMS must be scored independently.
                
                Duplicate debit:
                97-99
                
                Technical error after debit:
                90-96
                
                Timeout:
                88-94
                
                Merchant declined after debit:
                75-88
                
                Network issue:
                70-85
                
                Pending:
                60-70
                
                Limit exceeded:
                20-35
                
                Wrong PIN:
                1-10
                
                Wrong OTP:
                5-15
                
                Refund completed:
                100
                
                Return the most appropriate score.
                Never return 74 unless the SMS genuinely deserves exactly 74.

        amount rule: Return ONLY the number. ₹200 → 200, Rs.15,000 → 15000
        """.formatted(sms);
    }
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        try {
            if (!body.containsKey("candidates")) {
                throw new RuntimeException("API did not return candidates. Body: " + body);
            }
            var candidates = (List<Map<String, Object>>) body.get("candidates");
            var content = (Map<String, Object>) candidates.get(0).get("content");
            var parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Could not read Gemini response: " + e.getMessage());
        }
    }

    private FullAnalysisResult parseResponse(String rawText, String originalSms) {

        try {

            String cleaned = rawText
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');

            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            System.out.println("========== CLEANED JSON ==========");
            System.out.println(cleaned);
            System.out.println("==================================");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(cleaned);

            FullAnalysisResult r = new FullAnalysisResult();

            // ---------------- BASIC ----------------

            r.isFailure = node.path("is_failure").asBoolean(true);
            r.isReversal = node.path("is_reversal").asBoolean(false);

            r.amount = extractAmountFromNode(node.path("amount"), originalSms);

            r.upiRef = node.path("upi_ref").asText("");

            r.merchantName = node.path("merchant_name").asText("Unknown");

            r.bankName = node.path("bank_name").asText("Unknown");

            // ---------------- FAILURE TYPE ----------------

            // ---------------- FAILURE TYPE ----------------
            
            // Just trust the AI's classification directly, defaulting to BANK_ERROR if missing
            String aiType = node.path("failure_type").asText("BANK_ERROR").trim().toUpperCase();
            
            // Fix minor variations AI might output
            if (aiType.contains("TIME")) r.failureType = "TIMEOUT";
            else if (aiType.contains("USER") || aiType.contains("OTP") || aiType.contains("PIN")) r.failureType = "USER_ERROR";
            else if (aiType.contains("LIMIT")) r.failureType = "LIMIT_EXCEEDED";
            else if (aiType.contains("MERCHANT")) r.failureType = "MERCHANT_ERROR";
            else if (aiType.contains("DUPLICATE")) r.failureType = "DUPLICATE";
            else if (aiType.contains("NPCI")) r.failureType = "NPCI_ERROR";
            else r.failureType = aiType; 
            
            String sms = originalSms.toLowerCase();

            // ---------------- SCORE ----------------

            r.recoveryScore = node.path("recovery_probability").asInt(-1);

            if (r.recoveryScore <= 0 || r.recoveryScore > 100) {

                r.recoveryScore = calculateFallbackScore(
                        r.failureType,
                        r.upiRef,
                        originalSms);
            }

            // ---------------- OTHER ----------------

            r.recoveryReasoning =
                    node.path("recovery_reasoning").asText("");

            if (r.recoveryReasoning.isBlank()) {

                r.recoveryReasoning =
                        generateFallbackReasoning(
                                r.failureType,
                                r.recoveryScore,
                                r.upiRef);
            }

            r.recommendedAction =
                    node.path("recommended_action")
                            .asText("Contact your bank.");

            r.canDispute =
                    node.path("can_dispute").asBoolean(true);

            r.rbiApplies =
                    node.path("rbi_applies").asBoolean(false);

            r.expectedResolutionDays =
                    node.path("expected_resolution_days").asInt(5);

            r.escalationNeeded =
                    node.path("escalation_needed").asBoolean(false);

            r.rawSms = originalSms;

            // ---------------- REVERSAL DETECTION ----------------

            if (sms.contains("reversal")
                    || sms.contains("reversed")
                    || sms.contains("credited")
                    || sms.contains("credited back")
                    || sms.contains("refund")
                    || sms.contains("refund initiated")
                    || sms.contains("refund processed")
                    || sms.contains("refund successful")
                    || sms.contains("returned")
                    || sms.contains("auto reversal")) {

                r.isFailure = false;
                r.isReversal = true;
                r.failureType = "REFUND_COMPLETED";
                r.recoveryScore = 100;

                r.recoveryReasoning =
                        "Money has already been credited back to your account.";

                r.recommendedAction =
                        "No action required.";
            }

            System.out.println("===== FINAL RESULT =====");
            System.out.println("Failure : " + r.isFailure);
            System.out.println("Reversal: " + r.isReversal);
            System.out.println("Type    : " + r.failureType);
            System.out.println("Score   : " + r.recoveryScore);
            System.out.println("========================");

            return r;

        }

        catch (Exception e) {

            System.out.println("Parse Error : " + e.getMessage());

            return buildFallback(originalSms);
        }

    }

    private BigDecimal extractAmountFromNode(JsonNode amountNode, String sms) {
        // Try direct number from AI response first
        if (!amountNode.isMissingNode() && !amountNode.isNull()) {
            try {
                String raw = amountNode.asText()
                        .replaceAll("[₹,Rs\\.INRinr\\s]", "")
                        .trim();
                if (!raw.isEmpty()) {
                    BigDecimal val = new BigDecimal(raw);
                    if (val.compareTo(BigDecimal.ONE) >= 0) {
                        return val;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Fallback — scan the raw SMS for any number pattern
        return extractFromSmsText(sms);
    }

    private BigDecimal extractFromSmsText(String sms) {
        // Try every possible Indian currency format in order of specificity
        String[] patterns = {
                // Standard formats with currency prefix
                "(?:Rs\\.?\\s*|INR\\s*)[,\\d]+(?:\\.\\d{1,2})?",
                // Rupee symbol variants (multiple Unicode points)
                "[\u20B9\u20A8\uFFFD]\\s*[,\\d]+(?:\\.\\d{1,2})?",
                // "of/for/worth X" format
                "(?:of|for|worth)\\s+[,\\d]+(?:\\.\\d{1,2})?",
                // Amount with rupees suffix
                "[,\\d]+(?:\\.\\d{1,2})?\\s*(?:rupees|/-)",
                // Contextual — number near payment/transaction keywords
                "(?:payment|amount|deducted|debited|charged|txn|transaction)" +
                        "\\s+(?:of\\s+)?[,\\d]+(?:\\.\\d{1,2})?",
        };

        for (String patternStr : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(sms);
            while (m.find()) {
                // Extract just the digits from whatever matched
                String match = m.group().replaceAll("[^\\d.]", "");
                // Remove leading/trailing dots
                match = match.replaceAll("^\\.+|\\.+$", "");
                if (!match.isEmpty() && !match.equals(".")) {
                    try {
                        BigDecimal val = new BigDecimal(match);
                        // Valid transaction amount range: ₹1 to ₹1 crore
                        if (val.compareTo(BigDecimal.ONE) >= 0 &&
                                val.compareTo(new BigDecimal("10000000")) <= 0) {
                            return val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        System.out.println("[WARN] Could not extract amount from: " + sms);
        return BigDecimal.ZERO;
    }
    private int calculateFallbackScore(String failureType, String upiRef, String sms) {
        int base = switch (failureType) {

            case "DUPLICATE" -> 97;

            case "TIMEOUT" -> {
                int score = 84;

                if (upiRef != null &&
                        !upiRef.startsWith("REF-") &&
                        upiRef.length() >= 10)
                    score += 8;

                yield score;
            }

            case "BANK_ERROR" -> {

                int score = 70;

                String s = sms.toLowerCase();

                if (s.contains("technical"))
                    score += 10;

                if (s.contains("server"))
                    score += 8;

                if (s.contains("timeout"))
                    score += 7;

                if (s.contains("failed"))
                    score += 4;

                if (upiRef != null &&
                        !upiRef.startsWith("REF-") &&
                        upiRef.length() >= 10)
                    score += 8;

                yield Math.min(score,95);
            }

            case "LIMIT_EXCEEDED" -> 28;

            case "GHOST_MERCHANT" -> 45;

            case "USER_ERROR" -> 8;

            default -> 50;
        };

        // Real ref number → bank can trace → higher chance
        if (upiRef != null && !upiRef.startsWith("REF-") && upiRef.length() >= 10) {
            base += 7;
        }

        // SMS mentions auto-refund → already in process
        if (sms.toLowerCase().contains("will be refunded") ||
                sms.toLowerCase().contains("auto reversal") ||
                sms.toLowerCase().contains("will be reversed")) {
            base += 8;
        }

        // SMS mentions no debit → nothing to recover
        if (sms.toLowerCase().contains("not debited") ||
                sms.toLowerCase().contains("no amount") ||
                sms.toLowerCase().contains("amount not deducted")) {
            base = Math.min(base, 20);
        }

        return Math.max(5, Math.min(97, base));
    }

    private FullAnalysisResult buildFallback(String sms) {

        FullAnalysisResult r = new FullAnalysisResult();

        r.isFailure = true;
        r.isReversal = false;

        // Extract amount directly from SMS
        r.amount = extractFromSmsText(sms);

        r.upiRef = "REF-" + System.currentTimeMillis();
        r.merchantName = "Unknown Merchant";
        r.bankName = "Unknown Bank";
        r.failureType = "BANK_ERROR";

        r.recoveryScore = calculateFallbackScore(
                r.failureType,
                r.upiRef,
                sms);

        r.recoveryChance = "MEDIUM";

        r.recoveryReasoning =
                generateFallbackReasoning(
                        r.failureType,
                        r.recoveryScore,
                        r.upiRef);

        r.recommendedAction =
                "Contact your bank with the transaction details.";

        r.canDispute = true;
        r.rbiApplies = false;
        r.rawSms = sms;

        return r;
    }

    // expose extractFromSmsText for TransactionService fallback
    public BigDecimal extractAmountFromSms(String sms) {
        return extractFromSmsText(sms);
    }
    private String generateFallbackReasoning(String type, int score, String ref) {
        boolean hasRef = ref != null && !ref.startsWith("REF-") && ref.length() >= 10;
        return switch (type) {
            case "DUPLICATE" -> "Duplicate charge detected — one of the strongest cases for recovery. " +
                    "Bank is fully liable under RBI guidelines. " +
                    (hasRef ? "Reference number " + ref + " enables precise tracing." : "Raise dispute immediately.");
            case "TIMEOUT" -> "Network timeout failure — bank is liable under RBI Circular 2019-20/142. " +
                    "Auto-refund expected within 24-48 hours. " +
                    (hasRef ? "Reference " + ref + " confirms the transaction record exists." : "Contact bank if not refunded in 48 hours.");
            case "BANK_ERROR" -> "Bank-side processing error — bank bears full liability. " +
                    "RBI mandates refund within 5 business days. " +
                    (hasRef ? "Use reference " + ref + " when raising dispute." : "File dispute with transaction timestamp.");
            case "LIMIT_EXCEEDED" -> "Spending limit blocked this transaction. Money was NOT debited. " +
                    "Increase your daily limit in your banking app and retry.";
            case "GHOST_MERCHANT" -> "Unregistered merchant — complex case requiring NPCI involvement. " +
                    "File dispute immediately and escalate to NPCI if unresolved in 3 days.";
            case "USER_ERROR" -> "Authentication failure due to user input error. " +
                    "If no money was debited, simply retry with correct details. " +
                    "If debited, contact bank immediately with transaction timestamp.";
            default -> "Contact your bank with all transaction details and request investigation.";
        };
    }

    public static class FullAnalysisResult {
        public boolean isFailure;
        public boolean isReversal;
        public BigDecimal amount;
        public String upiRef;
        public String merchantName;
        public String bankName;
        public String failureType;
        public int recoveryScore;        // now comes from AI's recovery_probability
        public String recoveryReasoning; // AI explains WHY this specific score
        public String recommendedAction;
        public boolean canDispute;
        public boolean rbiApplies;
        public int expectedResolutionDays;
        public boolean escalationNeeded;
        public String rawSms;
        public String recoveryChance;
    }}
