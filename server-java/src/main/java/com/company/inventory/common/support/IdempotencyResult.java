package com.company.inventory.common.support;

/**
 * 幂等缓存条目:首次成功响应的完整快照(HTTP 状态码、内容类型、响应体字节)。
 *
 * <p>同 key 的后续请求命中缓存时,按此快照原样重放,保证 at-least-once 重试幂等。</p>
 *
 * @param status      首次请求的 HTTP 状态码
 * @param contentType 首次响应的 Content-Type(可为 null,表示未设置)
 * @param body        首次响应的完整字节体
 * @author inventory
 */
public record IdempotencyResult(int status, String contentType, byte[] body) {
}
