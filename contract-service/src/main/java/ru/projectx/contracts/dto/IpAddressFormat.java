package ru.projectx.contracts.dto;

public final class IpAddressFormat {

    private static final String OCTET = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final String IPV4 = OCTET + "(\\." + OCTET + "){3}";
    private static final String HEXTET = "[0-9a-fA-F]{1,4}";
    private static final String IPV6 = "((" + HEXTET + ":){7}" + HEXTET
            + "|(" + HEXTET + ":){1,7}:"
            + "|(" + HEXTET + ":){1,6}:" + HEXTET
            + "|(" + HEXTET + ":){1,5}(:" + HEXTET + "){1,2}"
            + "|(" + HEXTET + ":){1,4}(:" + HEXTET + "){1,3}"
            + "|(" + HEXTET + ":){1,3}(:" + HEXTET + "){1,4}"
            + "|(" + HEXTET + ":){1,2}(:" + HEXTET + "){1,5}"
            + "|" + HEXTET + ":(:" + HEXTET + "){1,6}"
            + "|:((:" + HEXTET + "){1,7}|:)"
            + "|::(ffff(:0{1,4})?:)?" + IPV4
            + "|(" + HEXTET + ":){1,4}:" + IPV4 + ")";

    public static final String PATTERN = IPV4 + "|" + IPV6;

    private IpAddressFormat() {
    }
}
