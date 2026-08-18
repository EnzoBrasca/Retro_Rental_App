package com.retrorental.backend.service;

import com.retrorental.backend.config.MistralProperties;
import com.retrorental.backend.dto.response.TicketAnalysisResult;
import com.retrorental.backend.exception.ErrorCode;
import com.retrorental.backend.exception.TicketAnalysisException;
import com.retrorental.backend.model.enums.TipoCombustible;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementacion de {@link TicketAnalysisService} sobre el endpoint de OCR de
 * Mistral (POST /v1/ocr).
 *
 * SOLO se instancia cuando hay "mistral.api-key" configurada (mismo condicional
 * que el bean RestClient en MistralConfig), asi la app arranca sin la key
 * durante el setup.
 *
 * El OCR "pelado" devuelve markdown; para obtener los campos ya estructurados se
 * usa "document_annotation_format": se le pasa un JSON schema y Mistral responde
 * el objeto en el campo "document_annotation" de la respuesta.
 */
@Service
@ConditionalOnProperty(prefix = "mistral", name = "api-key")
public class MistralTicketAnalysisService implements TicketAnalysisService {

    // Schema que Mistral debe respetar al devolver los datos del ticket.
    //
    // Cada campo admite null (union ["tipo","null"]) porque un dato ilegible
    // debe volver null, no inventarse. Igual se listan todos en "required" y se
    // usa additionalProperties:false: sin eso, el modelo tiende a devolver JSON
    // MALFORMADO (claves con comillas escapadas de más) y solo se rescata algún
    // campo suelto. IMPORTANTE: NO usar "strict":true en el wrapper json_schema;
    // con este OCR rompe el JSON de la anotación (ver buildRequestBody).
    // Valores admitidos para tipoCombustible: los del enum TipoCombustible más
    // null (dato ilegible). Se usa Arrays.asList porque List.of NO admite null.
    private static final List<Object> TIPO_COMBUSTIBLE_VALUES = Arrays.asList(
        TipoCombustible.NAFTA_SUPER.name(),
        TipoCombustible.NAFTA_PREMIUM.name(),
        TipoCombustible.GASOIL_GRADO_2.name(),
        TipoCombustible.GASOIL_GRADO_3.name(),
        TipoCombustible.GNC.name(),
        null
    );

    private static final Map<String, Object> ANNOTATION_SCHEMA = Map.of(
        "type", "object",
        "properties", Map.of(
            "litros", Map.of("type", List.of("number", "null"),
                "description", "Litros cargados de combustible"),
            "fechaCarga", Map.of("type", List.of("string", "null"),
                "description", "Fecha y hora de la carga en formato ISO-8601 (YYYY-MM-DDTHH:mm:ss)"),
            "importeTotal", Map.of("type", List.of("number", "null"),
                "description", "Importe total pagado"),
            "precioPorLitro", Map.of("type", List.of("number", "null"),
                "description", "Precio por litro"),
            "estacion", Map.of("type", List.of("string", "null"),
                "description", "Nombre de la estacion de servicio"),
            "cuit", Map.of("type", List.of("string", "null"),
                "description", "CUIT del proveedor/estacion de servicio"),
            // El modelo clasifica el combustible del ticket en una de las
            // categorias del enum. La lista "enum" fuerza que devuelva un valor
            // valido (o null); las equivalencias comerciales van en la descripcion.
            "tipoCombustible", Map.of(
                "type", List.of("string", "null"),
                "enum", TIPO_COMBUSTIBLE_VALUES,
                "description", "Tipo de combustible cargado. Equivalencias: "
                    + "NAFTA_SUPER=nafta comun/super; NAFTA_PREMIUM=nafta premium/infinia/v-power; "
                    + "GASOIL_GRADO_2=gasoil comun/grado 2; GASOIL_GRADO_3=gasoil premium/infinia diesel/euro/grado 3; "
                    + "GNC=gas natural comprimido")
        ),
        "required", List.of("litros", "fechaCarga", "importeTotal", "precioPorLitro",
            "estacion", "cuit", "tipoCombustible"),
        "additionalProperties", false
    );

    private final RestClient mistralRestClient;
    private final MistralProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Mamparo: cuantos analisis pueden estar en vuelo a la vez.
     *
     * Sin esto, el limite de concurrencia real es el pool de hilos de Tomcat
     * (200 por defecto). Doscientas llamadas simultaneas significan doscientos
     * hilos bloqueados hasta 30 segundos y doscientas imagenes en base64 vivas
     * en memoria: la API deja de responder para TODO lo demas, incluido el
     * login y la carga manual de tickets. El tope convierte una saturacion del
     * OCR en un 503 acotado a ese endpoint, en vez de una caida general.
     *
     * Justo (fair): los que esperan entran en orden de llegada. Sin esto, con
     * carga sostenida un request puede quedar postergado indefinidamente
     * mientras otros que llegaron despues se cuelan.
     */
    private final Semaphore lugaresDisponibles;

    public MistralTicketAnalysisService(
        @Qualifier("mistralRestClient") RestClient mistralRestClient,
        MistralProperties properties,
        ObjectMapper objectMapper) {
        this.mistralRestClient = mistralRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.lugaresDisponibles = new Semaphore(properties.getMaxConcurrent(), true);
    }

    @Override
    public TicketAnalysisResult analyze(MultipartFile ticketFoto) {
        if (ticketFoto == null || ticketFoto.isEmpty()) {
            throw new TicketAnalysisException(ErrorCode.FILE_EMPTY, "La foto del ticket esta vacia");
        }

        // El lugar se toma ANTES de armar el data URI, no solo antes del HTTP:
        // toDataUri codifica la imagen entera en base64 (~13MB por cada 10MB de
        // foto). Si el tope se aplicara despues, el pico de memoria seguiria
        // siendo proporcional a la cantidad de hilos, que es justo lo que este
        // mamparo existe para acotar.
        tomarLugar();
        try {
            String dataUri = toDataUri(ticketFoto);
            Map<String, Object> requestBody = buildRequestBody(dataUri);

            JsonNode response;
            try {
                response = mistralRestClient.post()
                    .uri("/v1/ocr")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
            } catch (RuntimeException e) {
                throw new TicketAnalysisException(
                    ErrorCode.ANALYSIS_FAILED, "Fallo la llamada al OCR de Mistral", e);
            }

            return parseAnnotation(response);
        } finally {
            // En finally y no al final del try: si el OCR falla o el parseo
            // explota, el lugar se libera igual. Un permiso que no se devuelve
            // reduce el cupo para siempre, y a los pocos errores el endpoint
            // queda muerto sin que nada lo explique.
            lugaresDisponibles.release();
        }
    }

    /**
     * Espera un lugar libre hasta el timeout configurado. Si no lo consigue,
     * corta con 503 en vez de encolarse indefinidamente: mas vale que el
     * empleado cargue el ticket a mano que dejarlo esperando con un hilo del
     * servidor retenido.
     */
    private void tomarLugar() {
        boolean obtenido;
        try {
            obtenido = lugaresDisponibles.tryAcquire(
                properties.getAcquireTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Se restaura la marca de interrupcion: tragarsela deja al hilo sin
            // saber que le pidieron terminar, y el contenedor no puede cerrarlo.
            Thread.currentThread().interrupt();
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_BUSY,
                "El analisis de tickets se interrumpio. Intenta de nuevo", e);
        }

        if (!obtenido) {
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_BUSY,
                "Hay demasiados tickets en analisis en este momento. "
                    + "Intenta en unos segundos o carga los datos a mano");
        }
    }

    // Mistral recibe la imagen como data URI base64 dentro del documento.
    private String toDataUri(MultipartFile ticketFoto) {
        String contentType = ticketFoto.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            contentType = MediaType.IMAGE_JPEG_VALUE;
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(ticketFoto.getBytes());
            return "data:" + contentType + ";base64," + base64;
        } catch (IOException e) {
            throw new TicketAnalysisException(
                ErrorCode.ANALYSIS_FAILED, "No se pudo leer la foto del ticket", e);
        }
    }

    private Map<String, Object> buildRequestBody(String dataUri) {
        return Map.of(
            "model", properties.getModel(),
            "document", Map.of(
                "type", "image_url",
                "image_url", dataUri
            ),
            "document_annotation_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                    // Sin "strict": con este OCR, strict:true devuelve la
                    // anotación como JSON malformado. Ver ANNOTATION_SCHEMA.
                    "name", "ticket_carga",
                    "schema", ANNOTATION_SCHEMA
                )
            )
        );
    }

    // "document_annotation" puede llegar como string JSON o como objeto ya
    // parseado segun la version de la API; se contemplan ambos casos.
    private TicketAnalysisResult parseAnnotation(JsonNode response) {
        if (response == null || response.path("document_annotation").isMissingNode()
            || response.get("document_annotation").isNull()) {
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_FAILED,
                "El OCR de Mistral no devolvio datos estructurados del ticket");
        }

        JsonNode annotationNode = response.get("document_annotation");
        JsonNode data;
        try {
            data = annotationNode.isTextual()
                ? objectMapper.readTree(annotationNode.asText())
                : annotationNode;
        } catch (JacksonException e) {
            throw new TicketAnalysisException(ErrorCode.ANALYSIS_FAILED,
                "No se pudo interpretar la respuesta del OCR de Mistral", e);
        }

        return new TicketAnalysisResult(
            asDecimal(data, "litros"),
            asDateTime(data, "fechaCarga"),
            asDouble(data, "importeTotal"),
            asDouble(data, "precioPorLitro"),
            asText(data, "estacion"),
            asText(data, "cuit"),
            asTipoCombustible(data, "tipoCombustible")
        );
    }

    // Mapea el valor leido al enum. Si el modelo devolvio algo fuera del enum o
    // null, se deja null: el empleado elige el vehiculo (que define el combustible).
    private TipoCombustible asTipoCombustible(JsonNode node, String field) {
        String raw = asText(node, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TipoCombustible.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Double asDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    // Los litros del OCR entran como BigDecimal desde el borde, sin pasar por
    // double: `decimalValue()` lee el numero del JSON tal cual viene, mientras
    // que `asDouble()` lo haria pasar por punto flotante binario y le meteria
    // error de representacion antes de que nadie lo use (ver
    // docs/BACKEND-AUDIT.md, DB-04).
    private BigDecimal asDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private String asText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    // El modelo puede devolver fecha+hora, fecha+hora con offset/Z, o solo
    // fecha; se intentan en orden y, si ninguno parsea, se deja null para que
    // el empleado complete a mano.
    private LocalDateTime asDateTime(JsonNode node, String field) {
        String raw = asText(node, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            // ignore
        }
        try {
            // Tolera un offset/Z al final (ej. "...T09:31:00Z"): se descarta la
            // zona y se toma la hora local del ticket.
            return OffsetDateTime.parse(raw).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // ignore
        }
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
