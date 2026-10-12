package ru.projectx.datareference.config;

import java.util.List;

public final class ReferenceCaches {

    public static final String LPU_CATALOG = "lpuCatalog";
    public static final String LPU_CARD = "lpuCard";
    public static final String LPU_SERVICES = "lpuServices";
    public static final String LPU_SERVICES_WITH_PRICES = "lpuServicesWithPrices";
    public static final String SERVICE_PRICES = "servicePrices";
    public static final String MKB10_SEARCH = "mkb10Search";
    public static final String MKB10_CODES = "mkb10Codes";
    public static final String INSURANCE_CONTRACTS_BY_ID = "insuranceContractsById";
    public static final String INSURANCE_CONTRACTS_BY_EXTERNAL_ID = "insuranceContractsByExternalId";
    public static final String PROGRAMS = "programs";

    public static final List<String> ALL = List.of(
            LPU_CATALOG, LPU_CARD, LPU_SERVICES, LPU_SERVICES_WITH_PRICES, SERVICE_PRICES,
            MKB10_SEARCH, MKB10_CODES, INSURANCE_CONTRACTS_BY_ID, INSURANCE_CONTRACTS_BY_EXTERNAL_ID, PROGRAMS);

    public static final String TODAY_KEY = "todayKeyGenerator";

    private ReferenceCaches() {
    }
}
