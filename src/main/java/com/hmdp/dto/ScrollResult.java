package com.hmdp.dto;

import lombok.Data;
import java.util.List;

@Data
public class ScrollResult {
    // 查询的Blog结果
    private List<?> list;
    // 上次查询的最小时间戳
    private Long minTime;
    // 偏移量
    private Integer offset;
}
