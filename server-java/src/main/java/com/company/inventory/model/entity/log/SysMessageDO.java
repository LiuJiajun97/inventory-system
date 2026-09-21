package com.company.inventory.model.entity.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 站内消息表实体(V25,表 sys_message):审批/转换/预警通知。
 *
 * <p>消息发送失败绝不影响主流程(由调用方 try-catch 兜底,只打 warn 日志)。</p>
 *
 * <p>列名 read 在数据库为 is_read(项目 boolean 口径,字段名禁 is 前缀故 Java 字段叫 read)。</p>
 *
 * @author inventory
 */
@TableName("sys_message")
@Getter
@Setter
public class SysMessageDO {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 接收人用户 ID。 */
    private Long receiverId;
    /** 消息类型:approval/conversion/alert_daily。 */
    private String type;
    /** 消息标题。 */
    private String title;
    /** 消息内容。 */
    private String content;
    /** 关联单据类型(可空,如 quotation/requisition)。 */
    private String refDocType;
    /** 关联单据 ID(可空)。 */
    private Long refDocId;
    /** 是否已读(列名 is_read;Java 字段禁 is 前缀,故显式映射)。 */
    @com.baomidou.mybatisplus.annotation.TableField("is_read")
    private Boolean read;
    /** 创建时间。 */
    private LocalDateTime createdAt;

}