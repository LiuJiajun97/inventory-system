package com.company.inventory.common.constant;

/**
 * 菜单类型常量(阿里规范:业务类型进常量类,禁止魔法值)。
 *
 * <p>sys_menu.type 三型合一:目录(directory)/ 菜单(menu)/ 按钮(button,即权限码)。</p>
 *
 * @author inventory
 */
public enum MenuType {

    /** 目录(一级分组,无 path)。 */
    DIRECTORY("directory"),

    /** 菜单(可导航页面,有 path)。 */
    MENU("menu"),

    /** 按钮(挂在叶子菜单下的权限码,menu_code 即 permission code)。 */
    BUTTON("button");

    /** 类型存储值(与数据库一致)。 */
    private final String value;

    MenuType(String value) {
        this.value = value;
    }

    /**
     * 获取类型存储值。
     *
     * @return 类型值字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 按存储值解析类型,非法值返回 null。
     *
     * @param value 类型值字符串
     * @return 对应枚举或 null
     */
    public static MenuType fromValue(String value) {
        for (MenuType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}
