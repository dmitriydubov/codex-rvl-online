package ru.projectx.datareference.controller.openapi;

record ApiParameter(String name, String location, boolean required) {

    static final String PATH = "path";
    static final String QUERY = "query";
    static final String HEADER = "header";
    static final String UNMAPPED = "без @RequestParam и @PathVariable";

    static ApiParameter described(YamlNode parameter) {
        return new ApiParameter(parameter.get("name").text().orElse(""), parameter.get("in").text().orElse(""),
                parameter.get("required").isTrue());
    }

    boolean inQuery() {
        return QUERY.equals(location);
    }
}
