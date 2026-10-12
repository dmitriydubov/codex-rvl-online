package ru.projectx.datareference.acceptance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OneCMoneyScaleMessages {

    static final BigDecimal ONE_THOUSANDTH = new BigDecimal("0.001");

    private static final String PRICE_QUEUE = "1c-usk_to_rvl-online_pricelists-contragents-changes";
    private static final Map<String, String> BASE_PRICE_ROW = Map.of(
            "начало", "2020-01-01", "окончание", "3000-01-01", "цена", "4000.00", "цена со скидкой", "нет");
    private static final Map<String, String> TRANSFER_PRICE_ROW = Map.of(
            "начало", "2026-01-01", "окончание", "3000-01-01", "цена", "6000.00", "цена со скидкой", "нет");
    private static final List<String> PRICE_FIELDS = List.of("price", "priceWithDiscount");
    private static final List<String> BATCH_FIELDS = Stream.concat(PRICE_FIELDS.stream(), Stream.of("rowNumber")).toList();

    private static final BigDecimal COPAYMENT_THIRD_DIGIT = new BigDecimal("10.251");

    private static final String RENAMED_POLICY = "СТЕНД-0001, редакция 2";
    private static final String RESENT = "новая отправка ";
    private static final String UNKNOWN_POLICY_FIELD = "bonusAmount";
    private static final Map<String, Function<ObjectNode, ObjectNode>> POLICY_ROWS = Map.of(
            "amount", OneCMoneyScaleMessages::policyProgram,
            "amountD", OneCMoneyScaleMessages::policyProgram,
            "premium", OneCMoneyScaleMessages::policyProgram,
            "premiumD", OneCMoneyScaleMessages::policyProgram,
            "limit", OneCMoneyScaleMessages::policyProgram,
            "copayment", OneCMoneyScaleMessages::policyServiceLine,
            "layerNumber", OneCMoneyScaleMessages::policyServiceLine,
            "rowNumber", OneCMoneyScaleMessages::policyHeader);
    private static final Map<String, BigDecimal> TWO_DIGITS = Map.of(
            "amount", new BigDecimal("12345.67"), "copayment", new BigDecimal("12.34"));
    private static final Map<String, BigDecimal> THREE_DIGITS = Map.of(
            "amount", new BigDecimal("12345.678"), "copayment", new BigDecimal("12.345"));

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCMoneyScaleMessages() {
    }

    static List<OneCMessage> priceList(String service, String field, String number) {
        List<OneCMessage> transfer = OneCServiceMessages.priceList(service, List.of(BASE_PRICE_ROW));
        ObjectNode row = (ObjectNode) transfer.getFirst().data().withArray("recordSet").get(0);
        row.put(priceField(field), new BigDecimal(number));
        return transfer;
    }

    static OneCMessage priceBatch(UUID messageId, UUID cid, String service, String field, BigDecimal value) {
        if (!BATCH_FIELDS.contains(field)) {
            throw new IllegalArgumentException("шаг не знает поля строки пакета передачи: " + field);
        }
        ObjectNode data = JSON.createObjectNode().put("cid", cid.toString()).put("keyValue", service);
        ObjectNode row = OneCServiceMessages.priceRow(1, TRANSFER_PRICE_ROW).put(field, value);
        data.putArray("recordSet").add(row);
        return new OneCMessage(OneCStream.PRICE_LISTS, PRICE_QUEUE, data, InboundStatus.FAILED, messageId);
    }

    static OneCMessage programWithDroppedRow(OneCMessage program, String droppedRow) {
        ObjectNode data = OneCProgramTermsMessages.renamed(program);
        ObjectNode extra = OneCProgramTermsMessages.firstRow(data).deepCopy()
                .put("percentCopayment", COPAYMENT_THIRD_DIGIT);
        OneCProgramTermsMessages.markDropped(extra, droppedRow);
        OneCProgramTermsMessages.addRow(data, extra);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    static OneCMessage programWithoutRvl(OneCMessage program) {
        ObjectNode data = program.data().deepCopy().put("rvl", false);
        OneCProgramTermsMessages.firstRow(data).put("percentCopayment", COPAYMENT_THIRD_DIGIT);
        return new OneCMessage(program.stream(), program.queue(), data, InboundStatus.PROCESSED);
    }

    static OneCMessage policy(OneCMessage policy, String field, BigDecimal value) {
        OneCMessage resent = policyWith(policy.data().deepCopy(), policy, field, value, InboundStatus.PROCESSED);
        UUID messageId = UUID.nameUUIDFromBytes(
                (RESENT + resent.messageId()).getBytes(StandardCharsets.UTF_8));
        return new OneCMessage(resent.stream(), resent.queue(), resent.data(), resent.expected(), messageId);
    }

    static OneCMessage renamedPolicy(OneCMessage policy, String field, BigDecimal value) {
        ObjectNode data = policy.data().deepCopy().put("number", RENAMED_POLICY);
        return policyWith(data, policy, field, value, InboundStatus.FAILED);
    }

    static OneCMessage renamedPolicyWithUnknownField(OneCMessage policy, BigDecimal value) {
        ObjectNode data = policy.data().deepCopy().put("number", RENAMED_POLICY);
        policyProgram(data).put(UNKNOWN_POLICY_FIELD, value);
        return new OneCMessage(policy.stream(), policy.queue(), data, InboundStatus.PROCESSED);
    }

    static BigDecimal twoDigits(String field) {
        return TWO_DIGITS.get(sumOrPercent(field));
    }

    static BigDecimal threeDigits(String field) {
        return THREE_DIGITS.get(sumOrPercent(field));
    }

    private static OneCMessage policyWith(
            ObjectNode data, OneCMessage policy, String field, BigDecimal value, InboundStatus expected) {
        lookup(POLICY_ROWS, field).apply(data).put(field, value);
        return new OneCMessage(policy.stream(), policy.queue(), data, expected);
    }

    private static String sumOrPercent(String field) {
        lookup(POLICY_ROWS, field);
        return "copayment".equals(field) ? field : "amount";
    }

    private static ObjectNode policyProgram(ObjectNode data) {
        return (ObjectNode) data.withArray("programms").get(0);
    }

    private static ObjectNode policyServiceLine(ObjectNode data) {
        return (ObjectNode) data.withArray("services").get(0);
    }

    private static ObjectNode policyHeader(ObjectNode data) {
        return (ObjectNode) data.withArray("header").get(0);
    }

    private static String priceField(String field) {
        if (!PRICE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("шаг не знает денежного поля строки прайса: " + field);
        }
        return field;
    }

    private static <T> T lookup(Map<String, T> vocabulary, String word) {
        return Optional.ofNullable(vocabulary.get(word))
                .orElseThrow(() -> new IllegalArgumentException("шаг не знает поля: " + word));
    }
}
