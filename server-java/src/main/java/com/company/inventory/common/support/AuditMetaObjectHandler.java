package com.company.inventory.common.support;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充:insert 时填 creator/createdAt/updater/updatedAt,update 时填 updater/updatedAt。
 *
 * @author inventory
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String username = UserContext.get();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "creator", String.class, username);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updater", String.class, username);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 强制覆盖而非 strictUpdateFill:strict 只填 null 字段,而 update 前 selectById
        // 已把 updatedAt 带成非 null,strict 会导致更新时 updatedAt/updater 永不刷新。
        // hasSetter 保护:部分 DO 无该字段时不填充也不报错。
        String username = UserContext.get();
        LocalDateTime now = LocalDateTime.now();
        if (metaObject.hasSetter("updater")) {
            this.setFieldValByName("updater", username, metaObject);
        }
        if (metaObject.hasSetter("updatedAt")) {
            this.setFieldValByName("updatedAt", now, metaObject);
        }
    }
}
