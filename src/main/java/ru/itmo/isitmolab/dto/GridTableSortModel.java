package ru.itmo.isitmolab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GridTableSortModel {
    public String colId; // идентификатор колонки (совпадает с полем JPA)
    public String sort; // "asc" | "desc"
}
