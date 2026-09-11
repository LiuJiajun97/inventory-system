package com.company.inventory.common.constant;






/**
 * 用户角色枚举(阿里规范:业务类型进常量类,禁止魔法值)。
 *
 * @author inventory
 */
public enum RoleEnum {

    /** 管理员。 */
    ADMIN("admin"),

    /** 库员。 */
    OPERATOR("operator"),

    /** 查看员。 */
    VIEWER("viewer");

    /** 角色存储值(与数据库/Fastify 版一致)。 */
    private final String value;

    RoleEnum(String value) {
        this.value = value;
    }

    /**
     * 获取角色存储值。
     *
     * @return 角色值字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 按存储值解析角色,非法值返回 null。
     *
     * @param value 角色值字符串
     * @return 对应枚举或 null
     */
    public static RoleEnum fromValue(String value) {
        for (RoleEnum role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        return null;
    }

    /**
     * 判断给定角色值是否合法。
     *
     * @param value 角色值字符串
     * @return true 表示合法
     */
    public static boolean isValid(String value) {
        return fromValue(value) != null;
    }
}
