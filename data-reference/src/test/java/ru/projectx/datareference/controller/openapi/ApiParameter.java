package ru.projectx.datareference.controller.openapi;

record ApiParameter(String name, String location, boolean required, String schema) {

    static final String PATH = "path";
    static final String QUERY = "query";
    static final String HEADER = "header";
    static final String UNMAPPED = "без @RequestParam и @PathVariable";

    static ApiParameter described(YamlNode parameter) {
        SchemaView schema = new SchemaView(parameter.get("schema"), false);
        return new ApiParameter(parameter.get("name").text().orElse(""), parameter.get("in").text().orElse(""),
                parameter.get("required").isTrue(), schema(schema.type(), schema.format()));
    }

    static String schema(Class<?> type) {
        return schema(JsonTypes.of(type), JsonTypes.formatOf(type));
    }

    ApiParameter withRequired(boolean required) {
        return new ApiParameter(name, location, required, schema);
    }

    boolean inQuery() {
        return QUERY.equals(location);
    }

    private static String schema(String type, String format) {
        return type + ", format " + format;
    }
}
