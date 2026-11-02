package com.pelypets.todolist.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.yaml.snakeyaml.Yaml;

/**
 * The generated spec must describe exactly the operations of the Ktor server's hand-written spec
 * ({@code src/test/resources/openapi/reference.yaml}), so existing clients keep working.
 */
class OpenApiContractTest extends ApiTest {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    @Test
    void generatedSpecHasTheSameOperationsAsTheKtorServer() throws IOException {
        Response generated = get("/v3/api-docs", null);
        assertThat(generated.status()).isEqualTo(HttpStatus.OK);

        assertThat(operations(read(generated, Map.class))).isEqualTo(operations(reference()));
    }

    @Test
    void generatedSpecHasTheReferenceSchemas() throws IOException {
        Set<String> generated = names(schemas(read(get("/v3/api-docs", null), Map.class)));
        Set<String> expected = names(schemas(reference()));
        // The reference names the status enum; generated specs inline it.
        expected.remove("TodoStatus");

        assertThat(generated).containsAll(expected);
    }

    private static Map<?, ?> reference() throws IOException {
        try (InputStream in = OpenApiContractTest.class.getResourceAsStream("/openapi/reference.yaml")) {
            return new Yaml().load(in);
        }
    }

    /** "METHOD path operationId" for every operation. */
    private static Set<String> operations(Map<?, ?> spec) {
        Set<String> result = new TreeSet<>();
        ((Map<?, ?>) spec.get("paths")).forEach((path, item) -> ((Map<?, ?>) item).forEach((method, operation) -> {
            if (HTTP_METHODS.contains(method.toString())) {
                Object operationId = ((Map<?, ?>) operation).get("operationId");
                result.add(method.toString().toUpperCase() + " " + path + " " + operationId);
            }
        }));
        return result;
    }

    private static Map<?, ?> schemas(Map<?, ?> spec) {
        return (Map<?, ?>) ((Map<?, ?>) spec.get("components")).get("schemas");
    }

    private static Set<String> names(Map<?, ?> map) {
        Set<String> result = new TreeSet<>();
        map.keySet().forEach(key -> result.add(key.toString()));
        return result;
    }
}
