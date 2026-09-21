package com.company.inventory.health;






import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口(契约:{ok:true, ts:UTC ISO 毫秒字符串})。
 *
 * @author inventory
 */
@Tag(name = "健康检查")
@RestController
public class HealthController {

    /** UTC ISO 毫秒格式(与日期契约一致)。 */
    private static final DateTimeFormatter ISO_MS_Z =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    /**
     * 健康检查。
     *
     * @return {ok, ts}
     */
    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", Boolean.TRUE);
        body.put("ts", LocalDateTime.now(ZoneOffset.UTC).format(ISO_MS_Z));
        return body;
    }
}
